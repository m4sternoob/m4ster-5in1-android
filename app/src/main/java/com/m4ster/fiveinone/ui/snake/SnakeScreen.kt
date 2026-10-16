package com.m4ster.fiveinone.ui.snake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.R
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.components.TrophySparkle
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/* Canvas snake with a few more knobs: three speeds, solid or wrapping
   walls, a persisted high score, and some juice — pulsing food, a score
   pop on every dot, and a gradient body. Swipe or the on-screen D-pad
   steers; 180° turns are ignored. Pace still quickens every 5 dots. */

private const val COLS = 20
private const val ROWS = 20
private const val BASE_STEP_MS = 170L
private const val MIN_STEP_MS = 70L
private const val DOTS_PER_LEVEL = 5
private const val SPEEDUP_PER_LEVEL_MS = 15L

private enum class Speed(val label: String, val mult: Double) {
    Chill("Chill", 1.45),
    Normal("Normal", 1.0),
    Insane("Insane", 0.6),
}

private enum class Walls(val label: String) {
    Solid("Solid walls"),
    Wrap("Wrap walls"),
}

/* Faster every 5 dots: 170ms a step at start, down to a 70ms floor. */
private fun stepMsFor(score: Int): Long {
    val levels = score / DOTS_PER_LEVEL
    return (BASE_STEP_MS - levels * SPEEDUP_PER_LEVEL_MS).coerceAtLeast(MIN_STEP_MS)
}

private enum class Dir(val dx: Int, val dy: Int) {
    Up(0, -1),
    Down(0, 1),
    Left(-1, 0),
    Right(1, 0);

    fun opposite(): Dir = when (this) {
        Up -> Down
        Down -> Up
        Left -> Right
        Right -> Left
    }
}

private data class Pos(val x: Int, val y: Int)

/* One food-eat burst: where it happened and whether it was golden. */
private data class EatFx(val key: Long, val at: Pos, val golden: Boolean)

