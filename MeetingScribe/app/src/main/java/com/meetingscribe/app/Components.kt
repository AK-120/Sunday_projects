package com.meetingscribe.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * The signature element: a strip of live microphone loudness. Newest sound
 * enters on the right and fades out to the left, like a chart-recorder tape.
 */
@Composable
fun Waveform(levels: List<Float>, color: Color, modifier: Modifier = Modifier, bars: Int = 48) {
    Canvas(modifier) {
        val gap = 5.dp.toPx()
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val mid = size.height / 2f
        for (i in 0 until bars) {
            val idx = levels.size - bars + i
            val v = if (idx >= 0) levels[idx] else 0f
            val h = max(barWidth, v * size.height)
            val fade = 0.18f + 0.82f * (i / (bars - 1f))
            drawRoundRect(
                color = color.copy(alpha = fade),
                topLeft = Offset(i * (barWidth + gap), mid - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Returns a function that asks for the microphone permission, then starts recording. */
@Composable
fun rememberStartRecording(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            ctx.startForegroundService(Intent(ctx, RecordingService::class.java))
        } else {
            Toast.makeText(
                ctx,
                "Allow the microphone to record. You can change this in the phone's app settings.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    return remember(launcher) {
        {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(perms.toTypedArray())
        }
    }
}

fun formatClock(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds sec"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}

fun formatWhen(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
