package com.m4ster.fiveinone.ui.twenty48

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/* 2048 visual identity: unlockable tile themes, all code-drawn.
   Each theme owns the board background, the empty cells, and a color
   ramp climbed as tiles double. Higher themes = harder milestones. */

enum class GameMode { Easy, Hard }

data class TileTheme(
    val id: String,
    val name: String,
    val unlockAt: Int, // tile value that unlocks it; 0 = available from the start
    val boardBg: Color,
    val emptyCell: Color,
    val low: Color, // color of the 2 tile
    val mid: Color, // color around 128
    val high: Color, // color at 2048 and beyond
) {
    /* Smooth HSV ramp: 2 -> low, 128 -> mid, 2048+ -> high. */
    fun tileColor(value: Int): Color {
        if (value <= 0) return emptyCell
        val level = (31 - Integer.numberOfLeadingZeros(value) - 1).coerceIn(0, 13)
        val t = (level / 10f).coerceIn(0f, 1f)
        return if (t < 0.5f) lerpHsv(low, mid, t * 2f)
        else lerpHsv(mid, high, (t - 0.5f) * 2f)
    }

    fun textColor(value: Int): Color {
        val c = tileColor(value)
        val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return if (lum > 0.62f) Color(0xFF232733) else Color(0xFFFBF7EF)
    }
}

private fun lerpHsv(a: Color, b: Color, t: Float): Color {
    val ha = FloatArray(3)
    val hb = FloatArray(3)
    android.graphics.Color.colorToHSV(a.toArgb(), ha)
    android.graphics.Color.colorToHSV(b.toArgb(), hb)
    var dh = hb[0] - ha[0]
    if (dh > 180f) dh -= 360f else if (dh < -180f) dh += 360f
    val h = (ha[0] + dh * t + 360f) % 360f
    val s = ha[1] + (hb[1] - ha[1]) * t
    val v = ha[2] + (hb[2] - ha[2]) * t
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
}

val TILE_THEMES = listOf(
    TileTheme(
        id = "classic", name = "Classic", unlockAt = 0,
        boardBg = Color(0xFF1C2230), emptyCell = Color(0xFF2A3344),
        low = Color(0xFF3D4E70), mid = Color(0xFFE07B2E), high = Color(0xFF4ADE80),
    ),
    TileTheme(
        id = "ember", name = "Ember", unlockAt = 256,
        boardBg = Color(0xFF1B1210), emptyCell = Color(0xFF2E1E14),
        low = Color(0xFFF2C14E), mid = Color(0xFFE0652E), high = Color(0xFFFF3B1F),
    ),
    TileTheme(
        id = "abyss", name = "Abyss", unlockAt = 512,
        boardBg = Color(0xFF0C1320), emptyCell = Color(0xFF16202F),
        low = Color(0xFF7DD3FC), mid = Color(0xFF3B82F6), high = Color(0xFF8B5CF6),
    ),
    TileTheme(
        id = "venom", name = "Venom", unlockAt = 1024,
        boardBg = Color(0xFF0D1811), emptyCell = Color(0xFF17281D),
        low = Color(0xFFA3E635), mid = Color(0xFF22C55E), high = Color(0xFF14B8A6),
    ),
    TileTheme(
        id = "titan", name = "Titan Gold", unlockAt = 2048,
        boardBg = Color(0xFF141109), emptyCell = Color(0xFF26200F),
        low = Color(0xFFD4A24C), mid = Color(0xFFF2C14E), high = Color(0xFFFFE9A3),
    ),
    TileTheme(
        id = "neon", name = "Neon", unlockAt = 4096,
        boardBg = Color(0xFF08080F), emptyCell = Color(0xFF12121E),
        low = Color(0xFF22D3EE), mid = Color(0xFFE879F9), high = Color(0xFFF8FAFC),
    ),
    TileTheme(
        id = "cosmic", name = "Cosmic", unlockAt = 8192,
        boardBg = Color(0xFF100C1C), emptyCell = Color(0xFF1D152E),
        low = Color(0xFFC084FC), mid = Color(0xFFE879F9), high = Color(0xFFFDE68A),
    ),
)

