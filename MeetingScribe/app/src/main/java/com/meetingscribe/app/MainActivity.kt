package com.meetingscribe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Result(val id: Long) : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val engine by MeetingEngine.state.collectAsStateWithLifecycle()
    val live = engine.phase == Phase.RECORDING || engine.phase == Phase.PROCESSING

    // When a meeting has been written and saved, open it straight away.
    LaunchedEffect(engine.phase, engine.savedId) {
        val id = engine.savedId
        if (engine.phase == Phase.DONE && id != null) {
            screen = Screen.Result(id)
            MeetingEngine.reset()
        }
    }

    BackHandler(enabled = !live && screen !is Screen.Home) { screen = Screen.Home }

    if (live) {
        // The recording screen draws behind the system bars on purpose: it is one colour.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) { LiveScreen() }
        return
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                onOpenMeeting = { screen = Screen.Result(it) },
                onOpenSettings = { screen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
            is Screen.Result -> ResultScreen(s.id, onBack = { screen = Screen.Home })
        }
    }
}
