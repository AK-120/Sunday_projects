package com.meetingscribe.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by remember { SettingsStore.flow(ctx) }.collectAsState(initial = null)

    var apiKey by remember { mutableStateOf("") }
    var sarvamKey by remember { mutableStateOf("") }
    var useNvidia by remember { mutableStateOf(false) }
    var recipient by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(AppSettings.DEFAULT_CHAT_MODEL) }
    var filled by remember { mutableStateOf(false) }

    // Fill the form once from storage.
    LaunchedEffect(saved) {
        val s = saved
        if (s != null && !filled) {
            apiKey = s.apiKey
            sarvamKey = s.sarvamKey
            useNvidia = s.useNvidiaSpeech
            recipient = s.recipient
            model = s.chatModel
            filled = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = sarvamKey,
                onValueChange = { sarvamKey = it },
                label = { Text("Sarvam API key (speech)") },
                supportingText = {
                    Text("From dashboard.sarvam.ai. Best for Indian English and Malayalam.")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("NVIDIA API key (translation and notes)") },
                supportingText = { Text("Free at build.nvidia.com. It starts with nvapi-") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use NVIDIA for speech instead", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Only if you don't want to use Sarvam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = useNvidia, onCheckedChange = { useNvidia = it })
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it },
                label = { Text("Send notes to") },
                supportingText = {
                    Text(
                        if (recipient.isNotBlank() && !isValidEmail(recipient)) "That doesn't look like an email address."
                        else "Opens your email app with this address filled in."
                    )
                },
                isError = recipient.isNotBlank() && !isValidEmail(recipient),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Text model for translation and notes") },
                supportingText = { Text("Any chat model from build.nvidia.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "How it works. Sarvam writes down what was said in the meeting language. " +
                    "If the meeting is not in English, the NVIDIA text model translates it to " +
                    "English, then writes the notes. Without a Sarvam key, NVIDIA Parakeet " +
                    "(English) and Whisper (other languages) are used for speech.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = {
                if (recipient.isNotBlank() && !isValidEmail(recipient)) {
                    toast(ctx, "Fix the email address first.")
                } else {
                    scope.launch {
                        val current = saved ?: AppSettings()
                        SettingsStore.save(
                            ctx,
                            current.copy(
                                apiKey = apiKey,
                                sarvamKey = sarvamKey,
                                useNvidiaSpeech = useNvidia,
                                recipient = recipient,
                                chatModel = model,
                            ),
                        )
                        toast(ctx, "Saved")
                        onBack()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Save settings", style = MaterialTheme.typography.labelLarge) }
    }
}