/* Tile values that unlock a new theme, in climb order. */
val MILESTONES = listOf(256, 512, 1024, 2048, 4096, 8192)

fun themeFor(id: String): TileTheme =
    TILE_THEMES.find { it.id == id } ?: TILE_THEMES[0]

fun themeForMilestone(value: Int): TileTheme? =
    TILE_THEMES.find { it.unlockAt == value }

/* ---- Code-drawn tiles: Easy gets a clean custom tile, Hard gets
   real depth — layered shadow, extruded sides, bevel highlight,
   with the stack height growing as the tile value climbs. ---- */

fun DrawScope.drawTileFace(
    sizePx: Float,
    cornerPx: Float,
    base: Color,
    mode: GameMode,
    value: Int,
    flash: Float,
) {
    val size = Size(sizePx, sizePx)
    if (mode == GameMode.Hard) {
        // Stacked depth: taller tiles for bigger values.
        val level = (31 - Integer.numberOfLeadingZeros(value.coerceAtLeast(2)) - 1).coerceIn(0, 13)
        val depth = sizePx * (0.06f + level * 0.012f)
        // Soft layered shadow.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.38f),
            topLeft = Offset(sizePx * 0.04f, depth + sizePx * 0.05f),
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        // Extruded sides: darker walls beneath the top face.
        drawRoundRect(
            color = base.copy(alpha = 1f).darken(0.35f),
            topLeft = Offset(0f, depth),
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        // Top face with a vertical light gradient.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(base.lighten(0.16f), base, base.darken(0.08f)),
            ),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        // Bevel: bright top edge, dark bottom edge.
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(cornerPx, sizePx * 0.06f),
            end = Offset(sizePx - cornerPx, sizePx * 0.06f),
            strokeWidth = sizePx * 0.035f,
        )
    } else {
        // Easy: clean custom tile — soft shadow, gentle gradient.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.28f),
            topLeft = Offset(sizePx * 0.03f, sizePx * 0.045f),
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(base.lighten(0.12f), base.darken(0.06f))),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(cornerPx, sizePx * 0.05f),
            end = Offset(sizePx - cornerPx, sizePx * 0.05f),
            strokeWidth = sizePx * 0.03f,
        )
    }
    if (flash > 0.01f) {
        drawRoundRect(
            color = Color.White.copy(alpha = flash.coerceIn(0f, 0.6f)),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
    }
}

private fun Color.lighten(by: Float): Color {
    val h = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), h)
    h[2] = (h[2] + by).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(h))
}

private fun Color.darken(by: Float): Color {
    val h = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), h)
    h[2] = (h[2] - by).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(h))
}

/* Board: theme-tinted panel with inset empty cells. */
fun DrawScope.drawBoard(
    widthPx: Float,
    theme: TileTheme,
    tilePx: Float,
    gapPx: Float,
    padPx: Float,
    cornerPx: Float,
) {
    drawRoundRect(
        color = theme.boardBg,
        topLeft = Offset.Zero,
        size = Size(widthPx, widthPx),
        cornerRadius = CornerRadius(cornerPx * 1.6f, cornerPx * 1.6f),
    )
    // Faint top sheen so the panel doesn't read flat.
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
        ),
        topLeft = Offset.Zero,
        size = Size(widthPx, widthPx * 0.4f),
        cornerRadius = CornerRadius(cornerPx * 1.6f, cornerPx * 1.6f),
    )
    for (cell in 0 until GRID_SIZE * GRID_SIZE) {
        val col = cell % GRID_SIZE
        val row = cell / GRID_SIZE
        val x = padPx + col * (tilePx + gapPx)
        val y = padPx + row * (tilePx + gapPx)
        // Inset well: dark fill plus a light bottom lip for depth.
        drawRoundRect(
            color = theme.emptyCell,
            topLeft = Offset(x, y),
            size = Size(tilePx, tilePx),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(x + cornerPx, y + tilePx - 2f),
            end = Offset(x + tilePx - cornerPx, y + tilePx - 2f),
            strokeWidth = 3f,
        )
    }
}

/* Dp -> px helper so draw code stays unit-clean. */
fun Dp.toPxFloat(density: Density): Float = with(density) { toPx() }
