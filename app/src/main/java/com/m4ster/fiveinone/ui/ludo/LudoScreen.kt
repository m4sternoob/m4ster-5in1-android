package com.m4ster.fiveinone.ui.ludo

import android.graphics.Paint
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import com.m4ster.fiveinone.ui.theme.BoardAlt
import com.m4ster.fiveinone.ui.theme.BoardDark
import com.m4ster.fiveinone.ui.theme.GridLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/* Simplified Ludo: you vs CPU, or 2 players pass-and-play. 24-cell track
   (6 cols × 4 rows, boustrophedon) plus a base strip. Two tokens each;
   roll a 6 to leave base; landing on an enemy token off a safe square
   captures it; exact roll to finish (cell 24). Roll a 6 for an extra turn.
   First to bring both tokens home wins.
   Juice: the dice tumbles before settling, tokens glide to their new cell,
   captures flash. */

private const val TRACK = 24
private const val BASE = -1
private const val HOME = 24

private enum class LudoMode { VsCpu, TwoPlayer }

private fun startOf(side: Int) = if (side == 0) 0 else 12
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

/** Center of a base slot: row 4; side 0 uses cols 1–2, side 1 cols 4–5.
 *  Slightly below strip center so the top labels don't overlap tokens. */
private fun baseCenter(side: Int, slot: Int, w: Float, h: Float): Offset {
    val cw = w / 6f
    val ch = h / 5f
    val col = if (side == 0) 1 + slot else 4 + slot
    return Offset((col + 0.5f) * cw, 4.62f * ch)
}

/** Pixel center of a token's cell (track, base, or home slot). */
private fun spotCenter(side: Int, idx: Int, cell: Int, w: Float, h: Float): Offset =
    if (cell == BASE || cell == HOME) baseCenter(side, idx, w, h)
    else trackCenter(cell, w, h)

private val DiceFaces = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

