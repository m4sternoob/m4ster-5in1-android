package com.m4ster.fiveinone.ui.tictactoe

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
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlinx.coroutines.delay

/* You (X) vs CPU (O). Full minimax with depth tie-breaking: the CPU
   never loses and prefers the fastest win / slowest loss. */

private const val PLAYER = 'X'
private const val CPU = 'O'

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
    var board by remember { mutableStateOf(CharArray(9) { ' ' }) }
    var status by remember { mutableStateOf("Your move (X)") }
    var playerScore by remember { mutableStateOf(0) }
    var cpuScore by remember { mutableStateOf(0) }
    var draws by remember { mutableStateOf(0) }
    var cpuThinking by remember { mutableStateOf(false) }

    fun reset() {
        board = CharArray(9) { ' ' }
        status = "Your move (X)"
        cpuThinking = false
    }

    fun finish(result: Char?) {
        when (result) {
            PLAYER -> {
                playerScore++
                status = "You win!"
            }
            CPU -> {
                cpuScore++
                status = "CPU wins!"
            }
            else -> {
                draws++
                status = "Draw!"
            }
        }
    }

    fun tap(i: Int) {
        if (cpuThinking || winnerOf(board) != null || board[i] != ' ') return
        val next = board.copyOf().also { it[i] = PLAYER }
        board = next
        winnerOf(next)?.let { finish(it); return }
        if (' ' !in next) {
            finish(null)
            return
        }
        status = "CPU is thinking…"
        cpuThinking = true
    }

    // CPU replies on its own beat. Keyed on cpuThinking, so a reset
    // (which clears the flag) cancels any in-flight reply.
    LaunchedEffect(cpuThinking) {
        if (!cpuThinking) return@LaunchedEffect
        delay(450)
        val next = board.copyOf()
        val move = bestCpuMove(next)
        if (move >= 0) {
            next[move] = CPU
            board = next
            val w = winnerOf(next)
            if (w != null) {
                finish(w)
            } else if (' ' !in next) {
                finish(null)
            } else {
                status = "Your move (X)"
            }
        }
        cpuThinking = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "You $playerScore · CPU $cpuScore · Draws $draws",
            style = MaterialTheme.typography.titleMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val i = r * 3 + c
                        val mark = board[i]
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { tap(i) },
                            contentAlignment = Alignment.Center,
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
        Text(status, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = ::reset) { Text("Restart") }
    }
}
