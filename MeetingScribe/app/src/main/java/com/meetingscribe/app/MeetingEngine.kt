package com.meetingscribe.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { IDLE, RECORDING, PROCESSING, DONE }

data class EngineState(
    val phase: Phase = Phase.IDLE,
    val elapsedSeconds: Int = 0,
    val segments: List<Segment> = emptyList(),
    val status: String = "",
    val error: String? = null,
    val language: String = "en",
    val speechEngine: String = "",
    /** Set when the finished meeting has been saved to history. */
    val savedId: Long? = null,
)

/**
 * Process-wide pipeline:
 * microphone -> speech chunks -> NVIDIA speech model -> (NVIDIA LLM translation)
 * -> live segments -> NVIDIA LLM summary -> saved meeting.
 *
 * It lives outside the Activity so recording keeps going with the screen off;
 * [RecordingService] keeps the process alive with a foreground notification.
 */
object MeetingEngine {

    private const val LEVEL_HISTORY = 64

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels

    private var recorder: PcmRecorder? = null

    fun start(ctx: Context) {
        val phase = _state.value.phase
        if (phase == Phase.RECORDING || phase == Phase.PROCESSING) return
        val app = ctx.applicationContext

        // Clear any previous meeting synchronously so observers never see stale results.
        _state.value = EngineState(status = "Starting")
        _levels.value = emptyList()

        scope.launch {
            val settings = SettingsStore.read(app)
            if (settings.apiKey.isBlank()) {
                // The NVIDIA key is always needed: it powers translation and the notes.
                _state.value = EngineState(error = "Add your NVIDIA API key in Settings first.")
                return@launch
            }

            val meetingId = System.currentTimeMillis()
            val language = languageOf(settings.language)
            val english = language.code == "en"
            val engine = speechEngineFor(settings, language.code)
            _state.value = EngineState(
                phase = Phase.RECORDING,
                status = "Listening",
                language = language.code,
                speechEngine = engine.label,
            )

            val asr: SpeechToText = when (engine) {
                SpeechEngine.SARVAM -> SarvamAsr(settings.sarvamKey)
                SpeechEngine.NVIDIA -> NvidiaAsr(settings.apiKey)
            }
            val chat = NvidiaChat(settings.apiKey, settings.chatModel)
            val chunks = Channel<ByteArray>(Channel.UNLIMITED)
            val pcm = PcmRecorder(
                onChunk = { chunks.trySend(it) },
                onLevel = { v -> _levels.update { (it + v).takeLast(LEVEL_HISTORY) } },
            )
            recorder = pcm

            // Handles chunks one at a time so the transcript stays in order.
            val consumer = launch {
                for (chunk in chunks) {
                    try {
                        val heard = TextCleaner.clean(asr.transcribe(chunk, language.code))
                        if (heard.isBlank()) continue

                        val segment = if (english) {
                            Segment(original = heard, english = heard)
                        } else {
                            val translated = try {
                                chat.translateToEnglish(heard, language.label)
                            } catch (e: Exception) {
                                _state.update { it.copy(error = "Translation problem: ${e.message}") }
                                ""
                            }
                            Segment(original = heard, english = translated)
                        }
                        _state.update { it.copy(segments = it.segments + segment) }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Speech problem: ${e.message}") }
                    }
                }
            }

            val timer = launch {
                while (isActive) {
                    delay(1000)
                    _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }

            try {
                withContext(Dispatchers.IO) { pcm.record() }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Recording failed.") }
            } finally {
                recorder = null
                timer.cancel()
                chunks.close()
            }

            _state.update { it.copy(phase = Phase.PROCESSING, status = "Finishing the last words") }
            consumer.join()
            asr.close()

            val segments = _state.value.segments
            val duration = _state.value.elapsedSeconds
            if (segments.isEmpty()) {
                _state.update { it.copy(phase = Phase.DONE, status = "No speech was picked up.") }
                return@launch
            }

            _state.update { it.copy(status = "Writing the notes") }
            val draft = Meeting(meetingId, language.code, duration, "", segments)
            val summary = try {
                chat.summarize(draft.englishTranscript)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Summary problem: ${e.message}") }
                ""
            }

            val meeting = draft.copy(summary = summary)
            withContext(Dispatchers.IO) { HistoryStore.save(app, meeting) }
            _state.update { it.copy(phase = Phase.DONE, status = "Done", savedId = meetingId) }
        }
    }

    fun stop() {
        recorder?.stop()
    }

    /** Clears a finished meeting so the home screen is clean again. */
    fun reset() {
        val p = _state.value.phase
        if (p == Phase.IDLE || p == Phase.DONE) {
            _state.value = EngineState()
            _levels.value = emptyList()
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
