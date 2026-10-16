package com.m4ster.fiveinone.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/* Ambient asset animations: subtle infinite loops that bring the static
   PNG art to life. All are cheap transforms (scale/offset/alpha/rotation
   via graphicsLayer) — no per-frame allocations, no layout passes. */

/** Breathing wrapper: use around an Image for the idle bounce loop. */
@Composable
fun Breathing(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathT",
    )
    Box(
        modifier = modifier.graphicsLayer {
            val s = 1f + 0.035f * t
            scaleX = s
            scaleY = s
            translationY = -5f * t
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Diagonal shine stripe that sweeps across its bounds, pauses, repeats.
 * Overlay it on top of an image (trophies) for the sparkle effect.
 */
@Composable
fun ShineSweep(
    modifier: Modifier = Modifier,
    stripeAlpha: Float = 0.38f,
    cycleMs: Int = 3800,
    sweepMs: Int = 1300,
) {
    BoxWithConstraints(modifier = modifier.clip(RectangleShape)) {
        val density = LocalDensity.current
        // Snapshot sizes once: no per-frame measurement.
        val wPx = with(density) { maxWidth.toPx() }
        val stripePx = wPx * 0.30f
        val brush = remember(wPx, stripeAlpha) {
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = stripeAlpha),
                1f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(stripePx, 0f),
            )
        }
        val t by rememberInfiniteTransition(label = "shine").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = cycleMs
                    0f at 0
                    1f at sweepMs
                    1f at cycleMs // hold: pause before the next sweep
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "shineT",
        )
        val x = -stripePx * 1.4f + t * (wPx + stripePx * 2.8f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { stripePx.toDp() })
                .graphicsLayer {
                    translationX = x
                    rotationZ = 16f
                }
                .background(brush),
        )
    }
}

/** Trophy image with a looping shine sweep. Drop-in for the static trophy. */
@Composable
fun TrophySparkle(
    resId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        ShineSweep(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Slow ken-burns drift for card art: gentle zoom that eases in and out.
 * [phaseShiftMs] staggers cards so they don't pulse in sync.
 */
@Composable
fun KenBurnsArt(
    resId: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    phaseShiftMs: Int = 0,
) {
    val t by rememberInfiniteTransition(label = "kenburns").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000 + phaseShiftMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kbT",
    )
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier.graphicsLayer {
            val s = 1f + 0.09f * t
            scaleX = s
            scaleY = s
            translationX = -8f * t
        },
        contentScale = contentScale,
    )
}

/** Slow infinite rotation for the 2048 win star. */
@Composable
fun RotatingStar(
    resId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val r by rememberInfiniteTransition(label = "starspin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "starR",
    )
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = r },
    )
}
