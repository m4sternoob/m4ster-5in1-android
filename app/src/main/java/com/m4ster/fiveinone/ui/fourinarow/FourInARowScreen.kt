package com.m4ster.fiveinone.ui.fourinarow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.BuildConfig
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentAmber
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import com.m4ster.fiveinone.ui.theme.BoardDark
import com.m4ster.fiveinone.ui.theme.GridLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Connect Four: tap a column to drop your disc. Vs CPU on Easy (random)
   or Hard (takes wins, blocks yours, favors the center), or 2-player
   pass-and-play. The noHints flavor leaves the game pure: no Hint button. */

private enum class FourMode { VsCpu, TwoPlayer }
private enum class CpuLevel { Easy, Hard }

@Composable
fun FourInARowScreen(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(FourMode.VsCpu) }
    var level by remember { mutableStateOf(CpuLevel.Hard) }
    var board by remember { mutableStateOf(newFourBoard()) }
    var turn by remember { mutableStateOf(PLAYER) }
    var status by remember { mutableStateOf("Your move") }
    var p1Score by remember { mutableStateOf(0) }
    var p2Score by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }
    var winLine by remember { mutableStateOf<IntArray?>(null) }
    var cpuThinking by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var gamesPlayed by remember { mutableStateOf(0) }
    var thinkDots by remember { mutableStateOf(0) }
    var hintCol by remember { mutableStateOf<Int?>(null) }

    fun nameFor(mark: Char) = if (mode == FourMode.VsCpu) {
        if (mark == PLAYER) "You" else "CPU"
    } else {
        if (mark == PLAYER) "Player 1" else "Player 2"
    }

    fun markColor(mark: Char) = if (mark == PLAYER) AccentBlue else AccentRed

    fun reset() {
        board = newFourBoard()
        winLine = null
        over = false
        turn = PLAYER
        cpuThinking = false
        hintCol = null
        status = if (mode == FourMode.VsCpu) "Your move" else "Player 1 to move"
    }

    fun setMode(m: FourMode) {
        mode = m
        reset()
    }

    fun setLevel(l: CpuLevel) {
        level = l
        reset()
    }

    fun finish(result: Char?, line: IntArray?) {
        winLine = line
        over = true
        gamesPlayed++
        hintCol = null
        if (result != null) {
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        when (result) {
            PLAYER -> {
                p1Score++
                status = if (mode == FourMode.VsCpu) "You win!" else "Player 1 wins!"
                if (mode == FourMode.VsCpu) {
                    scope.launch { StatsStore.recordFourInARowCpuWin(context) }
                }
            }
            CPU -> {
                p2Score++
                status = if (mode == FourMode.VsCpu) "CPU wins!" else "Player 2 wins!"
            }
            else -> {
                draws++
                status = "Draw!"
            }
        }
    }

    fun tapColumn(c: Int) {
        if (over || cpuThinking) return
        if (mode == FourMode.VsCpu && turn != PLAYER) return
        hintCol = null
        val next = dropDisc(board, c, turn) ?: return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        board = next
        val line = winningLineOf(next)
        if (line != null) {
            finish(turn, line)
            return
        }
        if (fourBoardFull(next)) {
            finish(null, null)
            return
        }
        if (mode == FourMode.TwoPlayer) {
            turn = other(turn)
            status = "${nameFor(turn)} to move"
        } else {
            status = "CPU is thinking…"
            cpuThinking = true
        }
    }

    // CPU replies on its own beat. Keyed on cpuThinking, so a reset
    // (which clears the flag) cancels any in-flight reply.
    LaunchedEffect(cpuThinking) {
        if (!cpuThinking) return@LaunchedEffect
        delay(450)
        val move = pickCpuMove(board, level == CpuLevel.Hard)
        if (move >= 0) {
            val next = dropDisc(board, move, CPU) ?: board
            board = next
            val line = winningLineOf(next)
            if (line != null) {
                finish(CPU, line)
            } else if (fourBoardFull(next)) {
                finish(null, null)
            } else {
                status = "Your move"
            }
        }
        cpuThinking = false
    }

    // Animated "thinking…" ellipsis while the CPU replies.
    LaunchedEffect(cpuThinking) {
        if (!cpuThinking) return@LaunchedEffect
        while (true) {
            delay(350)
            thinkDots = (thinkDots + 1) % 4
        }
    }

    val lineSet = winLine?.toSet() ?: emptySet()

    @Composable
    fun ScoreCard(label: String, score: Int, color: Color, active: Boolean) {
        Card(
            border = if (active) BorderStroke(2.dp, color) else null,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                Text("$score", style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    val liveStatus = when {
        over -> status
        cpuThinking -> "CPU is thinking" + ".".repeat(thinkDots)
        mode == FourMode.VsCpu && turn == PLAYER -> "Your move"
        else -> "${nameFor(turn)} to move"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == FourMode.VsCpu,
                onClick = { setMode(FourMode.VsCpu) },
                label = { Text("vs CPU") },
            )
            FilterChip(
                selected = mode == FourMode.TwoPlayer,
                onClick = { setMode(FourMode.TwoPlayer) },
                label = { Text("2 Players") },
            )
        }
        if (mode == FourMode.VsCpu) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = level == CpuLevel.Easy,
                    onClick = { setLevel(CpuLevel.Easy) },
                    label = { Text("Easy") },
                )
                FilterChip(
                    selected = level == CpuLevel.Hard,
                    onClick = { setLevel(CpuLevel.Hard) },
                    label = { Text("Hard") },
                )
            }
        }
        Text(
            "Round ${gamesPlayed + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreCard(nameFor(PLAYER), p1Score, AccentBlue, active = !over && turn == PLAYER)
            ScoreCard("Draws", draws, Color.Gray, active = false)
            ScoreCard(nameFor(CPU), p2Score, AccentRed, active = !over && turn == CPU)
        }
        // Seven tappable columns; discs stack from the bottom up.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BoardDark)
                .padding(8.dp),
        ) {
            for (c in 0 until FOUR_COLS) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { tapColumn(c) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Hint chevron above the suggested column.
                    Box(
                        modifier = Modifier.height(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hintCol == c) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Suggested column",
                                tint = AccentAmber,
                            )
                        }
                    }
                    for (r in 0 until FOUR_ROWS) {
                        val i = r * FOUR_COLS + c
                        val mark = board[i]
                        val lit = i in lineSet
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // The empty hole.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(GridLine.copy(alpha = 0.45f)),
                            )
                            // AnimatedVisibility needs a ColumnScope/RowScope
                            // receiver, so the disc sits in a Column.
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AnimatedVisibility(
                                    visible = mark != ' ',
                                    enter = scaleIn(),
                                    label = "disc",
                                ) {
                                    val disc = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(markColor(mark))
                                    Box(
                                        if (lit) disc.border(3.dp, AccentGreen, CircleShape)
                                        else disc,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!over) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(markColor(turn)),
                )
            }
            Text(liveStatus, style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The noHints flavor leaves the game pure - no hint button at all.
            if (BuildConfig.HINTS_ENABLED) {
                OutlinedButton(
                    onClick = {
                        hintCol = suggestColumn(board, turn).takeIf { it >= 0 }
                    },
                    enabled = !over && !cpuThinking,
                ) { Text("Hint") }
            }
            if (!over) {
                OutlinedButton(onClick = ::reset) { Text("Restart") }
            }
        }
        AnimatedVisibility(visible = over, enter = scaleIn()) {
            Button(onClick = ::reset) { Text("Play again") }
        }
        if (celebrating) {
            Dialog(
                onDismissRequest = { celebrating = false },
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Celebration { celebrating = false }
            }
        }
    }
}
