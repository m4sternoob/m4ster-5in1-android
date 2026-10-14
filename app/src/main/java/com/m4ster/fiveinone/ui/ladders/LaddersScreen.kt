package com.m4ster.fiveinone.ui.ladders

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.components.GameScaffold
import com.m4ster.fiveinone.ui.theme.AccentAmber
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/* Snakes & Ladders: 1 player vs CPU, or 2–4 players pass-and-play.
   10×10 boustrophedon board. Exact roll needed to finish. CPU moves on a
   short delay. GAME RULES ARE UNTOUCHED — this file is visuals + animation:
   hand-drawn Canvas snakes/ladders (see LaddersArt.kt), hopping tokens,
   portal slides, dice spin, and confetti bursts. System back is handled by
   MainActivity (game -> Home, Home -> exit confirm); this screen adds no
   BackHandler so the two never compete. */

private val Portals = mapOf(
    // ladders
    4 to 25, 13 to 46, 33 to 49, 42 to 63, 50 to 69, 62 to 81, 74 to 92,
    // snakes
    27 to 5, 40 to 3, 43 to 18, 54 to 34, 66 to 45, 76 to 58, 89 to 53, 99 to 41,
)
private val LadderFeet = setOf(4, 13, 33, 42, 50, 62, 74)

private enum class Mode { VsCpu, PassAndPlay }

private val TokenColors = listOf(AccentBlue, AccentRed, AccentGreen, AccentAmber)

