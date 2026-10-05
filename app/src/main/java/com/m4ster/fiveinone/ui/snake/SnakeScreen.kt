package com.m4ster.fiveinone.ui.snake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m4ster.fiveinone.ui.components.Celebration
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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

@Composable
fun SnakeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val best by StatsStore.snakeBest(context).collectAsState(initial = 0)

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

    // Food pulse and the "+1" pop are pure UI animation.
    val pulse by rememberInfiniteTransition(label = "food").animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
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

    Column(
        modifier = modifier
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
            // Golden food: worth 3× combo, blinks before it despawns.
            golden?.let { gp ->
                val gc = Offset(origin.x + gp.x * cell + cell / 2, origin.y + gp.y * cell + cell / 2)
                val alpha = if (goldenTtl < 12 && goldenTtl % 2 == 0) 0.35f else 1f
                drawCircle(Color(0xFFFBBF24).copy(alpha = alpha), radius = cell * 0.42f * pulse, center = gc)
                drawCircle(Color.White.copy(alpha = alpha), radius = cell * 0.16f, center = gc)
            }
            // Gradient body, bright head.
            val n = snake.size
            snake.forEachIndexed { i, p ->
                val f = if (n <= 1) 0f else i / (n - 1).toFloat()
                val color = if (i == 0) Color(0xFFEF4444)
                    else lerp(Color(0xFF4ADE80), Color(0xFF166534), f)
                drawRoundRect(
                    color = color,
                    topLeft = px(p),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.28f),
                )
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
