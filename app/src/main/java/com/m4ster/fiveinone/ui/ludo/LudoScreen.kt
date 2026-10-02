package com.m4ster.fiveinone.ui.ludo

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import com.m4ster.fiveinone.ui.theme.BoardAlt
import com.m4ster.fiveinone.ui.theme.BoardDark
import com.m4ster.fiveinone.ui.theme.GridLine
import kotlinx.coroutines.delay
import kotlin.math.abs

/* Simplified Ludo, you vs CPU. 24-cell track (6 cols × 4 rows, boustrophedon)
   plus a base strip. Two tokens each; roll a 6 to leave base; landing on an
   enemy token off a safe square captures it; exact roll to finish (cell 24).
   Roll a 6 for an extra turn. First to bring both tokens home wins. */

private const val TRACK = 24
private const val PLAYER = 0
private const val CPU = 1
private const val BASE = -1
private const val HOME = 24

private fun startOf(player: Int) = if (player == PLAYER) 0 else 12
private val Safe = setOf(0, 12)

/** Center of track cell i on a w×h board (track occupies rows 0..3). */
private fun trackCenter(i: Int, w: Float, h: Float): Offset {
    val cw = w / 6f
    val ch = h / 5f
    val row = i / 6
    val pos = i % 6
    val col = if (row % 2 == 0) pos else 5 - pos
    return Offset((col + 0.5f) * cw, (row + 0.5f) * ch)
}

/** Center of a base slot: row 4; player uses cols 1–2, CPU cols 4–5.
 *  Slightly below strip center so the top labels don't overlap tokens. */
private fun baseCenter(player: Int, slot: Int, w: Float, h: Float): Offset {
    val cw = w / 6f
    val ch = h / 5f
    val col = if (player == PLAYER) 1 + slot else 4 + slot
    return Offset((col + 0.5f) * cw, 4.62f * ch)
}

private val DiceFaces = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

