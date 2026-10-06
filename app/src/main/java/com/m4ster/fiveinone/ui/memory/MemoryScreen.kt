package com.m4ster.fiveinone.ui.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Memory Match: flip the cards, clear the board in as few moves as
   possible. Three grid sizes; the fewest-moves best is persisted. */

private val CardIcons = listOf(
    Icons.Filled.Favorite,
    Icons.Filled.Star,
    Icons.Filled.Bolt,
    Icons.Filled.Cake,
    Icons.Filled.MusicNote,
    Icons.Filled.Flight,
    Icons.Filled.Anchor,
    Icons.Filled.DirectionsBike,
    Icons.Filled.SportsSoccer,
    Icons.Filled.Phone,
    Icons.Filled.Camera,
    Icons.Filled.Home,
)

@Composable
fun MemoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var size by remember { mutableStateOf(MemorySize.Medium) }
    var deck by remember { mutableStateOf(newDeck(size.pairs)) }
    var flipped by remember { mutableStateOf(listOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var moves by remember { mutableStateOf(0) }
    var lock by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var gameId by remember { mutableStateOf(0) }
    var unflipTick by remember { mutableStateOf(0) }
    val best by StatsStore.memoryBest(context).collectAsState(initial = 0)

    fun newGame(s: MemorySize = size) {
        size = s
        gameId++
        deck = newDeck(s.pairs)
        flipped = emptyList()
        matched = emptySet()
        moves = 0
        lock = false
        won = false
        unflipTick = 0
    }

    fun flip(i: Int) {
        if (lock || won || i in matched || i in flipped) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val pair = flipped + i
        flipped = pair
        if (pair.size < 2) return
        moves++
        if (deck[pair[0]] == deck[pair[1]]) {
            matched = matched + pair
            flipped = emptyList()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (matched.size == deck.size) {
                won = true
                celebrating = true
                scope.launch { StatsStore.recordMemoryMoves(context, moves) }
            }
        } else {
            lock = true
            unflipTick++
        }
    }

    // A mismatched pair flips back after a beat. Keyed on gameId so
    // starting a new game mid-delay cancels the stale flip-back.
    LaunchedEffect(unflipTick, gameId) {
        if (unflipTick == 0) return@LaunchedEffect
        val id = gameId
        delay(700)
        if (id != gameId) return@LaunchedEffect
        flipped = emptyList()
        lock = false
        unflipTick = 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemorySize.entries.forEach { s ->
                FilterChip(
                    selected = size == s,
                    onClick = { newGame(s) },
                    label = { Text(s.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("Moves $moves", style = MaterialTheme.typography.titleMedium)
            Text(
                if (best > 0) "Best $best" else "Best —",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            deck.chunked(size.cols).forEachIndexed { r, row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEachIndexed { c, iconId ->
                        val i = r * size.cols + c
                        MemoryCard(
                            faceUp = i in flipped || i in matched,
                            isMatched = i in matched,
                            icon = CardIcons[iconId],
                            onFlip = { flip(i) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Button(onClick = { newGame() }) { Text("New game") }
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

/* One card: back shows "?", front shows the icon. The whole card rotates
   around Y; the face swaps at 90 degrees so it reads as a real flip. */
@Composable
private fun MemoryCard(
    faceUp: Boolean,
    isMatched: Boolean,
    icon: ImageVector,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "flip",
    )
    Card(
        onClick = onFlip,
        enabled = !faceUp,
        modifier = modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 12f * density
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isMatched) AccentGreen.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (rotation < 90f) {
                Text(
                    "?",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Un-mirror the back half of the rotation.
                Box(Modifier.graphicsLayer { rotationY = 180f }) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isMatched) AccentGreen
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
