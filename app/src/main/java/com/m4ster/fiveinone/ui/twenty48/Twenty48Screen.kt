package com.m4ster.fiveinone.ui.twenty48

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.BuildConfig
import com.m4ster.fiveinone.ui.stats.StatsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/* 2048, epic edition: tiles glide with spring physics, merges pop,
   spawns spring in, scores float up. Hard mode renders stacked 3D
   tiles and celebrates big merges like a titan — screen shake, flash,
   particle bursts. Milestone tiles (256/512/1024/...) unlock new
   visual themes with Duolingo-style celebrations and a daily streak. */

/* One rendered tile: identity, value, logical cell, and UI flags. */
private data class UiTile(
    val tile: Tile,
    val cell: Int,
    val isNew: Boolean = false,
    val absorbed: Boolean = false,
    val popKey: Int = 0,
)

private data class Burst(val key: Long, val cell: Int)

@Composable
fun Twenty48Screen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWpx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    val progress = remember { Twenty48Progress(context) }
    var mode by remember { mutableStateOf(progress.mode) }
    var themeId by remember { mutableStateOf(progress.equippedId) }
    var unlocked by remember { mutableStateOf(progress.unlockedIds) }
    val theme = themeFor(themeId)

    var uiTiles by remember {
        mutableStateOf(
            newTileBoard().mapIndexedNotNull { i, t ->
                t?.let { UiTile(it, i, isNew = true) }
            },
        )
    }
    var score by remember { mutableStateOf(0) }
    var moves by remember { mutableStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var won by remember { mutableStateOf(false) }
    var showWin by remember { mutableStateOf(false) }
    var gameId by remember { mutableStateOf(0) }
    var hintDir by remember { mutableStateOf<Direction?>(null) }
    var hintTick by remember { mutableStateOf(0) }
    var themeMenu by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf(0) }
    val best by StatsStore.twenty48Best(context).collectAsState(initial = 0)

    // Ephemeral FX state.
    var floatUps by remember { mutableStateOf(listOf<FloatUp>()) }
    var bursts by remember { mutableStateOf(listOf<Burst>()) }
    var confettiKey by remember { mutableStateOf(0L) }
    var celebration by remember { mutableStateOf<Pair<Int, TileTheme>?>(null) }
    var epicUnlock by remember { mutableStateOf<TileTheme?>(null) }
    var winNewUnlock by remember { mutableStateOf(false) }
    val shakeT = remember { Animatable(0f) }
    var shakeAmp by remember { mutableStateOf(0f) }
    val flashA = remember { Animatable(0f) }
    var fxId by remember { mutableStateOf(1L) }

    // Daily streak + games-played, once per screen launch.
    LaunchedEffect(Unit) { streak = progress.recordGameStart() }

    // Milestone banners dismiss themselves.
    LaunchedEffect(celebration) {
        if (celebration != null) {
            delay(2600)
            celebration = null
        }
    }

    // The bank only keeps the high-water mark, so calling it twice is harmless.
    fun bankScore() {
        if (score > 0) scope.launch { StatsStore.recordTwenty48Score(context, score) }
    }

    fun newGame() {
        bankScore()
        gameId++
        uiTiles = newTileBoard().mapIndexedNotNull { i, t ->
            t?.let { UiTile(it, i, isNew = true) }
        }
        score = 0
        moves = 0
        over = false
        won = false
        showWin = false
        hintDir = null
        floatUps = emptyList()
        bursts = emptyList()
        celebration = null
        epicUnlock = null
        winNewUnlock = false
    }

    fun shake(amp: Float, durMs: Int) {
        scope.launch {
            shakeAmp = amp
            shakeT.snapTo(0f)
            shakeT.animateTo(1f, tween(durMs, easing = LinearEasing))
            shakeT.snapTo(0f)
        }
    }

    fun flash() {
        scope.launch {
            flashA.snapTo(0f)
            flashA.animateTo(1f, tween(90))
            flashA.animateTo(0f, tween(300))
        }
    }

    /* Titan merge (Hard mode, big merges): the board shudders, a flash
       rolls across, particles erupt from the merge cell, and the tile
       slams home. */
    fun titanFx(cell: Int) {
        shake(18f, 380)
        flash()
        val k = fxId++
        bursts = bursts + Burst(k, cell)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun doMove(dir: Direction) {
        if (over || showWin || epicUnlock != null) return
        hintDir = null
        val board = MutableList<Tile?>(GRID_SIZE * GRID_SIZE) { null }
        uiTiles.filterNot { it.absorbed }.forEach { board[it.cell] = it.tile }
        val result = moveTiles(board, dir)
        if (!result.moved) {
            shake(7f, 200) // Nudge: the board says "nope".
            return
        }
        // Reconcile render tiles from the animation list.
        val prev = uiTiles.associateBy { it.tile.id }
        uiTiles = result.anims.map { a ->
            val old = prev[a.tile.id]
            UiTile(
                tile = a.tile,
                cell = a.to,
                isNew = a.from == -1,
                absorbed = a.absorbed,
                popKey = (old?.popKey ?: 0) + if (a.isMergeResult) 1 else 0,
            )
        }
        // Absorbed tiles slide into the merge, then leave.
        val absorbedIds = result.anims.filter { it.absorbed }.map { it.tile.id }.toSet()
        if (absorbedIds.isNotEmpty()) {
            scope.launch {
                delay(230)
                uiTiles = uiTiles.filterNot { it.tile.id in absorbedIds }
            }
        }
        // Merge celebrations: float-ups always, titan FX in Hard mode.
        for (m in result.merges) {
            val k = fxId++
            floatUps = floatUps + FloatUp(k, m.cell, m.value, big = m.value >= 512)
            if (mode == GameMode.Hard && m.value >= 256) titanFx(m.cell)
        }
        score += result.gained
        moves++
        if (result.gained > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        // Milestones, unlocks, win, game over.
        val maxTile = result.board.maxOf { it?.value ?: 0 }
        progress.recordTile(maxTile)
        for (m in MILESTONES) {
            if (maxTile >= m) {
                val unlockedTheme = progress.unlockMilestone(m)
                if (unlockedTheme != null) {
                    themeId = unlockedTheme.id
                    unlocked = progress.unlockedIds
                    when {
                        m < 2048 -> {
                            celebration = m to unlockedTheme
                            confettiKey++
                        }
                        m == 2048 -> winNewUnlock = true
                        else -> {
                            epicUnlock = unlockedTheme
                            confettiKey++
                        }
                    }
                    // 2048's unlock surfaces through the win overlay below.
                }
            }
        }
        if (maxTile >= 2048 && !won) {
            won = true
            showWin = true
            confettiKey++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (!canMove(result.board)) {
            over = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            bankScore()
        }
    }

    // A hinted arrow fades after a beat so it reads as a nudge, not UI.
    // Keyed on gameId: a new game mid-fade cancels the stale arrow.
    LaunchedEffect(hintTick, gameId) {
        if (hintTick == 0) return@LaunchedEffect
        val id = gameId
        delay(1200)
        if (id == gameId) hintDir = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Score header.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("SCORE", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "$score",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (streak >= 2) {
                    Text(
                        "$streak-day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFF2C14E),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BEST", style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (best > 0) "$best" else "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Mode pills + theme picker.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModePill("Easy", mode == GameMode.Easy) {
                        mode = GameMode.Easy
                        progress.mode = GameMode.Easy
                    }
                    ModePill("Hard", mode == GameMode.Hard) {
                        mode = GameMode.Hard
                        progress.mode = GameMode.Hard
                    }
                }
                Box {
                    TextButton(onClick = { themeMenu = true }) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = "Tile theme",
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(theme.name)
                    }
                    DropdownMenu(
                        expanded = themeMenu,
                        onDismissRequest = { themeMenu = false },
                    ) {
                        TILE_THEMES.forEach { t ->
                            val locked = t.id !in unlocked
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (locked) "${t.name} · reach ${t.unlockAt}"
                                        else t.name,
                                    )
                                },
                                onClick = {
                                    if (!locked) {
                                        themeId = t.id
                                        progress.equippedId = t.id
                                    }
                                    themeMenu = false
                                },
                                enabled = !locked,
                            )
                        }
                    }
                }
            }
            // The board: static custom grid + absolutely-positioned tiles.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(gameId) {
                        var dragTotal = Offset.Zero
                        detectDragGestures(
                            onDragStart = { dragTotal = Offset.Zero },
                            onDrag = { _, amount -> dragTotal += amount },
                            onDragEnd = {
                                val dx = dragTotal.x
                                val dy = dragTotal.y
                                dragTotal = Offset.Zero
                                if (max(abs(dx), abs(dy)) < 24f) return@detectDragGestures
                                doMove(
                                    if (abs(dx) > abs(dy)) {
                                        if (dx > 0) Direction.Right else Direction.Left
                                    } else {
                                        if (dy > 0) Direction.Down else Direction.Up
                                    },
                                )
                            },
                            onDragCancel = { dragTotal = Offset.Zero },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val gapPx = with(density) { 8.dp.toPx() }
                val padPx = with(density) { 14.dp.toPx() }
                val boardPx = with(density) { maxWidth.toPx() }
                val tilePx = (boardPx - padPx * 2 - gapPx * 3) / 4
                val cornerPx = with(density) { 10.dp.toPx() }

                fun cellOrigin(cell: Int): IntOffset {
                    val c = cell % GRID_SIZE
                    val r = cell / GRID_SIZE
                    return IntOffset(
                        (padPx + c * (tilePx + gapPx)).roundToInt(),
                        (padPx + r * (tilePx + gapPx)).roundToInt(),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(with(density) { boardPx.toDp() })
                        .offset {
                            val t = shakeT.value
                            if (t <= 0f || shakeAmp <= 0f) IntOffset.Zero
                            else IntOffset(
                                (sin(t * Math.PI * 5).toFloat() * (1f - t) * shakeAmp)
                                    .roundToInt(),
                                0,
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawBoard(boardPx, theme, tilePx, gapPx, padPx, cornerPx)
                    }
                    // Tiles glide on their own animated offsets.
                    uiTiles.forEach { ui ->
                        key(ui.tile.id) {
                            TileItem(
                                ui = ui,
                                tilePx = tilePx,
                                cornerPx = cornerPx,
                                theme = theme,
                                mode = mode,
                                cellOrigin = ::cellOrigin,
                            )
                        }
                    }
                    // Score float-ups drift off their merge cells.
                    floatUps.forEach { f ->
                        key(f.key) {
                            FloatUpItem(f, ::cellOrigin, tilePx) {
                                floatUps = floatUps.filterNot { it.key == f.key }
                            }
                        }
                    }
                    // Titan particle bursts.
                    bursts.forEach { b ->
                        key(b.key) {
                            val c = cellOrigin(b.cell)
                            ParticleBurst(
                                key = b.key,
                                centerPx = Offset(
                                    c.x + tilePx / 2f,
                                    c.y + tilePx / 2f,
                                ),
                                colors = listOf(
                                    theme.tileColor(2048),
                                    Color(0xFFFFE9A3),
                                    Color.White,
                                ),
                                onDone = { bursts = bursts.filterNot { it.key == b.key } },
                            )
                        }
                    }
                    // Titan flash rolls across the board.
                    if (flashA.value > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.White.copy(alpha = flashA.value * 0.4f),
                                ),
                        )
                    }
                    // Hint arrow (withHints flavor).
                    hintDir?.let { dir ->
                        Icon(
                            when (dir) {
                                Direction.Up -> Icons.Filled.ArrowUpward
                                Direction.Down -> Icons.Filled.ArrowDownward
                                Direction.Left -> Icons.Filled.ArrowBack
                                Direction.Right -> Icons.Filled.ArrowForward
                            },
                            contentDescription = "Suggested move",
                            modifier = Modifier.fillMaxSize(0.4f),
                            tint = Color(0xFF4ADE80).copy(alpha = 0.85f),
                        )
                    }
                    // Milestone banner: slides down, celebrates, leaves.
                    MilestoneBanner(
                        celebration = celebration,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
            // Actions.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The noHints flavor leaves the game pure - no hint button at all.
                if (BuildConfig.HINTS_ENABLED) {
                    OutlinedButton(
                        onClick = {
                            val board = MutableList<Tile?>(GRID_SIZE * GRID_SIZE) { null }
                            uiTiles.filterNot { it.absorbed }
                                .forEach { board[it.cell] = it.tile }
                            suggestMove(board)?.let {
                                hintDir = it
                                hintTick++
                            }
                        },
                        enabled = !over,
                    ) { Text("Hint") }
                    OutlinedButton(onClick = ::newGame) { Text("New game") }
                } else {
                    OutlinedButton(onClick = ::newGame) { Text("New game") }
                }
            }
        }

        // Confetti rains over everything on wins and unlocks.
        if (confettiKey > 0) {
            key(confettiKey) {
                ConfettiOverlay(
                    key = confettiKey,
                    widthPx = screenWpx,
                    onDone = {},
                )
            }
        }

        // Win overlay: 2048 reached.
        AnimatedVisibility(
            visible = showWin,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.padding(32.dp)) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "2048!",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFE9A3),
                        )
                        if (winNewUnlock) {
                            Text(
                                "Titan Gold theme unlocked",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            "Score $score · $moves moves",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { showWin = false },
                            ) { Text("Keep going") }
                            OutlinedButton(onClick = {
                                showWin = false
                                newGame()
                            }) { Text("New game") }
                        }
                    }
                }
            }
        }

        // Epic unlock overlay: 4096, 8192, ...
        AnimatedVisibility(
            visible = epicUnlock != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                epicUnlock?.let { t ->
                    Card(modifier = Modifier.padding(32.dp)) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${t.unlockAt}!",
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFE9A3),
                            )
                            Text(
                                "${t.name} theme unlocked",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Score $score · $moves moves",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = { epicUnlock = null }) {
                                Text("Keep playing")
                            }
                        }
                    }
                }
            }
        }

        // Game-over overlay: polished entrance, stats, replay.
        AnimatedVisibility(
            visible = over,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.padding(32.dp)) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "No moves left",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text("Score $score", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (best > 0) "Best $best" else "Best —",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(onClick = ::newGame) { Text("New game") }
                    }
                }
            }
        }
    }
}

