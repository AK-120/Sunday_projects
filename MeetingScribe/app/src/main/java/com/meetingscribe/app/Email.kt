package com.meetingscribe.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import java.text.DateFormat
import java.util.Date

fun isValidEmail(s: String) = Patterns.EMAIL_ADDRESS.matcher(s.trim()).matches()

/**
 * Opens the phone's email app with the recipient, subject and body filled in.
 * The person taps Send there, so no email password is ever stored in this app.
 * Returns false if no email app is installed.
 */
fun composeMeetingEmail(ctx: Context, to: String, summary: String, transcript: String): Boolean {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
    val body = buildString {
        if (summary.isNotBlank()) {
            append(summary).append("\n\n")
        }
        append("--- Full transcript (English) ---\n")
        append(transcript)
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to.trim()))
        putExtra(Intent.EXTRA_SUBJECT, "Meeting notes – $date")
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
