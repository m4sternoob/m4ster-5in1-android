package com.m4ster.fiveinone.ui.ladders

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import com.m4ster.fiveinone.ui.theme.BoardAlt
import com.m4ster.fiveinone.ui.theme.BoardDark
import com.m4ster.fiveinone.ui.theme.GridLine
import kotlinx.coroutines.delay

/* Snakes & Ladders: 1 player vs CPU, or 2 players pass-and-play.
   10×10 boustrophedon board, portals drawn as colored links.
   Exact roll needed to finish. CPU moves on a short delay. */

private val Portals = mapOf(
    // ladders
    4 to 25, 13 to 46, 33 to 49, 42 to 63, 50 to 69, 62 to 81, 74 to 92,
    // snakes
    27 to 5, 40 to 3, 43 to 18, 54 to 34, 66 to 45, 76 to 58, 89 to 53, 99 to 41,
)
private val LadderFeet = setOf(4, 13, 33, 42, 50, 62, 74)

private enum class Mode { VsCpu, TwoPlayer }

private val TokenColors = listOf(AccentBlue, AccentRed)

/** Center of square n (1..100) on a size×size board. Row 1 is the bottom. */
private fun cellCenter(n: Int, size: Float): Offset {
    val cell = size / 10f
    val rowFromBottom = (n - 1) / 10
    val posInRow = (n - 1) % 10
    val col = if (rowFromBottom % 2 == 0) posInRow else 9 - posInRow
    val rowFromTop = 9 - rowFromBottom
    return Offset((col + 0.5f) * cell, (rowFromTop + 0.5f) * cell)
}

/** Triple(landedSquare, finalSquare, portalKind: "ladder"/"snake"/null). */
private fun resolveMove(from: Int, roll: Int): Triple<Int, Int, String?> {
    val landed = (if (from == 0) 0 else from) + roll
    if (landed > 100) return Triple(from, from, null) // need exact roll
    val dest = Portals[landed]
    return if (dest != null) {
        Triple(landed, dest, if (landed in LadderFeet) "ladder" else "snake")
    } else {
        Triple(landed, landed, null)
    }
}

private val DiceFaces = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

