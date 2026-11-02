package com.meetingscribe.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SECTION_HEADINGS = listOf("summary", "key decisions", "action items")

@Composable
fun ResultScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by remember { SettingsStore.flow(ctx) }.collectAsState(initial = AppSettings())
    var meeting by remember { mutableStateOf<Meeting?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        meeting = withContext(Dispatchers.IO) { HistoryStore.get(ctx, id) }
        loaded = true
    }

    val m = meeting
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(formatWhen(id), style = MaterialTheme.typography.titleMedium)
                if (m != null) {
                    Text(
                        "${formatDuration(m.durationSeconds)}, ${languageOf(m.language).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { confirmDelete = true }, enabled = m != null) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete meeting")
            }
        }

        if (m == null) {
            if (loaded) {
                Text(
                    "This meeting could not be found.",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            if (m.summary.isBlank()) {
                Text(
                    "The summary could not be written. The full transcript is below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
                SummaryText(m.summary)
            }

            HorizontalDivider(
                Modifier.padding(vertical = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transcript", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (m.language != "en") {
                    Text(
                        "Show ${languageOf(m.language).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    Switch(checked = showOriginal, onCheckedChange = { showOriginal = it })
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                m.segments.forEach { seg ->
                    Text(
                        if (showOriginal) seg.original else seg.english.ifBlank { seg.original },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    when {
                        !isValidEmail(settings.recipient) ->
                            toast(ctx, "Add a recipient email in Settings first.")
                        !composeMeetingEmail(ctx, settings.recipient, m.summary, m.englishTranscript) ->
                            toast(ctx, "No email app found on this phone.")
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Email, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (isValidEmail(settings.recipient)) "Email notes" else "Set recipient",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick = {
                    val text = listOf(m.summary, m.englishTranscript).filter { it.isNotBlank() }.joinToString("\n\n")
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Meeting notes", text))
                    toast(ctx, "Copied")
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Copy", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this meeting?") },
            text = { Text("The notes and transcript will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    HistoryStore.delete(ctx, id)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** Renders the model's plain-text notes, giving the three section names a heading style. */
@Composable
private fun SummaryText(summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        summary.lines().forEach { raw ->
            val line = raw.trimEnd()
            if (line.isBlank()) return@forEach
            val lower = line.lowercase()
            val heading = SECTION_HEADINGS.firstOrNull { lower.startsWith(it) }
            if (heading != null) {
                val rest = line.substring(heading.length).trimStart(':', ' ')
                Text(
                    line.substring(0, heading.length),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (rest.isNotBlank()) Text(rest, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