@Composable
fun LudoScreen(modifier: Modifier = Modifier) {
    var playerTokens by remember { mutableStateOf(intArrayOf(BASE, BASE)) }
    var cpuTokens by remember { mutableStateOf(intArrayOf(BASE, BASE)) }
    var turn by remember { mutableStateOf(PLAYER) }
    var dice by remember { mutableStateOf<Int?>(null) }
    var movables by remember { mutableStateOf(emptyList<Int>()) }
    var message by remember { mutableStateOf("Your turn — tap Roll.") }
    var winner by remember { mutableStateOf<Int?>(null) }
    var cpuTrigger by remember { mutableStateOf(0) }
    var gameId by remember { mutableStateOf(0) }
    var boardPx by remember { mutableStateOf(IntSize.Zero) }

    fun reset() {
        playerTokens = intArrayOf(BASE, BASE)
        cpuTokens = intArrayOf(BASE, BASE)
        turn = PLAYER
        dice = null
        movables = emptyList()
        winner = null
        message = "Your turn — tap Roll."
        gameId++ // cancels any in-flight CPU turn
    }

    fun movableFor(player: Int, roll: Int): List<Int> {
        val tokens = if (player == PLAYER) playerTokens else cpuTokens
        return tokens.indices.filter { t ->
            val p = tokens[t]
            p != HOME && ((p == BASE && roll == 6) || (p >= 0 && p + roll <= HOME))
        }
    }

    fun hasWon(player: Int): Boolean {
        val tokens = if (player == PLAYER) playerTokens else cpuTokens
        return tokens.all { it == HOME }
    }

    fun wouldCapture(player: Int, token: Int, roll: Int): Boolean {
        val mine = if (player == PLAYER) playerTokens else cpuTokens
        val theirs = if (player == PLAYER) cpuTokens else playerTokens
        val from = mine[token]
        val dest = if (from == BASE) startOf(player) else from + roll
        return dest != HOME && dest !in Safe && dest in theirs
    }

    /** Applies a move. Returns (extraTurn, captured). Always copies the
     *  token arrays so Compose sees a new value and recomposes. */
    fun doMove(player: Int, token: Int, roll: Int): Pair<Boolean, Boolean> {
        val mine = (if (player == PLAYER) playerTokens else cpuTokens).copyOf()
        val theirs = (if (player == PLAYER) cpuTokens else playerTokens).copyOf()
        var dest = mine[token]
        dest = if (dest == BASE) startOf(player) else dest + roll
        var captured = false
        if (dest == HOME) {
            mine[token] = HOME
        } else {
            if (dest !in Safe) {
                for (o in theirs.indices) {
                    if (theirs[o] == dest) {
                        theirs[o] = BASE
                        captured = true
                    }
                }
            }
            mine[token] = dest
        }
        if (player == PLAYER) {
            playerTokens = mine
            cpuTokens = theirs
        } else {
            cpuTokens = mine
            playerTokens = theirs
        }
        return (roll == 6) to captured
    }

    fun playerRoll() {
        if (turn != PLAYER || dice != null || winner != null) return
        val r = (1..6).random()
        dice = r
        val moves = movableFor(PLAYER, r)
        movables = moves
        if (moves.isEmpty()) {
            message = "You rolled $r — no moves possible."
            turn = CPU
            dice = null
            cpuTrigger++
        } else {
            message = "You rolled $r — tap a glowing token."
        }
    }

    fun playerMoveToken(t: Int) {
        val r = dice ?: return
        if (turn != PLAYER || t !in movables || winner != null) return
        val (extra, captured) = doMove(PLAYER, t, r)
        dice = null
        movables = emptyList()
        if (hasWon(PLAYER)) {
            winner = PLAYER
            message = "You brought both tokens home — you win!"
            return
        }
        message = when {
            captured -> "You captured a CPU token!"
            extra -> "You rolled a 6 — roll again!"
            else -> "CPU's turn…"
        }
        if (!extra) {
            turn = CPU
            cpuTrigger++
        }
    }

    /** One CPU roll+move. Returns true if the CPU earned another turn. */
    fun cpuRollOnce(): Boolean {
        val r = (1..6).random()
        dice = r
        val moves = movableFor(CPU, r)
        var extra = false
        if (moves.isNotEmpty()) {
            // Prefer captures, otherwise any legal move.
            val pick = moves.firstOrNull { wouldCapture(CPU, it, r) } ?: moves.random()
            val (e, captured) = doMove(CPU, pick, r)
            extra = e
            message = if (captured) "CPU rolled $r and captured your token!" else "CPU rolled $r."
        } else {
            message = "CPU rolled $r — no moves."
        }
        if (hasWon(CPU)) {
            winner = CPU
            message = "CPU brought both tokens home — CPU wins."
            dice = null
            return false
        }
        if (!extra) {
            turn = PLAYER
            dice = null
        }
        return extra
    }

    // CPU acts on its own beat, chaining extra turns. gameId cancels a stale
    // turn when the player hits Restart mid-CPU-turn.
    LaunchedEffect(cpuTrigger, gameId) {
        val id = gameId
        if (cpuTrigger == 0 || turn != CPU) return@LaunchedEffect
        delay(800)
        if (id != gameId) return@LaunchedEffect
        var again = cpuRollOnce()
        while (again && id == gameId && winner == null) {
            delay(800)
            again = cpuRollOnce()
        }
    }

    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    winner == PLAYER -> "You win!"
                    winner == CPU -> "CPU wins!"
                    turn == PLAYER -> "Your turn"
                    else -> "CPU's turn"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(dice?.let { DiceFaces[it - 1] } ?: "🎲", fontSize = 36.sp)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(6f / 5f)
                .onSizeChanged { boardPx = it }
                .pointerInput(dice, movables, turn, winner, boardPx, playerTokens) {
                    detectTapGestures { tap ->
                        if (turn != PLAYER || dice == null || winner != null) return@detectTapGestures
                        if (boardPx == IntSize.Zero) return@detectTapGestures
                        val w = boardPx.width.toFloat()
                        val h = boardPx.height.toFloat()
                        val cw = w / 6f
                        val ch = h / 5f
                        val hit = movables.firstOrNull { t ->
                            val p = playerTokens[t]
                            val c = when {
                                p == BASE -> baseCenter(PLAYER, t, w, h)
                                p in 0 until TRACK -> trackCenter(p, w, h)
                                else -> null // HOME tokens aren't tappable
                            } ?: return@firstOrNull false
                            abs(tap.x - c.x) <= cw * 0.5f && abs(tap.y - c.y) <= ch * 0.5f
                        }
                        if (hit != null) playerMoveToken(hit)
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val cw = w / 6f
            val ch = h / 5f
            // Track cells; safe squares tinted green.
            for (i in 0 until TRACK) {
                val c = trackCenter(i, w, h)
                drawRect(
                    color = if (i in Safe) Color(0xFF1E3A2A) else if (i % 2 == 0) BoardDark else BoardAlt,
                    topLeft = Offset(c.x - cw / 2, c.y - ch / 2),
                    size = Size(cw, ch),
                )
            }
            for (gx in 0..6) drawLine(GridLine, Offset(gx * cw, 0f), Offset(gx * cw, 4 * ch), 1f)
            for (gy in 0..4) drawLine(GridLine, Offset(0f, gy * ch), Offset(w, gy * ch), 1f)
            // Base strip.
            drawRect(Color(0xFF101A30), topLeft = Offset(0f, 4 * ch), size = Size(w, ch))
            drawLine(GridLine, Offset(0f, 4 * ch), Offset(w, 4 * ch), 2f)
            drawLine(GridLine, Offset(3 * cw, 4 * ch), Offset(3 * cw, h), 2f)
            // Start markers (under tokens).
            drawCircle(AccentGreen, cw * 0.1f, trackCenter(0, w, h))
            drawCircle(AccentGreen, cw * 0.1f, trackCenter(12, w, h))
            val pDone = playerTokens.count { it == HOME }
            val cDone = cpuTokens.count { it == HOME }
            labelPaint.textSize = ch * 0.22f
            val native = drawContext.canvas.nativeCanvas
            // Labels sit at the top of the strip; tokens are centered below them.
            native.drawText("YOU · $pDone/2 home", 1.5f * cw, 4.3f * ch, labelPaint)
            native.drawText("CPU · $cDone/2 home", 4.5f * cw, 4.3f * ch, labelPaint)

            fun drawToken(center: Offset, color: Color, glow: Boolean, parked: Boolean) {
                val radius = if (parked) cw * 0.18f else cw * 0.3f
                if (glow) drawCircle(color.copy(alpha = 0.35f), cw * 0.44f, center)
                drawCircle(color, radius, center)
                drawCircle(Color.White, radius, center, style = Stroke(cw * 0.05f))
            }
            fun tokenSpot(player: Int, idx: Int): Offset? {
                val p = (if (player == PLAYER) playerTokens else cpuTokens)[idx]
                return when {
                    p == BASE || p == HOME -> baseCenter(player, idx, w, h)
                    else -> trackCenter(p, w, h)
                }
            }
            for (idx in 0..1) {
                val pSpot = tokenSpot(PLAYER, idx) ?: continue
                drawToken(
                    pSpot, AccentBlue,
                    glow = turn == PLAYER && dice != null && idx in movables,
                    parked = playerTokens[idx] == HOME,
                )
                val cSpot = tokenSpot(CPU, idx) ?: continue
                drawToken(cSpot, AccentRed, glow = false, parked = cpuTokens[idx] == HOME)
            }
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = ::playerRoll,
                enabled = turn == PLAYER && dice == null && winner == null,
            ) { Text("Roll") }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
        }
        Text(
            "Roll a 6 to leave base · green squares are safe · exact roll to finish",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
