package com.m4ster.fiveinone.ui.memory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.m4ster.fiveinone.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.components.PraiseBanner
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentGreen
import com.m4ster.fiveinone.ui.twenty48.ParticleBurst
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
    var newBest by remember { mutableStateOf(false) }
    var gameId by remember { mutableStateOf(0) }
    var unflipTick by remember { mutableStateOf(0) }
    // Juice: the latest matched pair (for the burst), consecutive-match
    // streak (for the escalating praise), and the praise itself.
    var lastMatched by remember { mutableStateOf(listOf<Int>()) }
    var matchKey by remember { mutableStateOf(0L) }
    var matchStreak by remember { mutableStateOf(0) }
    var praise by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var praiseSeq by remember { mutableStateOf(0L) }
    val best by StatsStore.memoryBest(context).collectAsState(initial = 0)

    // Card-back art follows the difficulty: easy / medium / hard.
    val cardBackRes = when (size) {
        MemorySize.Easy -> R.drawable.sprite_card_back_easy
        MemorySize.Medium -> R.drawable.sprite_card_back_medium
        MemorySize.Hard -> R.drawable.sprite_card_back_hard
    }

    fun newGame(s: MemorySize = size) {
        size = s
        gameId++
        deck = newDeck(s.pairs)
        flipped = emptyList()
        matched = emptySet()
        moves = 0
        lock = false
        won = false
        celebrating = false
        newBest = false
        unflipTick = 0
        lastMatched = emptyList()
        matchStreak = 0
        praise = null
    }

    fun nextSize(): MemorySize? = when (size) {
        MemorySize.Easy -> MemorySize.Medium
        MemorySize.Medium -> MemorySize.Hard
        MemorySize.Hard -> null
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
            // Juice: burst on the pair, escalating praise for streaks.
            matchKey++
            lastMatched = pair
            matchStreak++
            if (matchStreak >= 2) {
                praiseSeq++
                praise = when {
                    matchStreak >= 4 -> "Divine!"
                    matchStreak == 3 -> "Tasty!"
                    else -> "Sweet!"
                } to praiseSeq
            }
            val burstId = matchKey
            scope.launch {
                delay(900)
                if (matchKey == burstId) lastMatched = emptyList()
            }
            if (matched.size == deck.size) {
                won = true
                newBest = best == 0 || moves < best
                celebrating = true
                scope.launch { StatsStore.recordMemoryMoves(context, moves) }
            }
        } else {
            lock = true
            unflipTick++
            matchStreak = 0 // a miss breaks the praise streak
        }
    }

    // A mismatched pair flips back after a beat. Keyed on gameId so
    // starting a new game mid-delay cancels the stale flip-back.
    LaunchedEffect(unflipTick, gameId) {
        if (unflipTick == 0) return@LaunchedEffect
        val id = gameId
        delay(600)
        if (id != gameId) return@LaunchedEffect
        flipped = emptyList()
        lock = false
        unflipTick = 0
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_memory),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
        )
        Column(
            modifier = Modifier
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
        // The board cross-fades + settles in on every new game / level.
        AnimatedContent(
            targetState = gameId,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = tween(300))) togetherWith
                    fadeOut(tween(120))
            },
            label = "board",
            modifier = Modifier.weight(1f),
        ) {
            Column(
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
                                backRes = cardBackRes,
                                onFlip = { flip(i) },
                                modifier = Modifier.weight(1f),
                                burstKey = if (i in lastMatched) matchKey else null,
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = { newGame() }) { Text("New game") }
        if (celebrating) {            Dialog(
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
                    Celebration()
                    WinCard(
                        sizeLabel = size.label,
                        moves = moves,
                        newBest = newBest,
                        best = if (newBest) moves else best,
                        nextLabel = nextSize()?.label,
                        onNext = {
                            celebrating = false
                            nextSize()?.let { newGame(it) }
                        },
                        onReplay = {
                            celebrating = false
                            newGame()
                        },
                    )
                }
            }
        }
        // Escalating praise for consecutive matches, over everything.
        praise?.let { (text, id) ->
            PraiseBanner(
                text = text,
                key = id,
                modifier = Modifier.fillMaxSize(),
                onDone = { praise = null },
            )
        }
    }
}

/* Level-complete card: sits over the confetti with the score, best,
   and where to go next. */
@Composable
private fun WinCard(
    sizeLabel: String,
    moves: Int,
    newBest: Boolean,
    best: Int,
    nextLabel: String?,
    onNext: () -> Unit,
    onReplay: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.sprite_trophy),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text("Level Complete!", style = MaterialTheme.typography.headlineSmall)
            Text(
                "$sizeLabel · $moves moves",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (newBest) "New best!" else "Best $best",
                style = MaterialTheme.typography.titleMedium,
                color = if (newBest) AccentGreen else MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nextLabel != null) {
                    Button(onClick = onNext) { Text("Next: $nextLabel") }
                }
                androidx.compose.material3.OutlinedButton(onClick = onReplay) {
                    Text("Replay")
                }
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
    backRes: Int,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    burstKey: Long? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
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
                Image(
                    painter = painterResource(backRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
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
            // Match burst: particles erupt from the card, unclipped.
            if (burstKey != null) {
                var center by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { center = Offset(it.width / 2f, it.height / 2f) },
                ) {
                    ParticleBurst(
                        key = burstKey,
                        centerPx = center,
                        colors = listOf(
                            AccentGreen,
                            Color.White,
                            Color(0xFFFFE9A3),
                        ),
                        onDone = {},
                    )
                }
            }
        }
        }
    }
}
