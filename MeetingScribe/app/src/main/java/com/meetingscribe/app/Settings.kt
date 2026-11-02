package com.meetingscribe.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** A spoken language. [code] is a short code; each speech engine maps it to its own format. */
data class Language(val code: String, val label: String, val native: String)

val LANGUAGES = listOf(
    Language("en", "English", "English"),
    Language("ml", "Malayalam", "മലയാളം"),
    Language("hi", "Hindi", "हिन्दी"),
    Language("ta", "Tamil", "தமிழ்"),
    Language("te", "Telugu", "తెలుగు"),
    Language("kn", "Kannada", "ಕನ್ನಡ"),
    Language("bn", "Bengali", "বাংলা"),
    Language("mr", "Marathi", "मराठी"),
    Language("gu", "Gujarati", "ગુજરાતી"),
    Language("pa", "Punjabi", "ਪੰਜਾਬੀ"),
    Language("od", "Odia", "ଓଡ଼ିଆ"),
    Language("ur", "Urdu", "اردو"),
    Language("ar", "Arabic", "العربية"),
    Language("zh", "Chinese", "中文"),
    Language("ja", "Japanese", "日本語"),
    Language("ko", "Korean", "한국어"),
    Language("fr", "French", "Français"),
    Language("de", "German", "Deutsch"),
    Language("es", "Spanish", "Español"),
    Language("pt", "Portuguese", "Português"),
    Language("it", "Italian", "Italiano"),
    Language("ru", "Russian", "Русский"),
)

fun languageOf(code: String): Language =
    LANGUAGES.firstOrNull { it.code == code } ?: LANGUAGES.first()

/** Languages the Sarvam speech API accepts, with its BCP-47 tags. English uses Indian English. */
val SARVAM_TAGS = mapOf(
    "en" to "en-IN", "ml" to "ml-IN", "hi" to "hi-IN", "ta" to "ta-IN", "te" to "te-IN",
    "kn" to "kn-IN", "bn" to "bn-IN", "mr" to "mr-IN", "gu" to "gu-IN", "pa" to "pa-IN",
    "od" to "od-IN", "ur" to "ur-IN",
)

/**
 * Languages missing from NVIDIA's published speech-model support matrix.
 * Only matters when the NVIDIA speech engine is used.
 */
val NVIDIA_LIMITED = setOf("ml", "te")

enum class SpeechEngine(val label: String) { SARVAM("Sarvam"), NVIDIA("NVIDIA") }

data class AppSettings(
    val apiKey: String = "",
    val sarvamKey: String = "",
    val useNvidiaSpeech: Boolean = false,
    val recipient: String = "",
    val language: String = "en",
    val chatModel: String = DEFAULT_CHAT_MODEL,
    /** Names and terms to spell correctly, separated by commas or new lines. */
    val vocabulary: String = DEFAULT_VOCABULARY,
) {
    /** The vocabulary as a clean list (Sarvam accepts at most 50 terms of 64 characters). */
    fun vocabularyList(): List<String> =
        vocabulary.split(',', '\n').map { it.trim().take(64) }.filter { it.isNotEmpty() }.distinct().take(50)

    companion object {
        const val DEFAULT_CHAT_MODEL = "meta/llama-3.3-70b-instruct"
        const val DEFAULT_VOCABULARY = "Athul, Claude"
    }
}

/**
 * Sarvam is used when a Sarvam key is saved, the language is one it supports, and the
 * person has not switched it off. Everything else falls back to NVIDIA speech.
 */
fun speechEngineFor(s: AppSettings, languageCode: String): SpeechEngine =
    if (s.sarvamKey.isNotBlank() && !s.useNvidiaSpeech && languageCode in SARVAM_TAGS) SpeechEngine.SARVAM
    else SpeechEngine.NVIDIA

object SettingsStore {
    private val KEY_API = stringPreferencesKey("api_key")
    private val KEY_SARVAM = stringPreferencesKey("sarvam_key")
    private val KEY_USE_NVIDIA = booleanPreferencesKey("use_nvidia_speech")
    private val KEY_TO = stringPreferencesKey("recipient")
    private val KEY_LANG = stringPreferencesKey("language")
    private val KEY_MODEL = stringPreferencesKey("chat_model")
    private val KEY_VOCAB = stringPreferencesKey("vocabulary")

    fun flow(ctx: Context): Flow<AppSettings> = ctx.applicationContext.dataStore.data.map { p ->
        AppSettings(
            apiKey = p[KEY_API].orEmpty(),
            sarvamKey = p[KEY_SARVAM].orEmpty(),
            useNvidiaSpeech = p[KEY_USE_NVIDIA] ?: false,
            recipient = p[KEY_TO].orEmpty(),
            language = p[KEY_LANG] ?: "en",
            chatModel = p[KEY_MODEL] ?: AppSettings.DEFAULT_CHAT_MODEL,
            vocabulary = p[KEY_VOCAB] ?: AppSettings.DEFAULT_VOCABULARY,
        )
    }

    suspend fun read(ctx: Context): AppSettings = flow(ctx).first()

    suspend fun save(ctx: Context, s: AppSettings) {
        ctx.applicationContext.dataStore.edit { p ->
            p[KEY_API] = s.apiKey.trim()
            p[KEY_SARVAM] = s.sarvamKey.trim()
            p[KEY_USE_NVIDIA] = s.useNvidiaSpeech
            p[KEY_TO] = s.recipient.trim()
            p[KEY_LANG] = s.language
            p[KEY_MODEL] = s.chatModel.trim().ifBlank { AppSettings.DEFAULT_CHAT_MODEL }
            p[KEY_VOCAB] = s.vocabulary.trim()
        }
    }
}