/* One tile: glides on a spring to its cell, springs in on spawn,
   pops with a flash on merge. Custom-drawn face, 3D in Hard mode. */
@Composable
private fun TileItem(
    ui: UiTile,
    tilePx: Float,
    cornerPx: Float,
    theme: TileTheme,
    mode: GameMode,
    cellOrigin: (Int) -> IntOffset,
) {
    val target = cellOrigin(ui.cell)
    val offset = remember(ui.tile.id) {
        Animatable(target, IntOffset.VectorConverter)
    }
    val fx = remember(ui.tile.id) { TileFx(if (ui.isNew) 0f else 1f) }

    LaunchedEffect(ui.cell) {
        val t = cellOrigin(ui.cell)
        if (offset.value != t) {
            offset.animateTo(
                t,
                spring(dampingRatio = 0.82f, stiffness = 1100f),
            )
        }
    }
    // Spawn: spring in from nothing.
    LaunchedEffect(ui.tile.id) {
        fx.scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 550f))
    }
    // Merge: springy overshoot pop plus a brightness flash.
    LaunchedEffect(ui.popKey) {
        if (ui.popKey > 0) {
            fx.scale.animateTo(1.34f, spring(dampingRatio = 0.42f, stiffness = 700f))
            fx.scale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 950f))
            fx.flash.animateTo(0.55f, tween(90))
            fx.flash.animateTo(0f, tween(260))
        }
    }

    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { offset.value }
            .size(with(density) { tilePx.toDp() })
            .graphicsLayer {
                scaleX = fx.scale.value
                scaleY = fx.scale.value
            },
    ) {
        val base = theme.tileColor(ui.tile.value)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTileFace(tilePx, cornerPx, base, mode, ui.tile.value, fx.flash.value)
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${ui.tile.value}",
                fontSize = when {
                    ui.tile.value < 100 -> 28.sp
                    ui.tile.value < 1000 -> 24.sp
                    ui.tile.value < 10000 -> 20.sp
                    else -> 16.sp
                },
                fontWeight = FontWeight.Black,
                color = theme.textColor(ui.tile.value),
            )
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            label,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            color = if (selected) Color(0xFFFFE9A3)
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Milestone banner as a file-level composable so AnimatedVisibility resolves
// its plain overload: inside the board Box an outer ColumnScope was hijacking
// overload resolution and breaking compilation.
@Composable
private fun MilestoneBanner(
    celebration: Pair<Int, TileTheme>?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = celebration != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        celebration?.let { (value, t) ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1F2937).copy(alpha = 0.95f),
                ),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 12.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "$value REACHED!",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFFFFE9A3),
                    )
                    Text(
                        "${t.name} theme unlocked",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