@Composable
fun SnakeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val best by StatsStore.snakeBest(context).collectAsState(initial = 0)

    // Sprite art, loaded once: head, body segment, tail, golden food.
    val headBmp = imageResource(R.drawable.sprite_snake_head)
    val bodyBmp = imageResource(R.drawable.sprite_snake_body)
    val tailBmp = imageResource(R.drawable.sprite_snake_tail)
    val goldBmp = imageResource(R.drawable.sprite_food_golden)

    var snake by remember { mutableStateOf(listOf(Pos(10, 10), Pos(9, 10), Pos(8, 10))) }
    var dir by remember { mutableStateOf(Dir.Right) }
    var food by remember { mutableStateOf(Pos(14, 10)) }
    var score by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(Speed.Normal) }
    var walls by remember { mutableStateOf(Walls.Solid) }
    var newBest by remember { mutableStateOf(false) }
    var showPop by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf(false) }
    var lastGain by remember { mutableStateOf(0) }
    // Golden food: rare, worth more, despawns. Combo: chained quick eats
    // multiply normal food, up to x5.
    var golden by remember { mutableStateOf<Pos?>(null) }
    var goldenTtl by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(1) }
    var comboTtl by remember { mutableStateOf(0) }
    // Eat-burst FX: 0→1 progress while a burst is alive.
    var eatFx by remember { mutableStateOf<EatFx?>(null) }
    var fxSeq by remember { mutableStateOf(0L) }
    val burstT = remember { Animatable(0f) }
    LaunchedEffect(eatFx?.key) {
        val fx = eatFx ?: return@LaunchedEffect
        burstT.snapTo(0f)
        burstT.animateTo(1f, tween(450))
        eatFx = null
    }

    // Food pulse: pure UI animation, ping-pongs forever between the two sizes.
    var pulseTarget by remember { mutableStateOf(1.18f) }
    val pulse by animateFloatAsState(
        targetValue = pulseTarget,
        animationSpec = tween(700),
        label = "pulse",
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            pulseTarget = if (pulseTarget > 1f) 0.82f else 1.18f
        }
    }
    LaunchedEffect(score) {
        if (score > 0) {
            showPop = true
            delay(700)
            showPop = false
        }
    }

    fun randomFood(exclude: List<Pos>): Pos {
        while (true) {
            val p = Pos(Random.nextInt(COLS), Random.nextInt(ROWS))
            if (p !in exclude) return p
        }
    }

    fun reset() {
        val fresh = listOf(Pos(10, 10), Pos(9, 10), Pos(8, 10))
        snake = fresh
        dir = Dir.Right
        score = 0
        alive = true
        newBest = false
        combo = 1
        comboTtl = 0
        golden = null
        food = randomFood(fresh)
        running = true
    }

    fun step() {
        if (goldenTtl > 0) {
            goldenTtl--
            if (goldenTtl == 0) golden = null
        }
        if (comboTtl > 0) {
            comboTtl--
            if (comboTtl == 0) combo = 1
        }
        val head = snake.first()
        val raw = Pos(head.x + dir.dx, head.y + dir.dy)
        val next = if (walls == Walls.Wrap) {
            Pos((raw.x + COLS) % COLS, (raw.y + ROWS) % ROWS)
        } else raw
        val hitWall = walls == Walls.Solid &&
            (next.x !in 0 until COLS || next.y !in 0 until ROWS)
        val ate = next == food
        val ateGolden = golden != null && next == golden
        // Moving into the departing tail is legal when not eating.
        val body = if (ate || ateGolden) snake else snake.dropLast(1)
        if (hitWall || next in body) {
            alive = false
            running = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            newBest = score > 0 && score > best
            if (newBest) celebrating = true
            scope.launch { StatsStore.recordSnakeScore(context, score) }
            return
        }
        if (ate || ateGolden) {
            lastGain = if (ateGolden) 3 * combo else combo
            score += lastGain
            combo = minOf(combo + 1, 5)
            comboTtl = 25
            eatFx = EatFx(fxSeq++, next, ateGolden)
            if (ateGolden) {
                golden = null
            } else {
                food = randomFood(snake + next)
                if (golden == null && Random.nextFloat() < 0.12f) {
                    golden = randomFood(snake + next + food)
                    goldenTtl = 45
                }
            }
        }
        snake = listOf(next) + if (ate || ateGolden) snake else snake.dropLast(1)
    }

    // Game loop: restarts whenever `running` toggles; exits when it goes false.
    // Pace quickens as the score climbs and follows the speed pick live.
    LaunchedEffect(running) {
        while (running) {
            delay((stepMsFor(score) * speed.mult).toLong())
            step()
        }
    }

    // Shared steering rule for swipe and D-pad: no 180° turns,
    // they'd kill the snake instantly.
    fun steer(want: Dir) {
        if (want == dir.opposite()) return
        dir = want
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    @Composable
    fun PadButton(target: Dir, icon: ImageVector, label: String) {
        Button(
            onClick = { steer(target) },
            modifier = Modifier.size(64.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(36.dp))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.bg_snake),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Score: $score · Best $best",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (combo > 1) {
                Text(
                    "x$combo",
                    color = AccentAmber,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            AnimatedVisibility(
                visible = showPop,
                enter = scaleIn() + fadeIn(),
                exit = fadeOut(),
            ) {
                Text("+$lastGain", color = Color(0xFF4ADE80), style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Speed.entries.forEach { s ->
                FilterChip(
                    selected = s == speed,
                    onClick = { speed = s },
                    label = { Text(s.label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Walls.entries.forEach { w ->
                FilterChip(
                    selected = w == walls,
                    onClick = { walls = w },
                    label = { Text(w.label) },
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        val (x, y) = dragAmount
                        val want = if (abs(x) > abs(y)) {
                            if (x > 0) Dir.Right else Dir.Left
                        } else {
                            if (y > 0) Dir.Down else Dir.Up
                        }
                        steer(want)
                    }
                },
        ) {
            // Green frame = solid walls, blue frame = wrap. Board inset;
            // everything draws in board coordinates.
            val frame = if (walls == Walls.Solid) Color(0xFF16A34A) else Color(0xFF38BDF8)
            drawRect(frame, size = size)
            val bw = size.width * 0.02f
            val board = Size(size.width - 2 * bw, size.height - 2 * bw)
            val origin = Offset(bw, bw)
            drawRect(Color(0xFF0B1220), topLeft = origin, size = board)
            val cell = board.width / COLS
            fun px(p: Pos) = Offset(origin.x + p.x * cell, origin.y + p.y * cell)
            // Pulsing food.
            val fc = Offset(origin.x + food.x * cell + cell / 2, origin.y + food.y * cell + cell / 2)
            drawCircle(Color(0xFFF87171), radius = cell * 0.42f * pulse, center = fc)
            // Golden food sprite: worth 3× combo, blinks before it despawns.
            golden?.let { gp ->
                val gc = Offset(origin.x + gp.x * cell + cell / 2, origin.y + gp.y * cell + cell / 2)
                val alpha = if (goldenTtl < 12 && goldenTtl % 2 == 0) 0.35f else 1f
                val gs = (cell * 1.5f * pulse).roundToInt()
                // Pulsing glow halo: two cheap concentric circles, no allocations.
                val glowR = cell * 0.95f * pulse
                drawCircle(Color(0xFFFFD34D).copy(alpha = 0.16f * alpha), radius = glowR, center = gc)
                drawCircle(Color(0xFFFFD34D).copy(alpha = 0.10f * alpha), radius = glowR * 1.4f, center = gc)
                drawImage(
                    goldBmp,
                    dstOffset = IntOffset(
                        (gc.x - gs / 2).roundToInt(),
                        (gc.y - gs / 2).roundToInt(),
                    ),
                    dstSize = IntSize(gs, gs),
                    alpha = alpha,
                )
            }
            // Sprite snake: the head aims along `dir`, body segments follow
            // the flow, the tail taper trails behind.
            fun drawSpriteCentered(
                bmp: ImageBitmap,
                center: Offset,
                sizePx: Float,
                rotDeg: Float,
            ) {
                val ds = sizePx.roundToInt()
                rotate(rotDeg, center) {
                    drawImage(
                        bmp,
                        dstOffset = IntOffset(
                            (center.x - ds / 2).roundToInt(),
                            (center.y - ds / 2).roundToInt(),
                        ),
                        dstSize = IntSize(ds, ds),
                    )
                }
            }
            val headDeg = when (dir) {
                Dir.Right -> 0f
                Dir.Down -> 90f
                Dir.Left -> 180f
                Dir.Up -> 270f
            }
            val n = snake.size
            // Speed lines: once the snake gets long it feels fast.
            if (n >= 10 && alive) {
                val headC = px(snake.first()) + Offset(cell / 2, cell / 2)
                val back = when (dir) {
                    Dir.Right -> Offset(-1f, 0f)
                    Dir.Left -> Offset(1f, 0f)
                    Dir.Down -> Offset(0f, -1f)
                    Dir.Up -> Offset(0f, 1f)
                }
                for (i in 1..3) {
                    val start = headC + back * (cell * (0.9f + (i - 1) * 0.55f))
                    drawLine(
                        Color.White.copy(alpha = 0.20f - i * 0.05f),
                        start,
                        start + back * cell * (0.7f + i * 0.6f),
                        strokeWidth = cell * 0.13f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            snake.forEachIndexed { i, p ->
                val center = px(p) + Offset(cell / 2, cell / 2)
                when (i) {
                    0 -> drawSpriteCentered(headBmp, center, cell * 1.7f, headDeg)
                    n - 1 -> {
                        val prev = snake[n - 2]
                        val tailDeg = when {
                            p.x > prev.x -> 0f
                            p.x < prev.x -> 180f
                            p.y > prev.y -> 90f
                            else -> 270f
                        }
                        drawSpriteCentered(tailBmp, center, cell * 1.6f, tailDeg)
                    }
                    else -> {
                        val a = snake[i - 1]
                        val b = snake[i + 1]
                        drawSpriteCentered(
                            bodyBmp, center, cell * 1.6f,
                            if (a.x == b.x) 90f else 0f,
                        )
                    }
                }
            }
            // Eat burst: expanding ring + radiating sparks at the food cell.
            eatFx?.let { fx ->
                val t = burstT.value
                if (t < 1f) {
                    val c = Offset(
                        origin.x + fx.at.x * cell + cell / 2,
                        origin.y + fx.at.y * cell + cell / 2,
                    )
                    val col = if (fx.golden) Color(0xFFFFD700) else Color(0xFFF87171)
                    val alpha = 1f - t
                    drawCircle(
                        col.copy(alpha = alpha * 0.9f),
                        radius = cell * (0.5f + 1.7f * t),
                        center = c,
                        style = Stroke(cell * 0.16f * (1f - t) + 2f),
                    )
                    repeat(8) { k ->
                        val a = (k / 8f) * 2 * PI.toFloat() + fx.key
                        val d = cell * (0.4f + 1.9f * t)
                        drawCircle(
                            col.copy(alpha = alpha),
                            radius = cell * 0.11f * (1f - t) + 1f,
                            center = Offset(c.x + cos(a) * d, c.y + sin(a) * d),
                        )
                    }
                }
            }
            if (!alive) {
                drawRect(Color(0x99000000), size = size)
                val cx = size.width / 2
                val cy = size.height / 2
                drawCircle(Color(0xFFF87171), radius = cell * 1.4f, center = Offset(cx, cy), style = Stroke(cell * 0.35f))
                drawCircle(Color(0xFFF87171), radius = cell * 0.35f, center = Offset(cx, cy))
            }
        }
        if (!alive) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (newBest) {
                        TrophySparkle(
                            resId = R.drawable.sprite_trophy,
                            size = 96.dp,
                        )
                    }
                    Text("Game over", style = MaterialTheme.typography.titleLarge)
                    Text("Score $score · Best $best")
                    if (newBest) {
                        Text("New best!", color = AccentAmber, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (alive) running = !running }) {
                Text(if (running) "Pause" else "Start")
            }
            OutlinedButton(onClick = ::reset) { Text("Restart") }
        }
        // On-screen D-pad: Up on top, Left/Down/Right in a row.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PadButton(Dir.Up, Icons.Filled.KeyboardArrowUp, "Up")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton(Dir.Left, Icons.Filled.KeyboardArrowLeft, "Left")
                PadButton(Dir.Down, Icons.Filled.KeyboardArrowDown, "Down")
                PadButton(Dir.Right, Icons.Filled.KeyboardArrowRight, "Right")
            }
        }
        Text(
            if (walls == Walls.Solid) "Swipe on the board or use the pad to steer — the green border is solid"
            else "Swipe on the board or use the pad to steer — edges wrap around",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
}
