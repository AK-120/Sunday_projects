package com.meetingscribe.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(onOpenMeeting: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember { SettingsStore.flow(ctx) }.collectAsState(initial = AppSettings())
    val engine by MeetingEngine.state.collectAsStateWithLifecycle()
    val startRecording = rememberStartRecording()

    var meetings by remember { mutableStateOf<List<Meeting>>(emptyList()) }
    LaunchedEffect(engine.phase, engine.savedId) {
        meetings = withContext(Dispatchers.IO) { HistoryStore.list(ctx) }
    }

    var languageMenu by remember { mutableStateOf(false) }
    val language = languageOf(settings.language)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Meeting Scribe", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Text(
                "Language spoken in the meeting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { languageMenu = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        language.native,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (language.code != "en") {
                        Text(
                            "  to English",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Change language",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    LANGUAGES.forEach { l ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(l.native, style = MaterialTheme.typography.bodyLarge)
                                    if (l.native != l.label) {
                                        Text(
                                            "  ${l.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                languageMenu = false
                                scope.launch { SettingsStore.save(ctx, settings.copy(language = l.code)) }
                            },
                        )
                    }
                }
            }
            val speechEngine = speechEngineFor(settings, language.code)
            Text(
                "Speech by ${speechEngine.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (speechEngine == SpeechEngine.NVIDIA && language.code in NVIDIA_LIMITED) {
                Text(
                    "NVIDIA's speech models don't officially list ${language.label}, so expect " +
                        "mistakes. For much better results add a Sarvam key in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (speechEngine == SpeechEngine.NVIDIA && settings.sarvamKey.isBlank() &&
                language.code in SARVAM_TAGS
            ) {
                Text(
                    "Indian accents come out better with Sarvam. Add a Sarvam key in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            Button(
                onClick = startRecording,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(10.dp))
                Text("Start recording", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (settings.apiKey.isBlank()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Add your free NVIDIA API key before recording.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenSettings) { Text("Open settings") }
            }
        }

        if (engine.phase == Phase.DONE && engine.savedId == null) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "No speech was picked up. Move the phone closer to the speaker and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { MeetingEngine.reset() }) { Text("Dismiss") }
            }
        } else if (engine.error != null && engine.phase == Phase.IDLE) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(engine.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { MeetingEngine.reset() }) { Text("Dismiss") }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            Text("Recent meetings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }

        if (meetings.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. Record a meeting and its notes will appear in this list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            items(meetings, key = { it.id }) { m ->
                MeetingRow(m, onClick = { onOpenMeeting(m.id) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MeetingRow(m: Meeting, onClick: () -> Unit) {
    val preview = m.summary.lines()
        .firstOrNull { it.isNotBlank() && !it.trim().endsWith(":") }
        ?.removePrefix("Summary:")?.trim()
        ?: m.englishTranscript
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(formatWhen(m.id), style = MaterialTheme.typography.titleSmall)
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "${formatDuration(m.durationSeconds)}, ${languageOf(m.language).label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