/** Triple(landedSquare, finalSquare, portalKind: "ladder"/"snake"/null). */
private fun resolveMove(from: Int, roll: Int): Triple<Int, Int, String?> {
    val landed = from + roll
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
    val haptics = LocalHapticFeedback.current
    var mode by remember { mutableStateOf<Mode?>(null) }
    var playerCount by remember { mutableStateOf(2) }
    var positions by remember { mutableStateOf(listOf(0, 0)) } // 0 = off the board
    var turn by remember { mutableStateOf(0) }
    var dice by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf("Race to 100!") }
    var winner by remember { mutableStateOf<Int?>(null) }
    var cpuTrigger by remember { mutableStateOf(0) }
    var gameId by remember { mutableStateOf(0) }
    var animating by remember { mutableStateOf(false) }
    var animPositions by remember { mutableStateOf<List<Int>?>(null) }
    var portalFlash by remember { mutableStateOf<Int?>(null) }
    var tally by remember { mutableStateOf(listOf(0, 0)) }
    var celebrating by remember { mutableStateOf(false) }
    // --- animation-only state (never affects rules) ---
    var diceSpinTarget by remember { mutableStateOf(0f) }
    val diceSpin by animateFloatAsState(diceSpinTarget, tween(90), label = "diceSpin")
    var hopT by remember { mutableStateOf(0f) }
    var slide by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    var slideT by remember { mutableStateOf(0f) }
    var burstId by remember { mutableStateOf(0) }
    var burstOrigin by remember { mutableStateOf(Offset(0.5f, 0.3f)) }
    var boardPx by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val names = when (mode) {
        Mode.VsCpu -> listOf("You", "CPU")
        Mode.PassAndPlay -> (1..playerCount).map { "Player $it" }
        null -> listOf("Player 1", "Player 2")
    }
    // VsCpu is always 2 sides; pass-and-play uses the picked count.
    // A fun (not a val): mode/playerCount are read synchronously, so this
    // is always fresh — even inside reset() right after picking a mode.
    fun sideCount() = if (mode == Mode.VsCpu) 2 else playerCount

    fun reset() {
        positions = List(sideCount()) { 0 }
        tally = List(sideCount()) { 0 }
        turn = 0
        dice = null
        winner = null
        animating = false
        animPositions = null
        portalFlash = null
        slide = null
        slideT = 0f
        diceSpinTarget = 0f
        burstId = 0
        message = "Race to 100 — tap Roll!"
        gameId++ // cancels any in-flight CPU turn or token animation
    }

    fun pickVsCpu() {
        playerCount = 2
        mode = Mode.VsCpu
        reset()
    }

    fun pickPassAndPlay(n: Int) {
        playerCount = n
        mode = Mode.PassAndPlay
        reset()
    }

    // System back is owned by MainActivity (single BackHandler there):
    // from a game it steps back to Home, on Home it asks before exiting.
    // LaddersScreen deliberately adds no BackHandler so the two never fight.

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

    /** One full turn with juice: the dice tumbles with a spin, the token hops
        square by square, portals slide along their art. gameId aborts a stale
        turn on reset. Rules identical to before — only the motion changed. */
    suspend fun animatedTurn(who: Int, roll: Int) {
        val id = gameId
        animating = true
        diceSpinTarget = 0f
        repeat(6) {
            dice = (1..6).random()
            diceSpinTarget = (it + 1) * 60f
            delay(70)
        }
        if (id != gameId) return
        dice = roll
        delay(300)
        if (id != gameId) return
        diceSpinTarget = 0f
        val from = positions[who]
        val (landed, final, portal) = resolveMove(from, roll)
        var cur = from
        val path = if (from == 0) listOf(landed) else ((from + 1)..landed).toList()
        for (sq in path) {
            cur = sq
            animPositions = positions.toMutableList().also { it[who] = cur }
            delay(130)
            if (id != gameId) return
        }
        if (portal != null && final != landed) {
            // Ride the portal art instead of teleporting.
            portalFlash = who
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (portal == "ladder" && boardPx > 0f) {
                val c = cellCenter(final, boardPx)
                burstOrigin = Offset(c.x / boardPx, c.y / boardPx)
                burstId++
            }
            slide = Triple(landed, final, portal)
            val durMs = if (portal == "snake") 750L else 600L
            val steps = 24
            repeat(steps) { i ->
                slideT = (i + 1) / steps.toFloat()
                delay(durMs / steps)
                if (id != gameId) return
            }
            slide = null
            cur = final
            animPositions = positions.toMutableList().also { it[who] = cur }
            portalFlash = null
            delay(200)
            if (id != gameId) return
        }
        positions = positions.toMutableList().also { it[who] = cur }
        animPositions = null
        if (final == 100) {
            winner = who
            tally = tally.toMutableList().also { it[who]++ }
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val verb = if (names[who] == "You") "win" else "wins"
            message = "${names[who]} rolled $roll and reached 100 — ${names[who]} $verb!"
        } else if (roll == 6) {
            // Classic rule: a 6 earns another roll. Turn stays with the
            // same player; the CPU re-triggers itself in VsCpu mode.
            message = moveMessage(names[who], from, roll) + " — rolled a 6, roll again!"
            if (mode == Mode.VsCpu && who == 1) cpuTrigger++
        } else {
            message = moveMessage(names[who], from, roll)
            turn = (who + 1) % sideCount()
            if (mode == Mode.VsCpu && turn == 1) cpuTrigger++
        }
        animating = false
    }

    fun humanRoll() {
        if (mode == null || winner != null || animating) return
        // In VsCpu mode the CPU (player 2) rolls itself.
        if (mode == Mode.VsCpu && turn == 1) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { animatedTurn(turn, (1..6).random()) }
    }

    // CPU acts on its own beat. gameId in the key cancels a stale turn on reset.
    LaunchedEffect(cpuTrigger, gameId) {
        val id = gameId
        if (mode != Mode.VsCpu || cpuTrigger == 0 || turn != 1) return@LaunchedEffect
        delay(800)
        if (id != gameId) return@LaunchedEffect
        animatedTurn(1, (1..6).random())
    }

    // Hop clock: every time the animated token advances a square, sweep
    // hopT 0→1 over the step so the token arcs instead of teleporting.
    LaunchedEffect(animPositions) {
        if (animPositions == null || slide != null) {
            hopT = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - start) / 130_000_000f).coerceIn(0f, 1f)
            hopT = t
            if (t >= 1f) break
        }
    }

    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.parseColor("#8FA0BF")
            textAlign = Paint.Align.LEFT
            isAntiAlias = true
        }
    }

    // Mode picker: shown before the first game and when switching modes.
    if (mode == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101B31), Color(0xFF070C16)),
                    ),
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            Text(
                "Snakes & Ladders",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFBBF24),
            )
            Text(
                "Race to 100. Climb the wooden ladders, dodge the hungry snakes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
            )
            Button(onClick = ::pickVsCpu, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("1 Player (vs CPU)")
            }
            Button(onClick = { pickPassAndPlay(2) }, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("2 Players")
            }
            Button(onClick = { pickPassAndPlay(3) }, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("3 Players")
            }
            Button(onClick = { pickPassAndPlay(4) }, modifier = Modifier.fillMaxWidth(0.7f)) {
                Text("4 Players")
            }
        }
        return
    }

    val shown = animPositions ?: positions
    val movingIdx = animPositions?.let { ap ->
        ap.indices.firstOrNull { ap[it] != positions[it] }
    }
    val winText = when {
        mode == Mode.VsCpu && winner == 0 -> "You win!"
        mode == Mode.VsCpu && winner != null -> "CPU wins!"
        winner != null -> "${names[winner!!]} wins!"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101B31), Color(0xFF070C16)),
                ),
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Player pills: color dot, name, tally — the current turn glows.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.forEachIndexed { i, n ->
                val active = i == turn && winner == null
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) TokenColors[i].copy(alpha = 0.20f)
                            else Color(0xFF111A2E),
                            RoundedCornerShape(12.dp),
                        )
                        .border(
                            if (active) 1.5.dp else 0.dp,
                            TokenColors[i].copy(alpha = 0.85f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(TokenColors[i], size.minDimension / 2f)
                    }
                    Text(
                        "$n · ${tally[i]}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
        // Turn label + tumbling dice.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val turnLabel = when (mode) {
                Mode.VsCpu -> if (turn == 0) "Your turn" else "CPU's turn"
                else -> "${names[turn]}'s turn"
            }
            Text(
                turnLabel,
                style = MaterialTheme.typography.titleMedium,
                color = TokenColors[turn],
            )
            Text(
                dice?.let { DiceFaces[it - 1] } ?: "🎲",
                fontSize = 38.sp,
                modifier = Modifier.graphicsLayer {
                    rotationZ = diceSpin
                    val pulse = 1f + 0.22f * sin(diceSpin * PI / 180f).toFloat()
                    scaleX = pulse
                    scaleY = pulse
                },
            )
        }
        // Board + confetti burst overlay.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val density = LocalDensity.current
            LaunchedEffect(maxWidth) {
                with(density) { boardPx = maxWidth.toPx() }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boardSize = size.width
                val cell = boardSize / 10f
                drawBoardBackdrop()
                drawTiles(boardSize)
                // Ladders under snakes.
                for ((from, to) in Portals) {
                    if (from in LadderFeet) drawLadder(from, to, boardSize)
                }
                for ((from, to) in Portals) {
                    if (from in LadderFeet) drawLadderFootMarker(from, boardSize)
                }
                Portals.keys
                    .filter { it !in LadderFeet }
                    .sorted()
                    .forEachIndexed { idx, from ->
                        drawSnake(from, Portals[from]!!, boardSize, idx)
                    }
                drawBoardFrame(boardSize)
                // Square numbers, top-left of each tile.
                labelPaint.textSize = cell * 0.24f
                val native = drawContext.canvas.nativeCanvas
                for (n in 1..100) {
                    val c = cellCenter(n, boardSize)
                    native.drawText(
                        n.toString(),
                        c.x - cell * 0.40f,
                        c.y - cell * 0.22f,
                        labelPaint,
                    )
                }
                // Tokens, fanned out when several share a square.
                val bySquare = shown.mapIndexed { i, pos -> i to pos }
                    .filter { it.second != 0 }
                    .groupBy { it.second }
                shown.forEachIndexed { i, pos ->
                    if (pos == 0) return@forEachIndexed
                    // The sliding token is drawn at the slide point instead.
                    if (slide != null && i == movingIdx) return@forEachIndexed
                    val c = cellCenter(pos, boardSize)
                    val mates = bySquare[pos] ?: emptyList()
                    val slot = mates.indexOfFirst { it.first == i }
                    val spread = if (mates.size > 1)
                        (slot - (mates.size - 1) / 2f) * cell * 0.34f else 0f
                    var center = Offset(c.x + spread, c.y)
                    var scale = 1f
                    if (i == movingIdx && animPositions != null) {
                        val hop = sin(PI * hopT).toFloat()
                        center = center.copy(y = center.y - hop * cell * 0.38f)
                        scale = 1f + 0.18f * hop
                    }
                    drawToken(center, TokenColors[i], cell, scale, ring = portalFlash == i)
                }
                // Portal slide: token rides the snake curve / ladder line.
                slide?.let { (f, t, kind) ->
                    val p = if (kind == "snake") {
                        snakePoint(f, t, smoothstep(slideT), boardSize)
                    } else {
                        ladderPoint(f, t, slideT, boardSize)
                    }
                    movingIdx?.let { drawToken(p, TokenColors[it], cell, 1.12f) }
                }
            }
            if (burstId > 0) {
                LadderBurst(
                    burstId = burstId,
                    origin = burstOrigin,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val posLabel = { i: Int -> if (positions[i] == 0) "start" else "${positions[i]}" }
        Text(
            names.mapIndexed { i, n -> "$n: ${posLabel(i)}" }.joinToString("   "),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF94A3B8),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = ::humanRoll,
                enabled = winner == null && !animating && !(mode == Mode.VsCpu && turn == 1),
            ) { Text("Roll") }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
            OutlinedButton(onClick = { gameId++; mode = null }) { Text("Mode") }
        }
        if (winner != null) {
            Text(
                winText,
                style = MaterialTheme.typography.headlineSmall,
                color = TokenColors[winner!!],
            )
        }
        if (celebrating) {
            Dialog(
                onDismissRequest = { celebrating = false },
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Celebration { celebrating = false }
                    if (winner != null) {
                        Text(
                            winText,
                            style = MaterialTheme.typography.headlineLarge,
                            color = TokenColors[winner!!],
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}
