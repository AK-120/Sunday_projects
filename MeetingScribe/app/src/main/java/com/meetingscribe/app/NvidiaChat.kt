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
 * (integrate.api.nvidia.com): translation to English, fixing misheard words, and the
 * meeting summary. The default is a 70B Llama model; change it in Settings.
 *
 * [vocabulary] is the person's own list of names and terms. Speech models often write
 * a rare name or a brand as a common word ("Athul" -> "Atul", "Claude" -> "cloud"), so
 * every text step is told to prefer these spellings when the sound and the context fit.
 */
class NvidiaChat(private val apiKey: String, private val model: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun vocabLine(vocabulary: List<String>): String =
        if (vocabulary.isEmpty()) ""
        else " Names and terms that may appear, with their correct spelling: " +
            vocabulary.joinToString(", ") + ". If a word in the text sounds like one of them " +
            "and the context fits, use the correct spelling. Do not insert them anywhere else."

    suspend fun translateToEnglish(text: String, languageName: String, vocabulary: List<String>): String =
        complete(
            system = "You are a professional translator. Translate the user's text from " +
                "$languageName to English. The speaker mixes English and $languageName in the " +
                "same sentence, which is normal in India. Words already in English stay exactly " +
                "as they are, including names, numbers, and technical or business terms; " +
                "translate only the $languageName parts, so the result reads as one natural " +
                "English sentence. The text comes from automatic speech " +
                "recognition and can contain spelling mistakes; use context to fix them." +
                vocabLine(vocabulary) +
                " Output only the English translation. Do not add notes or explanations. " +
                "If the text is meaningless, output [unclear].",
            user = text,
            maxTokens = 1024,
            temperature = 0.1,
        )

    /** For English speech: fixes only misheard names and terms, never rewrites. */
    suspend fun correctEnglish(text: String, vocabulary: List<String>): String =
        complete(
            system = "You fix speech-recognition mistakes in a transcript. Change only words that " +
                "were clearly misheard." + vocabLine(vocabulary) +
                " Keep every other word, the order, and the meaning exactly as they are. " +
                "Do not add, remove or reword anything else. Output only the corrected text.",
            user = text,
            maxTokens = 1024,
            temperature = 0.0,
        )

    suspend fun summarize(englishTranscript: String, vocabulary: List<String>): String =
        complete(
            system = "You write concise meeting notes in English. Use plain text only (no markdown " +
                "symbols except a leading hyphen for list items). Output exactly these sections:\n" +
                "Summary: 3-5 sentences.\n" +
                "Key decisions: bullet list, or 'None'.\n" +
                "Action items: bullet list with owner and deadline if mentioned, or 'None'.\n" +
                "Only use facts present in the transcript. Do not invent names, numbers or dates. " +
                "Segments marked [unclear] were not understood; ignore them." +
                vocabLine(vocabulary),
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
