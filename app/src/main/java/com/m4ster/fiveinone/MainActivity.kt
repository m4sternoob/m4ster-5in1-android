package com.m4ster.fiveinone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.m4ster.fiveinone.ui.Screen
import com.m4ster.fiveinone.ui.components.GameScaffold
import com.m4ster.fiveinone.ui.guessing.GuessingScreen
import com.m4ster.fiveinone.ui.home.HomeScreen
import com.m4ster.fiveinone.ui.ladders.LaddersScreen
import com.m4ster.fiveinone.ui.ludo.LudoScreen
import com.m4ster.fiveinone.ui.snake.SnakeScreen
import com.m4ster.fiveinone.ui.theme.FiveInOneTheme
import com.m4ster.fiveinone.ui.tictactoe.TicTacToeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FiveInOneTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val goHome = { screen = Screen.Home }

                when (screen) {
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
                }
            }
        }
    }
}
