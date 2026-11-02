package com.meetingscribe.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One stretch of speech. [original] is in the spoken language, [english] is the English text. */
data class Segment(val original: String, val english: String)

data class Meeting(
    val id: Long,
    val language: String,
    val durationSeconds: Int,
    val summary: String,
    val segments: List<Segment>,
) {
    /** Full English transcript. Falls back to the original where translation failed. */
    val englishTranscript: String
        get() = segments.joinToString("\n\n") { it.english.ifBlank { it.original } }.trim()
}

/** Meetings are stored as small JSON files in the app's private storage. */
object HistoryStore {

    private fun dir(ctx: Context) = File(ctx.filesDir, "meetings").apply { mkdirs() }

    fun save(ctx: Context, m: Meeting) {
        val json = JSONObject()
            .put("id", m.id)
            .put("language", m.language)
            .put("duration", m.durationSeconds)
            .put("summary", m.summary)
            .put(
                "segments",
                JSONArray().also { arr ->
                    m.segments.forEach {
                        arr.put(JSONObject().put("o", it.original).put("e", it.english))
                    }
                }
            )
        File(dir(ctx), "${m.id}.json").writeText(json.toString())
    }

    fun list(ctx: Context): List<Meeting> =
        dir(ctx).listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.id }
            .orEmpty()

    fun get(ctx: Context, id: Long): Meeting? =
        File(dir(ctx), "$id.json").takeIf { it.exists() }
            ?.let { runCatching { parse(it.readText()) }.getOrNull() }

    fun delete(ctx: Context, id: Long) {
        File(dir(ctx), "$id.json").delete()
    }

    private fun parse(text: String): Meeting {
        val j = JSONObject(text)
        val arr = j.getJSONArray("segments")
        val segs = (0 until arr.length()).map {
            val s = arr.getJSONObject(it)
            Segment(s.optString("o"), s.optString("e"))
        }
        return Meeting(
            id = j.getLong("id"),
            language = j.optString("language", "en"),
            durationSeconds = j.optInt("duration"),
            summary = j.optString("summary"),
            segments = segs,
        )
    }
}

/** Cleans up typical speech-model mistakes before text is shown. */
object TextCleaner {
    private val FILLER_HALLUCINATIONS = setOf(
        "thank you.", "thank you", "thanks for watching!", "thanks for watching.",
        "you", "bye.", "bye", ".",
    )

    fun clean(raw: String): String {
        var t = raw.trim()
        if (t.lowercase() in FILLER_HALLUCINATIONS) return ""
        // Collapse a word or short phrase repeated many times in a row (a Whisper failure mode).
        t = t.replace(Regex("(\\b[\\p{L}\\p{M}]+(?:\\s+[\\p{L}\\p{M}]+){0,3}\\b)(?:[\\s,.]+\\1){4,}"), "$1")
        return t.trim()
    }
}
