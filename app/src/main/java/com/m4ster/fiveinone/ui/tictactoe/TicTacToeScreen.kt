package com.m4ster.fiveinone.ui.tictactoe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlinx.coroutines.delay

/* Tic-tac-toe two ways: you (X) vs the CPU (O) on Easy or Hard, or two
   players pass-and-play. Hard CPU is full minimax with depth
   tie-breaking: it never loses and prefers the fastest win. */

private const val PLAYER = 'X'
private const val CPU = 'O'

private enum class TttMode { VsCpu, TwoPlayer }
private enum class CpuLevel { Easy, Hard }

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

private fun minimax(b: CharArray, isCpu: Boolean, depth: Int): Int {
    winnerOf(b)?.let { return if (it == CPU) 10 - depth else depth - 10 }
    if (' ' !in b) return 0
    var best = if (isCpu) Int.MIN_VALUE else Int.MAX_VALUE
    for (i in b.indices) {
        if (b[i] != ' ') continue
        b[i] = if (isCpu) CPU else PLAYER
        val score = minimax(b, !isCpu, depth + 1)
        b[i] = ' '
        best = if (isCpu) maxOf(best, score) else minOf(best, score)
    }
    return best
}

private fun bestCpuMove(b: CharArray): Int {
    var bestScore = Int.MIN_VALUE
    var bestMove = -1
    // Center, then corners, then edges: nicer play when scores tie.
    val order = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7)
    for (i in order) {
        if (b[i] != ' ') continue
        b[i] = CPU
        val score = minimax(b, false, 0)
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
    var mode by remember { mutableStateOf(TttMode.VsCpu) }
    var level by remember { mutableStateOf(CpuLevel.Hard) }
    var board by remember { mutableStateOf(CharArray(9) { ' ' }) }
    var turn by remember { mutableStateOf(PLAYER) }
    var status by remember { mutableStateOf("Your move (X)") }
    var xScore by remember { mutableStateOf(0) }
    var oScore by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }
    var winLine by remember { mutableStateOf<IntArray?>(null) }
    var cpuThinking by remember { mutableStateOf(false) }

    fun reset() {
        board = CharArray(9) { ' ' }
        winLine = null
        turn = PLAYER
        cpuThinking = false
        status = if (mode == TttMode.VsCpu) "Your move (X)" else "Player X to move"
    }

    fun setMode(m: TttMode) {
        mode = m
        reset()
    }

    fun setLevel(l: CpuLevel) {
        level = l
        reset()
    }

    fun finish(result: Char?, line: IntArray?) {
        winLine = line
        when (result) {
            PLAYER -> {
                xScore++
                status = if (mode == TttMode.VsCpu) "You win!" else "Player X wins!"
            }
            CPU -> {
                oScore++
                status = if (mode == TttMode.VsCpu) "CPU wins!" else "Player O wins!"
            }
            else -> {
                draws++
                status = "Draw!"
            }
        }
    }

    fun tap(i: Int) {
        if (cpuThinking || winLine != null || winnerOf(board) != null || board[i] != ' ') return
        if (mode == TttMode.VsCpu && turn != PLAYER) return
        val mark = turn
        val next = board.copyOf().also { it[i] = mark }
        board = next
        val line = winLineOf(next)
        if (line != null) {
            finish(mark, line)
            return
        }
        if (' ' !in next) {
            finish(null, null)
            return
        }
        if (mode == TttMode.TwoPlayer) {
            turn = if (turn == PLAYER) CPU else PLAYER
            status = "Player $turn to move"
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
        val move = if (level == CpuLevel.Hard) bestCpuMove(next)
            else empties.randomOrNull() ?: -1
        if (move >= 0) {
            next[move] = CPU
            board = next
            val line = winLineOf(next)
            if (line != null) {
                finish(CPU, line)
            } else if (' ' !in next) {
                finish(null, null)
            } else {
                status = "Your move (X)"
            }
        }
        cpuThinking = false
    }

    val lineSet = winLine?.toSet() ?: emptySet()
    val scoreLine = if (mode == TttMode.VsCpu) "You $xScore · CPU $oScore · Draws $draws"
        else "X $xScore · O $oScore · Draws $draws"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                setMode(if (mode == TttMode.VsCpu) TttMode.TwoPlayer else TttMode.VsCpu)
            }) {
                Text(if (mode == TttMode.VsCpu) "vs CPU" else "2 Players")
            }
            OutlinedButton(
                onClick = { setLevel(if (level == CpuLevel.Hard) CpuLevel.Easy else CpuLevel.Hard) },
                enabled = mode == TttMode.VsCpu,
            ) {
                Text(if (level == CpuLevel.Hard) "Hard" else "Easy")
            }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
        }
        Text(scoreLine, style = MaterialTheme.typography.titleMedium)
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
        Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}