@Composable
fun LudoScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var mode by remember { mutableStateOf(LudoMode.VsCpu) }
    var sideA by remember { mutableStateOf(intArrayOf(BASE, BASE)) }
    var sideB by remember { mutableStateOf(intArrayOf(BASE, BASE)) }
    var turn by remember { mutableStateOf(0) }
    var dice by remember { mutableStateOf<Int?>(null) }
    var movables by remember { mutableStateOf(emptyList<Int>()) }
    var message by remember { mutableStateOf("Your turn — tap Roll.") }
    var winner by remember { mutableStateOf<Int?>(null) }
    var cpuTrigger by remember { mutableStateOf(0) }
    var gameId by remember { mutableStateOf(0) }
    var boardPx by remember { mutableStateOf(IntSize.Zero) }
    var tally by remember { mutableStateOf(listOf(0, 0)) }
    // Token glide: (side, token idx, from cell) + 0→1 progress.
    var glide by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var glideT by remember { mutableStateOf(1f) }
    // Capture flash: (side, track cell) + 0→1 progress.
    var captureFx by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var captureT by remember { mutableStateOf(0f) }
    var celebrating by remember { mutableStateOf(false) }

    fun tokens(s: Int) = if (s == 0) sideA else sideB
    fun name(s: Int) = if (mode == LudoMode.VsCpu) (if (s == 0) "You" else "CPU")
        else "Player ${s + 1}"

    LaunchedEffect(glide) {
        if (glide == null) return@LaunchedEffect
        // animate() reports (value, velocity) per frame.
        animate(0f, 1f, animationSpec = tween(380)) { v, _ -> glideT = v }
        glide = null
    }
    LaunchedEffect(captureFx) {
        if (captureFx == null) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(500)) { v, _ -> captureT = v }
        captureFx = null
    }

    fun reset() {
        sideA = intArrayOf(BASE, BASE)
        sideB = intArrayOf(BASE, BASE)
        turn = 0
        dice = null
        movables = emptyList()
        winner = null
        glide = null
        captureFx = null
        message = if (mode == LudoMode.VsCpu) "Your turn — tap Roll." else "Player 1 — tap Roll."
        gameId++ // cancels any in-flight CPU turn
    }

    fun setMode(m: LudoMode) {
        mode = m
        reset()
    }

    fun movableFor(s: Int, roll: Int): List<Int> {
        val tk = tokens(s)
        return tk.indices.filter { t ->
            val p = tk[t]
            p != HOME && ((p == BASE && roll == 6) || (p >= 0 && p + roll <= HOME))
        }
    }

    fun hasWon(s: Int) = tokens(s).all { it == HOME }

    fun wouldCapture(s: Int, token: Int, roll: Int): Boolean {
        val mine = tokens(s)
        val theirs = tokens(1 - s)
        val from = mine[token]
        val dest = if (from == BASE) startOf(s) else from + roll
        return dest != HOME && dest !in Safe && dest in theirs
    }

    /** Applies a move. Returns (extraTurn, captured). Always copies the
     *  token arrays so Compose sees a new value and recomposes. Kicks off
     *  the glide animation from the token's old cell. */
    fun doMove(s: Int, token: Int, roll: Int): Pair<Boolean, Boolean> {
        val mine = tokens(s).copyOf()
        val theirs = tokens(1 - s).copyOf()
        val fromCell = mine[token]
        val dest = if (fromCell == BASE) startOf(s) else fromCell + roll
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
        if (s == 0) {
            sideA = mine
            sideB = theirs
        } else {
            sideB = mine
            sideA = theirs
        }
        glideT = 0f
        glide = Triple(s, token, fromCell)
        return (roll == 6) to captured
    }

    /** Advance after a turn: extra roll keeps the same side going. */
    fun advanceTurn(s: Int, extra: Boolean) {
        dice = null
        if (!extra) {
            turn = 1 - s
            if (mode == LudoMode.VsCpu && turn == 1) cpuTrigger++
        } else if (mode == LudoMode.VsCpu && s == 1) {
            cpuTrigger++ // CPU chains its extra turn
        }
    }

    fun roll(s: Int) {
        if (turn != s || dice != null || winner != null) return
        if (mode == LudoMode.VsCpu && s == 1) return // CPU rolls itself
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val r = (1..6).random()
        val id = gameId
        scope.launch {
            repeat(6) {
                dice = (1..6).random()
                delay(70)
            }
            if (id != gameId) return@launch
            dice = r
            val moves = movableFor(s, r)
            movables = moves
            if (moves.isEmpty()) {
                message = "${name(s)} rolled $r — no moves possible."
                advanceTurn(s, false)
            } else {
                message = "${name(s)} rolled $r — tap a glowing token."
            }
        }
    }

    fun moveToken(s: Int, t: Int) {
        val r = dice ?: return
        if (turn != s || t !in movables || winner != null) return
        if (mode == LudoMode.VsCpu && s == 1) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val (extra, captured) = doMove(s, t, r)
        dice = null
        movables = emptyList()
        if (captured) captureFx = s to tokens(s)[t]
        if (hasWon(s)) {
            winner = s
            tally = tally.toMutableList().also { it[s]++ }
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            message = "${name(s)} brought both tokens home — ${name(s)} wins!"
            return
        }
        message = when {
            captured -> "${name(s)} captured a token!"
            extra -> "${name(s)} rolled a 6 — roll again!"
            else -> "${name(1 - s)}'s turn…"
        }
        advanceTurn(s, extra)
    }

    /** One CPU roll+move. Returns true if the CPU earned another turn. */
    suspend fun cpuRollOnce(id: Int): Boolean {
        repeat(6) {
            dice = (1..6).random()
            delay(70)
        }
        if (id != gameId) return false
        val r = (1..6).random()
        dice = r
        val moves = movableFor(1, r)
        var extra = false
        if (moves.isNotEmpty()) {
            // Prefer captures, otherwise any legal move.
            val pick = moves.firstOrNull { wouldCapture(1, it, r) } ?: moves.random()
            val (e, captured) = doMove(1, pick, r)
            extra = e
            if (captured) captureFx = 1 to tokens(1)[pick]
            message = if (captured) "CPU rolled $r and captured your token!" else "CPU rolled $r."
        } else {
            message = "CPU rolled $r — no moves."
        }
        if (hasWon(1)) {
            winner = 1
            tally = tally.toMutableList().also { it[1]++ }
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            message = "CPU brought both tokens home — CPU wins."
            dice = null
            return false
        }
        advanceTurn(1, extra)
        return extra
    }

    // CPU acts on its own beat, chaining extra turns. gameId cancels a stale
    // turn when the player hits Restart mid-CPU-turn.
    LaunchedEffect(cpuTrigger, gameId) {
        val id = gameId
        if (mode != LudoMode.VsCpu || cpuTrigger == 0 || turn != 1) return@LaunchedEffect
        delay(800)
        if (id != gameId) return@LaunchedEffect
        var again = cpuRollOnce(id)
        while (again && id == gameId && winner == null) {
            delay(800)
            again = cpuRollOnce(id)
        }
    }

    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    // In VsCpu the human is always side 0; in 2P the tapping side is `turn`.
    val tapSide = turn
    val canTap = dice != null && winner == null &&
        (mode == LudoMode.TwoPlayer || turn == 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                setMode(if (mode == LudoMode.VsCpu) LudoMode.TwoPlayer else LudoMode.VsCpu)
            }) {
                Text(if (mode == LudoMode.VsCpu) "vs CPU" else "2 Players")
            }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
        }
        Text(
            "${name(0)} ${tally[0]} · ${name(1)} ${tally[1]}",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    winner != null -> "${name(winner!!)} wins!"
                    mode == LudoMode.VsCpu && turn == 0 -> "Your turn"
                    else -> "${name(turn)}'s turn"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (winner == null) AccentGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(dice?.let { DiceFaces[it - 1] } ?: "🎲", fontSize = 36.sp)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(6f / 5f)
                .onSizeChanged { boardPx = it }
                .pointerInput(dice, movables, turn, winner, boardPx, sideA, sideB, mode) {
                    detectTapGestures { tap ->
                        if (!canTap) return@detectTapGestures
                        if (boardPx == IntSize.Zero) return@detectTapGestures
                        val w = boardPx.width.toFloat()
                        val h = boardPx.height.toFloat()
                        val cw = w / 6f
                        val ch = h / 5f
                        val tk = tokens(tapSide)
                        val hit = movables.firstOrNull { t ->
                            val p = tk[t]
                            val c = when {
                                p == BASE -> baseCenter(tapSide, t, w, h)
                                p in 0 until TRACK -> trackCenter(p, w, h)
                                else -> null // HOME tokens aren't tappable
                            } ?: return@firstOrNull false
                            abs(tap.x - c.x) <= cw * 0.5f && abs(tap.y - c.y) <= ch * 0.5f
                        }
                        if (hit != null) moveToken(tapSide, hit)
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
            val done0 = sideA.count { it == HOME }
            val done1 = sideB.count { it == HOME }
            labelPaint.textSize = ch * 0.22f
            val native = drawContext.canvas.nativeCanvas
            // Labels sit at the top of the strip; tokens are centered below them.
            native.drawText("${name(0).uppercase()} · $done0/2 home", 1.5f * cw, 4.3f * ch, labelPaint)
            native.drawText("${name(1).uppercase()} · $done1/2 home", 4.5f * cw, 4.3f * ch, labelPaint)

            // Capture flash: an expanding, fading ring on the capture cell.
            captureFx?.let { (_, cell) ->
                val c = trackCenter(cell, w, h)
                drawCircle(
                    Color.White.copy(alpha = 1f - captureT),
                    radius = cw * (0.32f + 0.4f * captureT),
                    center = c,
                    style = Stroke(cw * 0.07f),
                )
            }

            fun drawToken(center: Offset, color: Color, glow: Boolean, parked: Boolean) {
                val radius = if (parked) cw * 0.18f else cw * 0.3f
                if (glow) drawCircle(color.copy(alpha = 0.35f), cw * 0.44f, center)
                drawCircle(color, radius, center)
                drawCircle(Color.White, radius, center, style = Stroke(cw * 0.05f))
            }
            for (s in 0..1) {
                for (idx in 0..1) {
                    val tk = tokens(s)
                    val to = spotCenter(s, idx, tk[idx], w, h)
                    // Glide the moving token from its old cell to its new one.
                    val g = glide
                    val center = if (g != null && g.first == s && g.second == idx) {
                        lerp(spotCenter(s, idx, g.third, w, h), to, glideT)
                    } else to
                    drawToken(
                        center,
                        if (s == 0) AccentBlue else AccentRed,
                        glow = s == tapSide && dice != null && idx in movables && canTap,
                        parked = tk[idx] == HOME,
                    )
                }
            }
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { roll(turn) },
                enabled = dice == null && winner == null &&
                    (mode == LudoMode.TwoPlayer || turn == 0),
            ) { Text("Roll") }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
        }
        Text(
            "Roll a 6 to leave base · green squares are safe · exact roll to finish",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
