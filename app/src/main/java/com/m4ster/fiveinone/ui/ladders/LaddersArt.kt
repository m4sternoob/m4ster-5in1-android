package com.m4ster.fiveinone.ui.ladders

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.StrokeCap
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* Hand-drawn board art for Snakes & Ladders. Everything here is programmatic
   Canvas — zero image assets, so it stays crisp at any density and costs
   almost nothing to draw (a few hundred primitives per frame, only while
   something animates). Game logic lives in LaddersScreen.kt; this file is
   pure visuals. */

/** Center of square n (1..100) on a size×size board. Row 1 is the bottom. */
fun cellCenter(n: Int, size: Float): Offset {
    val cell = size / 10f
    val rowFromBottom = (n - 1) / 10
    val posInRow = (n - 1) % 10
    val col = if (rowFromBottom % 2 == 0) posInRow else 9 - posInRow
    val rowFromTop = 9 - rowFromBottom
    return Offset((col + 0.5f) * cell, (rowFromTop + 0.5f) * cell)
}

private fun Offset.norm(): Offset {
    val l = getDistance()
    return if (l > 0f) this / l else Offset.Zero
}

/** Smoothstep easing for slides: slow start, fast middle, soft landing. */
fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

/**
 * Point at t (0..1) along a snake's S-curve, head square -> tail square.
 * A cubic Bezier with the middle control points pushed to opposite sides
 * of the travel direction gives the classic sidewinder wiggle. The same
 * function draws the snake AND animates the token sliding down it, so the
 * token always rides the visible body.
 */
fun snakePoint(fromSq: Int, toSq: Int, t: Float, boardSize: Float): Offset {
    val a = cellCenter(fromSq, boardSize)
    val b = cellCenter(toSq, boardSize)
    val delta = b - a
    val len = delta.getDistance().coerceAtLeast(1f)
    val dir = delta / len
    val n = Offset(-dir.y, dir.x)
    val cell = boardSize / 10f
    val wiggle = (len * 0.20f).coerceIn(cell * 0.4f, cell * 1.6f)
    val p0 = a
    val p1 = a + dir * (len * 0.30f) + n * wiggle
    val p2 = a + dir * (len * 0.70f) - n * wiggle
    val p3 = b
    val u = 1f - t
    return p0 * (u * u * u) + p1 * (3f * u * u * t) +
        p2 * (3f * u * t * t) + p3 * (t * t * t)
}

/** Point at t (0..1) climbing a ladder, with a small rung bounce. */
fun ladderPoint(fromSq: Int, toSq: Int, t: Float, boardSize: Float): Offset {
    val a = cellCenter(fromSq, boardSize)
    val b = cellCenter(toSq, boardSize)
    val p = a + (b - a) * t
    val n = Offset(-(b - a).y, (b - a).x).norm()
    return p + n * (sin(t * PI).toFloat() * boardSize / 10f * 0.10f)
}

/** One snake look per portal: body color + darker pattern color. */
private val SnakeSkins = listOf(
    Color(0xFF10B981) to Color(0xFF065F46), // emerald
    Color(0xFF8B5CF6) to Color(0xFF4C1D95), // violet
    Color(0xFFFB923C) to Color(0xFF9A3412), // orange
    Color(0xFFFB7185) to Color(0xFF9F1239), // rose
    Color(0xFF22D3EE) to Color(0xFF155E75), // cyan
    Color(0xFFA3E635) to Color(0xFF3F6212), // lime
    Color(0xFFF59E0B) to Color(0xFF92400E), // amber
    Color(0xFF38BDF8) to Color(0xFF0C4A6E), // sky
)

private val WoodLight = Color(0xFFD29A5B)
private val WoodDark = Color(0xFF8A5A2B)
private val TileA = Color(0xFF1D2B4E)
private val TileB = Color(0xFF141F38)
private val FrameGold = Color(0xFFFBBF24)

/**
 * A thick, juicy snake: tapered S-curve body with belly highlight and dark
 * bands, drop shadow, and a head with eyes + forked tongue. Head sits on
 * the square you land on (fromSq); tail on the destination (toSq).
 */
