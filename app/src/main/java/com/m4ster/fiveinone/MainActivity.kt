package com.m4ster.fiveinone

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.m4ster.fiveinone.ui.Screen
import com.m4ster.fiveinone.ui.components.GameScaffold
import com.m4ster.fiveinone.ui.fourinarow.FourInARowScreen
import com.m4ster.fiveinone.ui.guessing.GuessingScreen
import com.m4ster.fiveinone.ui.home.HomeScreen
import com.m4ster.fiveinone.ui.ladders.LaddersScreen
import com.m4ster.fiveinone.ui.ludo.LudoScreen
import com.m4ster.fiveinone.ui.memory.MemoryScreen
import com.m4ster.fiveinone.ui.settings.SettingsScreen
import com.m4ster.fiveinone.ui.snake.SnakeScreen
import com.m4ster.fiveinone.ui.theme.FiveInOneTheme
import com.m4ster.fiveinone.ui.tictactoe.TicTacToeScreen
import com.m4ster.fiveinone.ui.twenty48.Twenty48Screen
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FiveInOneTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var showExitDialog by remember { mutableStateOf(false) }
                val activity = LocalContext.current as Activity
                val goHome = { screen = Screen.Home }

                // System back gesture: from any game or settings screen,
                // go back one step to Home — never straight out of the app.
                // On Home itself, ask before exiting.
                if (screen != Screen.Home) {
                    BackHandler { screen = Screen.Home }
                } else {
                    BackHandler { showExitDialog = true }
                }

                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { it / 5 }) togetherWith
                                (fadeOut() + slideOutHorizontally { -it / 5 })
                        },
                        label = "screen",
                    ) { target ->
                        when (target) {
                            Screen.Home -> HomeScreen(onOpen = { screen = it })
                            Screen.Guessing -> GameScaffold("Number Guessing", goHome) { p ->
                                GuessingScreen(Modifier.padding(p))
                            }
                            Screen.Snake -> GameScaffold("Snake", goHome) { p ->
                                SnakeScreen(Modifier.padding(p))
                            }
                            Screen.Ladders -> GameScaffold("Snakes & Ladders", goHome) { p ->
                                LaddersScreen(Modifier.padding(p))
                            }
                            Screen.Ludo -> GameScaffold("Ludo", goHome) { p ->
                                LudoScreen(Modifier.padding(p))
                            }
                            Screen.TicTacToe -> GameScaffold("Tic-Tac-Toe", goHome) { p ->
                                TicTacToeScreen(Modifier.padding(p))
                            }
                            Screen.Connect4 -> GameScaffold("Connect 4", goHome) { p ->
                                FourInARowScreen(Modifier.padding(p))
                            }
                            Screen.Memory -> GameScaffold("Memory Match", goHome) { p ->
                                MemoryScreen(Modifier.padding(p))
                            }
                            Screen.Twenty48 -> GameScaffold("2048", goHome) { p ->
                                Twenty48Screen(Modifier.padding(p))
                            }
                            Screen.Settings -> GameScaffold("Settings", goHome) { p ->
                                SettingsScreen(Modifier.padding(p))
                            }
                        }
                    }
                    // FPS meter, top-left on every screen.
                    FpsCounter(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 8.dp, top = 2.dp),
                    )
                }

                if (showExitDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("Exit 5IN1?") },
                        text = { Text("Close the app?") },
                        confirmButton = {
                            TextButton(onClick = { activity.finish() }) { Text("Exit") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
                        },
                    )
                }
            }
        }
    }
}

/* Tiny FPS meter: counts frames with withFrameNanos and refreshes the
   readout once a second. One small Text, nothing allocated per frame.
   Compose renders on the display's vsync, so this shows the real rate
   (60 on 60Hz panels, 120 on 120Hz) — nothing here caps it. */
@Composable
private fun FpsCounter(modifier: Modifier = Modifier) {
    var fps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var windowStart = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            frames++
            val elapsed = now - windowStart
            if (elapsed >= 1_000_000_000L) {
                fps = (frames * 1e9 / elapsed).roundToInt()
                frames = 0
                windowStart = now
            }
        }
    }
    Text(
        text = "$fps fps",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}
