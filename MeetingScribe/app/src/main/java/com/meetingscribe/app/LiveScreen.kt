package com.meetingscribe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Shown while recording and while the notes are being written. */
@Composable
fun LiveScreen() {
    val state by MeetingEngine.state.collectAsStateWithLifecycle()
    val levels by MeetingEngine.levels.collectAsStateWithLifecycle()
    val recording = state.phase == Phase.RECORDING
    val language = languageOf(state.language)
    val listState = rememberLazyListState()

    LaunchedEffect(state.segments.size) {
        if (state.segments.isNotEmpty()) listState.animateScrollToItem(state.segments.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LiveGround)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (recording) Gold else LiveMuted)
            )
            Text(
                "  " + if (recording) "Recording with ${state.speechEngine}" else state.status,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = LiveMuted,
            )
        }

        Text(
            formatClock(state.elapsedSeconds),
            style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
            color = LiveInk,
            modifier = Modifier.padding(top = 8.dp),
        )

        Waveform(
            levels = levels,
            color = Gold,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(vertical = 8.dp),
        )

        if (!recording) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                color = Gold,
                trackColor = LiveMuted.copy(alpha = 0.3f),
            )
        }

        if (state.segments.isEmpty()) {
            Text(
                if (recording) "Words will appear here after the first pause in speech."
                else "Nothing was transcribed.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = LiveMuted,
                modifier = Modifier.padding(top = 16.dp).weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(state.segments) { _, seg ->
                    Column {
                        Text(
                            seg.english.ifBlank { "Translating…" },
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = LiveInk,
                        )
                        if (language.code != "en") {
                            Text(
                                seg.original,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = LiveMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        state.error?.let { msg ->
            Text(
                msg,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Gold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = { MeetingEngine.stop() },
            enabled = recording,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Gold,
                contentColor = OnGold,
                disabledContainerColor = LiveMuted.copy(alpha = 0.25f),
                disabledContentColor = LiveMuted,
            ),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(
                if (recording) "Stop and write notes" else "Writing notes",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
        }
    }
}
