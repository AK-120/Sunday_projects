package com.meetingscribe.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Records 16 kHz mono 16-bit PCM and hands it over in speech-sized chunks.
 *
 * Chunks are cut at a pause (about 0.4 s of quiet) once they are at least
 * [MIN_CHUNK_SECONDS] long, and forcibly at [MAX_CHUNK_SECONDS]. Cutting at
 * pauses keeps words whole, which matters a lot for accuracy. Chunks with no
 * real speech are dropped, because speech models invent text from silence.
 *
 * [onLevel] reports the live loudness (0..1) for the waveform.
 * [record] blocks until [stop] is called, so run it on a background thread.
 */
class PcmRecorder(
    private val onChunk: (ByteArray) -> Unit,
    private val onLevel: (Float) -> Unit,
) {

    @Volatile
    private var running = true

    fun stop() {
        running = false
    }

    @SuppressLint("MissingPermission") // permission is requested by the UI before starting
    fun record() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuf, SAMPLE_RATE * 2)
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone is unavailable." }

        val buf = ByteArray(READ_BYTES)
        val acc = ByteArrayOutputStream()
        var speechReads = 0
        var quietRun = 0
        var peak = 1000.0 // slowly decaying loudness reference, adapts to the room

        fun flush() {
            if (speechReads >= MIN_SPEECH_READS) onChunk(acc.toByteArray())
            acc.reset()
            speechReads = 0
            quietRun = 0
        }

        recorder.startRecording()
        try {
            while (running) {
                val n = recorder.read(buf, 0, buf.size)
                if (n < 0) break
                if (n == 0) continue
                val rms = rms(buf, n)
                peak = max(peak * 0.9995, rms)
                onLevel(((rms / 5000.0).coerceIn(0.0, 1.0)).pow(0.6).toFloat())

                val isSpeech = rms > max(MIN_SPEECH_RMS, peak * 0.12)
                if (isSpeech) {
                    speechReads++
                    quietRun = 0
                } else {
                    quietRun++
                }
                acc.write(buf, 0, n)

                val seconds = acc.size() / (SAMPLE_RATE * 2)
                val pausedLongEnough = quietRun >= QUIET_READS_FOR_CUT
                if ((seconds >= MIN_CHUNK_SECONDS && pausedLongEnough) || seconds >= MAX_CHUNK_SECONDS) {
                    flush()
                } else if (speechReads == 0 && seconds >= MIN_CHUNK_SECONDS) {
                    // Nothing but silence so far: don't let it pile up.
                    acc.reset()
                    quietRun = 0
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            flush()
        }
    }

    private fun rms(b: ByteArray, n: Int): Double {
        var sum = 0.0
        var i = 0
        var count = 0
        while (i + 1 < n) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
            i += 2
            count++
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val READ_BYTES = 1280 // 40 ms
        private const val MIN_CHUNK_SECONDS = 7 // shorter chunks show the English text sooner
        private const val MAX_CHUNK_SECONDS = 28 // Sarvam's REST endpoint allows about 30 s
        private const val QUIET_READS_FOR_CUT = 10 // 0.4 s
        private const val MIN_SPEECH_READS = 8 // about 0.3 s of actual speech
        private const val MIN_SPEECH_RMS = 220.0
    }
}
