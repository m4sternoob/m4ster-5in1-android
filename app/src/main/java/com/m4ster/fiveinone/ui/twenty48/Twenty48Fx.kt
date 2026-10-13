package com.m4ster.fiveinone.ui.twenty48

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* Per-tile animation handles. Created once per tile identity. */
class TileFx(initialScale: Float) {
    val scale: Animatable<Float, AnimationVector1D> = Animatable(initialScale)
    val flash: Animatable<Float, AnimationVector1D> = Animatable(0f)
}

/* A "+N" that drifts up from a merge cell and fades out. */
data class FloatUp(val key: Long, val cell: Int, val value: Int, val big: Boolean)

@Composable
fun FloatUpItem(
    f: FloatUp,
    cellOriginPx: (Int) -> IntOffset,
    tilePx: Float,
    onDone: () -> Unit,
) {
    val rise = remember(f.key) { Animatable(0f) }
    val alpha = remember(f.key) { Animatable(1f) }
    LaunchedEffect(f.key) {
        rise.animateTo(-tilePx * 0.9f, androidx.compose.animation.core.tween(750))
    }
    LaunchedEffect(f.key) {
        alpha.animateTo(0f, androidx.compose.animation.core.tween(750))
    }
    LaunchedEffect(f.key) {
        delay(780)
        onDone()
    }
    val base = cellOriginPx(f.cell)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                IntOffset(
                    (base.x + tilePx / 2).toInt(),
                    (base.y + rise.value).toInt(),
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = "+${f.value}",
            fontSize = if (f.big) 26.sp else 20.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFFE9A3).copy(alpha = alpha.value),
        )
    }
}

/* ---- Particles: titan-merge bursts ---- */

private data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var rot: Float,
    var vrot: Float,
    val color: Color,
)

@Composable
fun ParticleBurst(
    key: Long,
    centerPx: Offset,
    colors: List<Color>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parts = remember(key) {
        List(26) {
            val a = Random.nextFloat() * (2 * Math.PI).toFloat()
            val sp = 260f + Random.nextFloat() * 560f
            Particle(
                x = centerPx.x, y = centerPx.y,
                vx = cos(a) * sp, vy = sin(a) * sp - 240f,
                size = 7f + Random.nextFloat() * 11f,
                rot = Random.nextFloat() * 360f,
                vrot = -420f + Random.nextFloat() * 840f,
                color = colors.random(),
            )
        }
    }
    var t by remember(key) { mutableStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (true) {
            val el = (withFrameNanos { it } - start) / 1_000_000f
            if (el >= 800f) break
            t = el / 800f
            val dt = 1f / 60f
            parts.forEach { p ->
                p.vy += 1500f * dt
                p.vx *= (1f - 1.6f * dt)
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.vrot * dt
            }
        }
        onDone()
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        parts.forEach { p ->
            rotate(p.rot, Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = Offset(p.x - p.size / 2f, p.y - p.size / 4f),
                    size = Size(p.size, p.size / 2f),
                )
            }
        }
    }
}

/* ---- Confetti: full-screen celebration rain ---- */

private data class Confetto(
    var x: Float,
    var y: Float,
    var vy: Float,
    var swayPhase: Float,
    var swayAmp: Float,
    var size: Float,
    var rot: Float,
    var vrot: Float,
    val color: Color,
)

@Composable
fun ConfettiOverlay(
    key: Long,
    widthPx: Float,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = listOf(
        Color(0xFFFFE9A3), Color(0xFF4ADE80), Color(0xFF22D3EE),
        Color(0xFFE879F9), Color(0xFFF2C14E), Color(0xFFFFFFFF),
    )
    val bits = remember(key) {
        List(90) {
            Confetto(
                x = Random.nextFloat() * widthPx,
                y = -40f - Random.nextFloat() * 400f,
                vy = 320f + Random.nextFloat() * 420f,
                swayPhase = Random.nextFloat() * (2 * Math.PI).toFloat(),
                swayAmp = 24f + Random.nextFloat() * 48f,
                size = 8f + Random.nextFloat() * 10f,
                rot = Random.nextFloat() * 360f,
                vrot = -360f + Random.nextFloat() * 720f,
                color = palette.random(),
            )
        }
    }
    var t by remember(key) { mutableStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        while (true) {
            val el = (withFrameNanos { it } - start) / 1_000_000f
            if (el >= 2400f) break
            t = el / 1000f
            val dt = 1f / 60f
            bits.forEach { b ->
                b.y += b.vy * dt
                b.rot += b.vrot * dt
            }
        }
        onDone()
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val fade = (1f - (t - 1.8f) / 0.6f).coerceIn(0f, 1f)
        bits.forEach { b ->
            val x = b.x + sin(b.swayPhase + t * 3f) * b.swayAmp
            rotate(b.rot, Offset(x, b.y)) {
                drawRect(
                    color = b.color.copy(alpha = fade),
                    topLeft = Offset(x - b.size / 2f, b.y - b.size / 4f),
                    size = Size(b.size, b.size / 2f),
                )
            }
        }
    }
}