fun DrawScope.drawSnake(fromSq: Int, toSq: Int, boardSize: Float, index: Int) {
    val cell = boardSize / 10f
    val (body, dark) = SnakeSkins[index % SnakeSkins.size]
    val segs = 26
    val pts = List(segs + 1) { i -> snakePoint(fromSq, toSq, i / segs.toFloat(), boardSize) }
    val headW = cell * 0.34f
    val tailW = cell * 0.10f
    val shadow = Offset(4f, 6f)
    val widthAt = { i: Int -> lerp(headW, tailW, i / segs.toFloat()) }

    // Soft drop shadow so the snake sits above the board.
    for (i in 0 until segs) {
        drawLine(
            Color.Black.copy(alpha = 0.30f),
            pts[i] + shadow, pts[i + 1] + shadow,
            widthAt(i), StrokeCap.Round,
        )
    }
    // Tapered body: wide at the head, thin at the tail.
    for (i in 0 until segs) {
        drawLine(body, pts[i], pts[i + 1], widthAt(i), StrokeCap.Round)
    }
    // Belly highlight down the middle.
    for (i in 0 until segs) {
        drawLine(
            Color.White.copy(alpha = 0.20f),
            pts[i], pts[i + 1], widthAt(i) * 0.42f, StrokeCap.Round,
        )
    }
    // Dark bands every few segments — each snake's pattern.
    for (i in 2 until segs step 4) {
        drawLine(
            dark.copy(alpha = 0.55f),
            pts[i], pts[i + 1], widthAt(i) * 1.04f, StrokeCap.Round,
        )
    }
    // Head: faces away from the body, toward whoever just landed on it.
    val head = pts[0]
    val face = (pts[0] - pts[2]).norm()
    val side = Offset(-face.y, face.x)
    val hr = headW * 0.85f
    drawCircle(Color.Black.copy(alpha = 0.30f), hr, head + shadow)
    drawCircle(body, hr, head)
    drawCircle(dark.copy(alpha = 0.45f), hr, head, style = Stroke(hr * 0.16f))
    // Eyes look toward the victim.
    val eyeBase = head + face * hr * 0.34f
    for (s in listOf(1f, -1f)) {
        val e = eyeBase + side * s * hr * 0.40f
        drawCircle(Color.White, hr * 0.30f, e)
        drawCircle(Color(0xFF0B1220), hr * 0.15f, e + face * hr * 0.09f)
    }
    // Forked tongue flick.
    val tip = head + face * hr * 1.02f
    val stem = tip + face * hr * 0.42f
    val fork = side * hr * 0.30f
    val tongue = Color(0xFFEF4444)
    drawLine(tongue, tip, stem, hr * 0.13f, StrokeCap.Round)
    drawLine(tongue, stem, stem + face * hr * 0.28f + fork, hr * 0.11f, StrokeCap.Round)
    drawLine(tongue, stem, stem + face * hr * 0.28f - fork, hr * 0.11f, StrokeCap.Round)
    // Tail tip.
    drawCircle(body, tailW * 0.75f, pts[segs])
}

/** Wooden ladder: two rails with highlight, rungs, and a drop shadow. */
fun DrawScope.drawLadder(fromSq: Int, toSq: Int, boardSize: Float) {
    val cell = boardSize / 10f
    val a = cellCenter(fromSq, boardSize)
    val b = cellCenter(toSq, boardSize)
    val delta = b - a
    val len = delta.getDistance().coerceAtLeast(1f)
    val dir = delta / len
    val n = Offset(-dir.y, dir.x)
    val railOff = cell * 0.17f
    val railW = cell * 0.10f
    val shadow = Offset(4f, 6f)
    // Shadows.
    drawLine(Color.Black.copy(alpha = 0.30f), a + n * railOff + shadow, b + n * railOff + shadow, railW, StrokeCap.Round)
    drawLine(Color.Black.copy(alpha = 0.30f), a - n * railOff + shadow, b - n * railOff + shadow, railW, StrokeCap.Round)
    // Rails: dark base + light core = rounded wooden poles.
    drawLine(WoodDark, a + n * railOff, b + n * railOff, railW, StrokeCap.Round)
    drawLine(WoodDark, a - n * railOff, b - n * railOff, railW, StrokeCap.Round)
    drawLine(WoodLight, a + n * railOff, b + n * railOff, railW * 0.55f, StrokeCap.Round)
    drawLine(WoodLight, a - n * railOff, b - n * railOff, railW * 0.55f, StrokeCap.Round)
    // Rungs.
    val rungs = (len / (cell * 0.5f)).toInt().coerceAtLeast(2)
    for (i in 1..rungs) {
        val c = a + delta * (i / (rungs + 1f))
        drawLine(WoodDark, c + n * railOff, c - n * railOff, cell * 0.075f, StrokeCap.Round)
        drawLine(WoodLight, c + n * railOff, c - n * railOff, cell * 0.035f, StrokeCap.Round)
    }
}

/** Board backdrop: deep vertical gradient. Draw first, full canvas. */
fun DrawScope.drawBoardBackdrop() {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF101B31), Color(0xFF070C16)),
        ),
        size = size,
    )
}

