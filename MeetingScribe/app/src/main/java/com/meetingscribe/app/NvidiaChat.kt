package com.meetingscribe.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Text models on NVIDIA's OpenAI-compatible hosted endpoint
 * (integrate.api.nvidia.com): translation to English and the meeting summary.
 * The default is a 70B Llama model; change it in Settings.
 */
class NvidiaChat(private val apiKey: String, private val model: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun translateToEnglish(text: String, languageName: String): String =
        complete(
            system = "You are a professional translator. Translate the user's text from " +
                "$languageName to English. The speaker may mix English words into " +
                "$languageName: keep those as they are. The text comes from automatic speech " +
                "recognition and can contain spelling mistakes; use context to fix them. " +
                "Output only the English translation. Do not add notes or explanations. " +
                "If the text is meaningless, output [unclear].",
            user = text,
            maxTokens = 1024,
            temperature = 0.1,
        )

    suspend fun summarize(englishTranscript: String): String =
        complete(
            system = "You write concise meeting notes in English. Use plain text only (no markdown " +
                "symbols except a leading hyphen for list items). Output exactly these sections:\n" +
                "Summary: 3-5 sentences.\n" +
                "Key decisions: bullet list, or 'None'.\n" +
                "Action items: bullet list with owner and deadline if mentioned, or 'None'.\n" +
                "Only use facts present in the transcript. Do not invent names, numbers or dates. " +
                "Segments marked [unclear] were not understood; ignore them.",
            user = "Meeting transcript:\n\n$englishTranscript",
            maxTokens = 1024,
            temperature = 0.2,
        )

    private suspend fun complete(
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))

        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("stream", false)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("NVIDIA text model failed (HTTP ${resp.code}): ${text.take(200)}")
            }
            JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }
}
