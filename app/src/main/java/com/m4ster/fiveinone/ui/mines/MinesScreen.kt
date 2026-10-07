@file:OptIn(ExperimentalFoundationApi::class)

package com.m4ster.fiveinone.ui.mines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.theme.AccentAmber
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.theme.AccentRed
import com.m4ster.fiveinone.ui.theme.BoardAlt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* Minesweeper: tap to reveal, long-press to flag. Difficulty chips,
   flood-fill reveals, and the first tap is always safe. Win by
   revealing every safe cell; the board celebrates with confetti. */

@Composable
fun MinesScreen(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var difficulty by remember { mutableStateOf(MineDifficulty.Easy) }
    var board by remember { mutableStateOf<List<MineCell>?>(null) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var flagged by remember { mutableStateOf(setOf<Int>()) }
    var over by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }

    val cells = difficulty.rows * difficulty.cols
    val minesLeft = difficulty.mines - flagged.size
    val lost = over && !won

    fun newGame(d: MineDifficulty = difficulty) {
        difficulty = d
        board = null
        revealed = emptySet()
        flagged = emptySet()
        over = false
        won = false
        celebrating = false
    }

    fun tap(i: Int) {
        if (over || i in revealed || i in flagged) return
        // First tap builds the field around the tapped cell.
        val field = board ?: newMinefield(difficulty, i).also { board = it }
        if (field[i].mined) {
            revealed = revealed + i
            over = true
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val open = reveal(difficulty, field, revealed, i)
        revealed = open
        if (open.size == cells - difficulty.mines) {
            over = true
            won = true
            celebrating = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun toggleFlag(i: Int) {
        if (over || i in revealed) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        flagged = if (i in flagged) flagged - i else flagged + i
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MineDifficulty.values().forEach { d ->
                FilterChip(
                    selected = difficulty == d,
                    onClick = { newGame(d) },
                    label = { Text(d.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                when {
                    won -> "Cleared!"
                    lost -> "Boom"
                    else -> "Mines $minesLeft"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    won -> AccentGreen
                    lost -> AccentRed
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            Text(
                "${difficulty.rows}×${difficulty.cols} · ${difficulty.mines} mines",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Cells sized off the real width so the grid always fits.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val gap = 4.dp
            val size = (maxWidth - gap * (difficulty.cols - 1)) / difficulty.cols
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until difficulty.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until difficulty.cols) {
                            val i = r * difficulty.cols + c
                            val cell = board?.get(i)
                            MineTile(
                                cell = cell,
                                isRevealed = i in revealed,
                                isFlagged = i in flagged,
                                showMine = lost && cell?.mined == true && i !in flagged,
                                misFlagged = lost && i in flagged && cell?.mined == false,
                                size = size,
                                onTap = { tap(i) },
                                onFlag = { toggleFlag(i) },
                            )
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { newGame() }) { Text("New game") }
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

/* One cell: covered, flagged, revealed number, or a mine. After a
   loss, unflagged mines are exposed and wrong flags turn red. */
@Composable
private fun MineTile(
    cell: MineCell?,
    isRevealed: Boolean,
    isFlagged: Boolean,
    showMine: Boolean,
    misFlagged: Boolean,
    size: Dp,
    onTap: () -> Unit,
    onFlag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.size(size),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRevealed && cell?.mined == true -> AccentRed.copy(alpha = 0.35f)
                isRevealed -> BoardAlt
                misFlagged -> AccentRed.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onTap, onLongClick = onFlag),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isRevealed && cell?.mined == true -> MineIcon()
                isRevealed && (cell?.adjacent ?: 0) > 0 -> Text(
                    "${cell?.adjacent}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = numberColor(cell?.adjacent ?: 0),
                )
                isFlagged -> Icon(
                    Icons.Filled.Flag,
                    contentDescription = "Flagged",
                    modifier = Modifier.size(size * 0.55f),
                    tint = if (misFlagged) AccentRed else AccentAmber,
                )
                showMine -> MineIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/* A spiked mine drawn with Canvas — no icon font needed. */
@Composable
private fun MineIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val r = size.minDimension / 2
        val c = center
        for (a in 0 until 8) {
            val ang = a * PI / 4
            val dir = Offset(cos(ang).toFloat(), sin(ang).toFloat())
            drawLine(
                tint,
                c + dir * r * 0.65f,
                c + dir * r,
                strokeWidth = r * 0.18f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(tint, r * 0.6f, c)
        drawCircle(Color.White, r * 0.16f, c + Offset(-r * 0.2f, -r * 0.2f))
    }
}

private fun numberColor(n: Int): Color = when (n) {
    1 -> Color(0xFF60A5FA)
    2 -> Color(0xFF4ADE80)
    3 -> Color(0xFFF87171)
    4 -> Color(0xFF818CF8)
    5 -> Color(0xFFFBBF24)
    6 -> Color(0xFF2DD4BF)
    7 -> Color(0xFFF472B6)
    else -> Color(0xFF9CA3AF)
}
