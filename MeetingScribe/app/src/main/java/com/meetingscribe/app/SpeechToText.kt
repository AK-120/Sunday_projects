package com.meetingscribe.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Turns a chunk of 16 kHz mono 16-bit PCM audio into text in the spoken language. */
interface SpeechToText {
    suspend fun transcribe(pcm: ByteArray, languageCode: String): String
    fun close()
}

/**
 * Sarvam AI speech-to-text (https://docs.sarvam.ai), built for Indian languages and
 * Indian-accented English. Uses the REST endpoint, which takes up to about 30 seconds
 * of audio per request, so the recorder never makes chunks longer than 28 seconds.
 */
class SarvamAsr(private val apiKey: String) : SpeechToText {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(pcm: ByteArray, languageCode: String): String =
        withContext(Dispatchers.IO) {
            val tag = SARVAM_TAGS[languageCode] ?: error("Sarvam does not support this language.")
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "chunk.wav", wavOf(pcm).toRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("language_code", tag)
                .build()
            val request = Request.Builder()
                .url("https://api.sarvam.ai/speech-to-text")
                .header("api-subscription-key", apiKey)
                .post(body)
                .build()

            var lastCode = 0
            for (attempt in 1..3) {
                val (code, text) = http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
                lastCode = code
                if (code in 200..299) {
                    return@withContext JSONObject(text).optString("transcript").trim()
                }
                if (code == 403 || code == 401) {
                    error("Sarvam rejected the API key. Check it in Settings.")
                }
                if ((code == 429 || code == 503) && attempt < 3) {
                    delay(2500L * attempt)
                } else {
                    error("Sarvam speech failed (HTTP $code): ${text.take(160)}")
                }
            }
            error("Sarvam speech failed (HTTP $lastCode).")
        }

    override fun close() = Unit

    /** Wraps raw 16 kHz mono 16-bit PCM in a 44-byte WAV header. */
    private fun wavOf(pcm: ByteArray): ByteArray {
        val rate = PcmRecorder.SAMPLE_RATE
        val buf = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return buf.array()
    }
}
