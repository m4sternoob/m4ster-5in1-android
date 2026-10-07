package com.m4ster.fiveinone.ui.twenty48

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.BuildConfig
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.BoardAlt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/* 2048: swipe to slide and merge tiles, chase the 2048 tile. Best
   score is persisted. The withHints flavor gets a Hint button that
   flashes the roomiest move as an arrow over the board. */

@Composable
fun Twenty48Screen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var board by remember { mutableStateOf(newBoard()) }
    var score by remember { mutableStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var gameId by remember { mutableStateOf(0) }
    var hintDir by remember { mutableStateOf<Direction?>(null) }
    var hintTick by remember { mutableStateOf(0) }
    val best by StatsStore.twenty48Best(context).collectAsState(initial = 0)

    // The bank only keeps the high-water mark, so calling it twice is harmless.
    fun bankScore() {
        if (score > 0) scope.launch { StatsStore.recordTwenty48Score(context, score) }
    }

    fun newGame() {
        bankScore()
        gameId++
        board = newBoard()
        score = 0
        over = false
        hintDir = null
    }

    fun doMove(dir: Direction) {
        if (over) return
        hintDir = null
        val result = move(board, dir)
        if (!result.moved) return
        val grown = spawnTile(result.board)
        board = grown
        score += result.gained
        haptics.performHapticFeedback(
            if (result.gained > 0) HapticFeedbackType.LongPress
            else HapticFeedbackType.TextHandleMove,
        )
        if (!canMove(grown)) {
            over = true
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            bankScore()
        }
    }

    // A hinted arrow fades after a beat so it reads as a nudge, not UI.
    // Keyed on gameId: a new game mid-fade cancels the stale arrow.
    LaunchedEffect(hintTick, gameId) {
        if (hintTick == 0) return@LaunchedEffect
        val id = gameId
        delay(1200)
        if (id == gameId) hintDir = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("Score $score", style = MaterialTheme.typography.titleMedium)
            Text(
                if (best > 0) "Best $best" else "Best —",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Square tiles sized off the real width: no aspect-ratio guessing.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(gameId) {
                    var dragTotal = Offset.Zero
                    detectDragGestures(
                        onDragStart = { dragTotal = Offset.Zero },
                        onDrag = { _, amount -> dragTotal += amount },
                        onDragEnd = {
                            val dx = dragTotal.x
                            val dy = dragTotal.y
                            dragTotal = Offset.Zero
                            if (max(abs(dx), abs(dy)) < 24f) return@detectDragGestures
                            doMove(
                                if (abs(dx) > abs(dy)) {
                                    if (dx > 0) Direction.Right else Direction.Left
                                } else {
                                    if (dy > 0) Direction.Down else Direction.Up
                                },
                            )
                        },
                        onDragCancel = { dragTotal = Offset.Zero },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val tile = (maxWidth - 24.dp) / 4
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                board.chunked(GRID_SIZE).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value -> Tile(value, tile) }
                    }
                }
            }
            hintDir?.let { dir ->
                Icon(
                    when (dir) {
                        Direction.Up -> Icons.Filled.ArrowUpward
                        Direction.Down -> Icons.Filled.ArrowDownward
                        Direction.Left -> Icons.Filled.ArrowBack
                        Direction.Right -> Icons.Filled.ArrowForward
                    },
                    contentDescription = "Suggested move",
                    modifier = Modifier.fillMaxSize(0.4f),
                    tint = AccentGreen.copy(alpha = 0.85f),
                )
            }
            if (over) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No moves left", style = MaterialTheme.typography.titleLarge)
                            Text("Score $score", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = ::newGame) { Text("New game") }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The noHints flavor leaves the game pure - no hint button at all.
            if (BuildConfig.HINTS_ENABLED) {
                OutlinedButton(
                    onClick = {
                        suggestMove(board)?.let {
                            hintDir = it
                            hintTick++
                        }
                    },
                    enabled = !over,
                ) { Text("Hint") }
            }
            OutlinedButton(onClick = ::newGame) { Text("New game") }
        }
    }
}

/* One tile: empty cells stay on the board color, numbered tiles climb
   a warm ramp toward green at 2048. Big numbers shrink to fit. */
@Composable
private fun Tile(value: Int, size: Dp, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.size(size),
        colors = CardDefaults.cardColors(containerColor = tileColor(value)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (value != 0) {
                Text(
                    "$value",
                    fontSize = when {
                        value < 100 -> 28.sp
                        value < 1000 -> 24.sp
                        else -> 20.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (value < 8) MaterialTheme.colorScheme.onSurfaceVariant
                    else Color(0xFFFBF7EF),
                )
            }
        }
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> BoardAlt
    2 -> Color(0xFF33415E)
    4 -> Color(0xFF3D4E70)
    8 -> Color(0xFFE08A3C)
    16 -> Color(0xFFE07B2E)
    32 -> Color(0xFFDE6A5A)
    64 -> Color(0xFFDB4F3D)
    128 -> Color(0xFFF2C14E)
    256 -> Color(0xFFF0B429)
    512 -> Color(0xFFEE9F1A)
    1024 -> Color(0xFFEC8F0A)
    2048 -> Color(0xFF4ADE80)
    else -> Color(0xFF60A5FA)
}
