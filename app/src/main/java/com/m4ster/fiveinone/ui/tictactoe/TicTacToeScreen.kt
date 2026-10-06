package com.m4ster.fiveinone.ui.tictactoe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlinx.coroutines.delay

/* Tic-tac-toe two ways: you (X) vs the CPU (O) on Easy or Hard, or two
   players pass-and-play. Hard CPU is full minimax with depth
   tie-breaking: it never loses and prefers the fastest win.
   Misere flips the goal: completing 3 in a row loses the round. */

private const val PLAYER = 'X'
private const val CPU = 'O'

private enum class TttMode { VsCpu, TwoPlayer }
private enum class CpuLevel { Easy, Hard }
private enum class TttRule { Classic, Misere }

private val Wins = arrayOf(
    intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
    intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
    intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
)

private fun winnerOf(b: CharArray): Char? {
    for (w in Wins) {
        if (b[w[0]] != ' ' && b[w[0]] == b[w[1]] && b[w[1]] == b[w[2]]) return b[w[0]]
    }
    return null
}

private fun winLineOf(b: CharArray): IntArray? {
    for (w in Wins) {
        if (b[w[0]] != ' ' && b[w[0]] == b[w[1]] && b[w[1]] == b[w[2]]) return w
    }
    return null
}

private fun other(mark: Char) = if (mark == PLAYER) CPU else PLAYER

/* In misere the line-completer loses, so the terminal scores flip:
   a CPU line is bad for the CPU, a player line is good. */
private fun minimax(b: CharArray, isCpu: Boolean, depth: Int, misere: Boolean): Int {
    winnerOf(b)?.let {
        val cpuMadeLine = it == CPU
        return if (misere != cpuMadeLine) 10 - depth else depth - 10
    }
    if (' ' !in b) return 0
    var best = if (isCpu) Int.MIN_VALUE else Int.MAX_VALUE
    for (i in b.indices) {
        if (b[i] != ' ') continue
        b[i] = if (isCpu) CPU else PLAYER
        val score = minimax(b, !isCpu, depth + 1, misere)
        b[i] = ' '
        best = if (isCpu) maxOf(best, score) else minOf(best, score)
    }
    return best
}

private fun bestCpuMove(b: CharArray, misere: Boolean): Int {
    var bestScore = Int.MIN_VALUE
    var bestMove = -1
    // Center, then corners, then edges: nicer play when scores tie.
    val order = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7)
    for (i in order) {
        if (b[i] != ' ') continue
        b[i] = CPU
        val score = minimax(b, false, 0, misere)
        b[i] = ' '
        if (score > bestScore) {
            bestScore = score
            bestMove = i
        }
    }
    return bestMove
}

