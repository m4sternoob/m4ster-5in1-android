package com.m4ster.fiveinone.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m4ster.fiveinone.R
import com.m4ster.fiveinone.ui.Screen
import com.m4ster.fiveinone.ui.stats.GuessStats
import com.m4ster.fiveinone.ui.stats.StatsStore

private data class GameEntry(
    val screen: Screen,
    val title: String,
    val blurb: String,
    val cardArt: Int,
)

private val Games = listOf(
    GameEntry(Screen.Guessing, "Number Guessing", "Guess 1–100 with hot/cold hints", R.drawable.card_guessing),
    GameEntry(Screen.Snake, "Snake", "Eat, grow, don't crash", R.drawable.card_snake),
    GameEntry(Screen.Ladders, "Snakes & Ladders", "Race to square 100 — vs CPU or a friend", R.drawable.card_ladders),
    GameEntry(Screen.Ludo, "Ludo", "You vs CPU — bring both tokens home", R.drawable.card_ludo),
    GameEntry(Screen.TicTacToe, "Tic-Tac-Toe", "Vs CPU or a friend — minimax on Hard", R.drawable.card_tictactoe),
    GameEntry(Screen.Connect4, "Connect 4", "Drop discs — connect four to win", R.drawable.card_connect4),
    GameEntry(Screen.Memory, "Memory Match", "Flip pairs — fewest moves wins", R.drawable.card_memory),
    GameEntry(Screen.Twenty48, "2048", "Slide and merge — chase the 2048 tile", R.drawable.card_2048),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val guessStats by StatsStore.guessStats(context).collectAsState(initial = GuessStats(0, 0, 0))
    val snakeBest by StatsStore.snakeBest(context).collectAsState(initial = 0)
    val memoryBest by StatsStore.memoryBest(context).collectAsState(initial = 0)
    val twenty48Best by StatsStore.twenty48Best(context).collectAsState(initial = 0)
    val fourInARowWins by StatsStore.fourInARowCpuWins(context).collectAsState(initial = 0)
    // Live stat line under the blurb, only once there's something to show.
    val statFor: Map<Screen, String> = mapOf(
        Screen.Guessing to if (guessStats.wins > 0)
            "Wins ${guessStats.wins} · Streak ${guessStats.streak}" else "",
        Screen.Snake to if (snakeBest > 0) "Best $snakeBest" else "",
        Screen.Connect4 to if (fourInARowWins > 0) "Vs CPU wins $fourInARowWins" else "",
        Screen.Memory to if (memoryBest > 0) "Best $memoryBest moves" else "",
        Screen.Twenty48 to if (twenty48Best > 0) "Best $twenty48Best" else "",
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("5IN1") },
                actions = {
                    IconButton(onClick = { onOpen(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ui_header_banner),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                    Text(
                        "8 games · one app",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            items(Games) { game ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(game.screen) },
                ) {
                    Column {
                        Image(
                            painter = painterResource(game.cardArt),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.padding(16.dp)) {
                            Text(game.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                game.blurb,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val stat = statFor[game.screen].orEmpty()
                            if (stat.isNotEmpty()) {
                                Text(
                                    stat,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
