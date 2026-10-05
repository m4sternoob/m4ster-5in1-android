package com.m4ster.fiveinone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.m4ster.fiveinone.ui.theme.AccentAmber
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlin.random.Random

/* Confetti burst for wins: ~70 paper bits with random velocity, gravity,
   spin, and fade, drawn on a full-size Canvas. Runs ~2 seconds, then
   calls onDone so the host can dismiss it. Cheap: one Canvas, no images. */

private val PartyColors = listOf(AccentRed, AccentAmber, AccentGreen, AccentBlue, Color.White)

private data class Bit(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var rot: Float, var vr: Float,
    var color: Color, var life: Float,
)

@Composable
fun Celebration(
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    var bits by remember { mutableStateOf(listOf<Bit>()) }

    LaunchedEffect(Unit) {
        val rnd = Random(System.currentTimeMillis())
        bits = List(70) {
            Bit(
                x = rnd.nextFloat(),
                y = -0.05f - rnd.nextFloat() * 0.25f,
                vx = (rnd.nextFloat() - 0.5f) * 0.5f,
                vy = 0.45f + rnd.nextFloat() * 0.65f,
                rot = rnd.nextFloat() * 360f,
                vr = (rnd.nextFloat() - 0.5f) * 540f,
                color = PartyColors[rnd.nextInt(PartyColors.size)],
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
            bits = bits.map { b ->
                b.apply {
                    vy += 1.7f * dt // gravity
                    x += vx * dt
                    y += vy * dt
                    rot += vr * dt
                    life = (1f - elapsed / 1.9f).coerceAtLeast(0f)
                }
            }.filter { it.y < 1.25f && it.life > 0f }
            if (bits.isEmpty() || elapsed > 2.2f) break
        }
        onDone()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val s = size.minDimension / 85f
        bits.forEach { b ->
            val center = Offset(b.x * size.width, b.y * size.height)
            rotate(b.rot, center) {
                drawRect(
                    color = b.color.copy(alpha = b.life),
                    topLeft = Offset(center.x - s / 2, center.y - s / 4),
                    size = Size(s, s / 2),
                )
            }
        }
    }
}