@Composable
fun LaddersScreen(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf<Mode?>(null) }
    var positions by remember { mutableStateOf(listOf(0, 0)) } // 0 = off the board
    var turn by remember { mutableStateOf(0) }
    var dice by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("Race to 100!") }
    var winner by remember { mutableStateOf<Int?>(null) }
    var cpuTrigger by remember { mutableStateOf(0) }
    var gameId by remember { mutableStateOf(0) }

    val names = when (mode) {
        Mode.VsCpu -> listOf("You", "CPU")
        Mode.TwoPlayer -> listOf("Player 1", "Player 2")
        null -> listOf("Player 1", "Player 2")
    }

    fun reset() {
        positions = listOf(0, 0)
        turn = 0
        dice = null
        winner = null
        message = "Race to 100 — tap Roll!"
        gameId++ // cancels any in-flight CPU turn
    }

    fun pickMode(m: Mode) {
        mode = m
        reset()
    }

    fun moveMessage(who: String, from: Int, roll: Int): String {
        val (landed, final, portal) = resolveMove(from, roll)
        val fromLabel = if (from == 0) "start" else "$from"
        return when {
            final == 100 -> "$who rolled $roll and reached 100!"
            landed == from -> "$who rolled $roll — needs exactly ${100 - from} to finish."
            portal == "ladder" -> "$who rolled $roll: $fromLabel → $landed, ladder up to $final!"
            portal == "snake" -> "$who rolled $roll: $fromLabel → $landed, snake down to $final."
            else -> "$who rolled $roll: $fromLabel → $final"
        }
    }

    /** Applies one roll for player `who` and advances the turn. */
    fun applyRoll(who: Int, roll: Int) {
        dice = roll
        val (_, final, _) = resolveMove(positions[who], roll)
        message = moveMessage(names[who], positions[who], roll)
        positions = positions.toMutableList().also { it[who] = final }
        if (final == 100) {
            winner = who
            val verb = if (names[who] == "You") "win" else "wins"
            message = "${names[who]} rolled $roll and reached 100 — ${names[who]} $verb!"
        } else {
            turn = 1 - who
            if (mode == Mode.VsCpu && turn == 1) cpuTrigger++
        }
    }

    fun humanRoll() {
        if (mode == null || winner != null) return
        // In VsCpu mode the CPU (player 2) rolls itself.
        if (mode == Mode.VsCpu && turn == 1) return
        applyRoll(turn, (1..6).random())
    }

    // CPU acts on its own beat. gameId in the key cancels a stale turn on reset.
    LaunchedEffect(cpuTrigger, gameId) {
        val id = gameId
        if (mode != Mode.VsCpu || cpuTrigger == 0 || turn != 1) return@LaunchedEffect
        delay(800)
        if (id != gameId) return@LaunchedEffect
        applyRoll(1, (1..6).random())
    }

    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // Mode picker — shown before the first game and when switching modes.
    if (mode == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Snakes & Ladders", style = MaterialTheme.typography.headlineMedium)
            Text("Race to 100. Land on a ladder to climb, dodge the snakes.")
            Button(onClick = { pickMode(Mode.VsCpu) }) { Text("1 Player (vs CPU)") }
            Button(onClick = { pickMode(Mode.TwoPlayer) }) { Text("2 Players (pass & play)") }
        }
        return
    }

    val turnLabel = when {
        mode == Mode.VsCpu && turn == 0 -> "Your turn"
        mode == Mode.VsCpu -> "CPU's turn"
        else -> "${names[turn]}'s turn"
    }
    val posLabel = { i: Int -> if (positions[i] == 0) "start" else "${positions[i]}" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(turnLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                dice?.let { DiceFaces[it - 1] } ?: "🎲",
                fontSize = 36.sp,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cell = size.width / 10f
            for (n in 1..100) {
                val c = cellCenter(n, size.width)
                val rowFromBottom = (n - 1) / 10
                drawRect(
                    color = if ((rowFromBottom + n) % 2 == 0) BoardDark else BoardAlt,
                    topLeft = Offset(c.x - cell / 2, c.y - cell / 2),
                    size = Size(cell, cell),
                )
            }
            for (i in 0..10) {
                val p = i * cell
                drawLine(GridLine, Offset(p, 0f), Offset(p, size.width), 1f)
                drawLine(GridLine, Offset(0f, p), Offset(size.width, p), 1f)
            }
            // Portals: green = ladder up, red = snake down.
            for ((from, to) in Portals) {
                val a = cellCenter(from, size.width)
                val b = cellCenter(to, size.width)
                val color = if (from in LadderFeet) AccentGreen else AccentRed
                drawLine(color, a, b, strokeWidth = cell * 0.12f)
                drawCircle(color, radius = cell * 0.16f, center = a)
                drawCircle(color, radius = cell * 0.16f, center = b)
            }
            // Square numbers.
            labelPaint.textSize = cell * 0.26f
            val native = drawContext.canvas.nativeCanvas
            for (n in 1..100) {
                val c = cellCenter(n, size.width)
                native.drawText(n.toString(), c.x, c.y + cell * 0.09f, labelPaint)
            }
            // Tokens (offset when sharing a square).
            val shared = positions[0] != 0 && positions[0] == positions[1]
            positions.forEachIndexed { i, pos ->
                if (pos == 0) return@forEachIndexed
                val c = cellCenter(pos, size.width)
                val shift = if (shared) (if (i == 0) -cell * 0.18f else cell * 0.18f) else 0f
                val center = Offset(c.x + shift, c.y)
                drawCircle(TokenColors[i], cell * 0.3f, center)
                drawCircle(Color.White, cell * 0.3f, center, style = Stroke(cell * 0.06f))
            }
        }
        Text("${names[0]}: ${posLabel(0)}   ${names[1]}: ${posLabel(1)}")
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = ::humanRoll,
                enabled = winner == null && !(mode == Mode.VsCpu && turn == 1),
            ) { Text("Roll") }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
            OutlinedButton(onClick = { mode = null }) { Text("Mode") }
        }
        if (winner != null) {
            val w = winner!!
            Text(
                when {
                    mode == Mode.VsCpu && w == 0 -> "You win!"
                    mode == Mode.VsCpu -> "CPU wins!"
                    else -> "${names[w]} wins!"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = TokenColors[w],
            )
        }
    }
}