@Composable
fun TicTacToeScreen(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var mode by remember { mutableStateOf(TttMode.VsCpu) }
    var level by remember { mutableStateOf(CpuLevel.Hard) }
    var rule by remember { mutableStateOf(TttRule.Classic) }
    var board by remember { mutableStateOf(CharArray(9) { ' ' }) }
    var turn by remember { mutableStateOf(PLAYER) }
    var status by remember { mutableStateOf("Your move (X)") }
    var xScore by remember { mutableStateOf(0) }
    var oScore by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }
    var winLine by remember { mutableStateOf<IntArray?>(null) }
    var cpuThinking by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var gamesPlayed by remember { mutableStateOf(0) }
    var thinkDots by remember { mutableStateOf(0) }

    fun nameFor(mark: Char) = if (mode == TttMode.VsCpu) {
        if (mark == PLAYER) "You" else "CPU"
    } else {
        if (mark == PLAYER) "Player 1" else "Player 2"
    }

    fun markColor(mark: Char) = if (mark == PLAYER) AccentBlue else AccentRed

    fun reset() {
        board = CharArray(9) { ' ' }
        winLine = null
        over = false
        turn = PLAYER
        cpuThinking = false
        status = if (mode == TttMode.VsCpu) "Your move (X)" else "Player 1 (X) to move"
    }

    fun setMode(m: TttMode) {
        mode = m
        reset()
    }

    fun setLevel(l: CpuLevel) {
        level = l
        reset()
    }

    fun setRule(r: TttRule) {
        rule = r
        reset()
    }

    fun finish(result: Char?, line: IntArray?) {
        winLine = line
        over = true
        gamesPlayed++
        if (result != null) {
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        when (result) {
            PLAYER -> {
                xScore++
                status = if (mode == TttMode.VsCpu) "You win!" else "Player 1 (X) wins!"
            }
            CPU -> {
                oScore++
                status = if (mode == TttMode.VsCpu) "CPU wins!" else "Player 2 (O) wins!"
            }
            else -> {
                draws++
                status = "Draw!"
            }
        }
    }

    fun tap(i: Int) {
        if (over || cpuThinking || winnerOf(board) != null || board[i] != ' ') return
        if (mode == TttMode.VsCpu && turn != PLAYER) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val mark = turn
        val next = board.copyOf().also { it[i] = mark }
        board = next
        val line = winLineOf(next)
        if (line != null) {
            // Misere: the one who completes the line loses.
            finish(if (rule == TttRule.Misere) other(mark) else mark, line)
            return
        }
        if (' ' !in next) {
            finish(null, null)
            return
        }
        if (mode == TttMode.TwoPlayer) {
            turn = if (turn == PLAYER) CPU else PLAYER
            status = "${nameFor(turn)} ($turn) to move"
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
        val next = board.copyOf()
        val empties = next.indices.filter { next[it] == ' ' }
        val misere = rule == TttRule.Misere
        val move = if (level == CpuLevel.Hard) bestCpuMove(next, misere)
            else empties.randomOrNull() ?: -1
        if (move >= 0) {
            next[move] = CPU
            board = next
            val line = winLineOf(next)
            if (line != null) {
                finish(if (misere) PLAYER else CPU, line)
            } else if (' ' !in next) {
                finish(null, null)
            } else {
                status = "Your move (X)"
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
        mode == TttMode.VsCpu && turn == PLAYER -> "Your move (X)"
        else -> "${nameFor(turn)} ($turn) to move"
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
                selected = mode == TttMode.VsCpu,
                onClick = { setMode(TttMode.VsCpu) },
                label = { Text("vs CPU") },
            )
            FilterChip(
                selected = mode == TttMode.TwoPlayer,
                onClick = { setMode(TttMode.TwoPlayer) },
                label = { Text("2 Players") },
            )
        }
        if (mode == TttMode.VsCpu) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule == TttRule.Classic,
                onClick = { setRule(TttRule.Classic) },
                label = { Text("Classic") },
            )
            FilterChip(
                selected = rule == TttRule.Misere,
                onClick = { setRule(TttRule.Misere) },
                label = { Text("Misère") },
            )
        }
        if (rule == TttRule.Misere) {
            Text(
                "Misère: completing 3 in a row loses.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Round ${gamesPlayed + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreCard("${nameFor(PLAYER)} (X)", xScore, AccentBlue, active = !over && turn == PLAYER)
            ScoreCard("Draws", draws, Color.Gray, active = false)
            ScoreCard("${nameFor(CPU)} (O)", oScore, AccentRed, active = !over && turn == CPU)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val i = r * 3 + c
                        val mark = board[i]
                        val lit = i in lineSet
                        val bg by animateColorAsState(
                            if (lit) AccentGreen.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface,
                            label = "cellbg",
                        )
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable { tap(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // AnimatedVisibility needs a ColumnScope/RowScope receiver,
                            // so the mark sits in a Column inside the Box.
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AnimatedVisibility(
                                    visible = mark != ' ',
                                    enter = scaleIn(),
                                    label = "mark",
                                ) {
                                    Text(
                                        mark.toString(),
                                        fontSize = 44.sp,
                                        color = if (mark == PLAYER) AccentBlue else AccentRed,
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
        AnimatedVisibility(visible = over, enter = scaleIn()) {
            Button(onClick = ::reset) { Text("Play again") }
        }
        if (!over) {
            OutlinedButton(onClick = ::reset) { Text("Restart") }
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