/** 100 rounded tiles with a subtle top light; 2px gaps read as the grid. */
fun DrawScope.drawTiles(boardSize: Float) {
    val cell = boardSize / 10f
    val radius = CornerRadius(cell * 0.14f, cell * 0.14f)
    for (n in 1..100) {
        val c = cellCenter(n, boardSize)
        val rowFromBottom = (n - 1) / 10
        val base = if ((rowFromBottom + n) % 2 == 0) TileA else TileB
        val tl = Offset(c.x - cell / 2f + 1.5f, c.y - cell / 2f + 1.5f)
        val sz = Size(cell - 3f, cell - 3f)
        drawRoundRect(base, tl, sz, radius)
        // Faint light from the top edge — cheap depth.
        drawRoundRect(
            Color.White.copy(alpha = 0.045f), tl,
            Size(sz.width, sz.height * 0.5f), radius,
        )
    }
}

/** Thin gold frame inset from the board edge. */
fun DrawScope.drawBoardFrame(boardSize: Float) {
    val inset = boardSize * 0.012f
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                FrameGold.copy(alpha = 0.55f),
                FrameGold.copy(alpha = 0.10f),
            ),
        ),
        topLeft = Offset(inset, inset),
        size = Size(boardSize - inset * 2f, boardSize - inset * 2f),
        cornerRadius = CornerRadius(boardSize * 0.03f, boardSize * 0.03f),
        style = Stroke(width = boardSize * 0.008f),
    )
}

/** Gold dot on ladder feet so climbs are easy to spot. */
fun DrawScope.drawLadderFootMarker(square: Int, boardSize: Float) {
    val cell = boardSize / 10f
    val c = cellCenter(square, boardSize)
    drawCircle(
        FrameGold.copy(alpha = 0.30f),
        cell * 0.30f,
        c + Offset(0f, cell * 0.30f),
    )
}

/** Game token: glossy disc with shadow and white ring. */
fun DrawScope.drawToken(
    center: Offset,
    color: Color,
    cell: Float,
    scale: Float = 1f,
    ring: Boolean = false,
) {
    val r = cell * 0.26f * scale
    drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(2f, 4f))
    drawCircle(color, r, center)
    // Glossy highlight, top-left.
    drawCircle(
        Color.White.copy(alpha = 0.38f),
        r * 0.42f,
        center + Offset(-r * 0.32f, -r * 0.34f),
    )
    drawCircle(Color.White, r, center, style = Stroke(cell * 0.06f))
    if (ring) {
        drawCircle(Color.White, r * 1.55f, center, style = Stroke(cell * 0.09f))
    }
}

private data class BurstPart(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var color: Color, var life: Float,
)

private val BurstColors = listOf(
    Color(0xFFFBBF24), Color(0xFF4ADE80), Color(0xFFFFFFFF), Color(0xFF60A5FA),
)

/**
 * Short confetti burst for ladder climbs. Origin is in 0..1 fractions of the
 * overlay box. ~26 bits, up-biased cone, gravity, ~1s, then gone.
 * Kept local to ladders so the shared Celebration stays untouched.
 */
@Composable
fun LadderBurst(
    burstId: Int,
    origin: Offset,
    modifier: Modifier = Modifier,
) {
    var parts by remember(burstId) { mutableStateOf(emptyList<BurstPart>()) }

    LaunchedEffect(burstId) {
        if (burstId == 0) return@LaunchedEffect
        val rnd = Random(burstId * 7919L + System.currentTimeMillis())
        parts = List(26) {
            val ang = -PI / 2 + (rnd.nextFloat() - 0.5f) * 1.6
            val sp = 0.55f + rnd.nextFloat() * 0.75f
            BurstPart(
                x = origin.x, y = origin.y,
                vx = (cos(ang) * sp).toFloat(),
                vy = (sin(ang) * sp).toFloat(),
                color = BurstColors[rnd.nextInt(BurstColors.size)],
                life = 1f,
            )
        }
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            val elapsed = (now - start) / 1e9f
            parts = parts.map { p ->
                p.apply {
                    vy += 2.2f * dt
                    x += vx * dt
                    y += vy * dt
                    life = (1f - elapsed / 0.95f).coerceAtLeast(0f)
                }
            }.filter { it.life > 0f && it.y < 1.2f }
            if (parts.isEmpty() || elapsed > 1.2f) break
        }
        parts = emptyList()
    }

    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.014f
        parts.forEach { p ->
            drawCircle(
                p.color.copy(alpha = p.life),
                r * (0.6f + 0.4f * p.life),
                Offset(p.x * size.width, p.y * size.height),
            )
        }
    }
}
