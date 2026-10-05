package com.m4ster.fiveinone.ui.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/* Canvas snake. A coroutine loop steps the game; swipe or the on-screen
   D-pad steers. Walls are solid (green frame), the head is red, and the
   pace quickens every 5 dots. Immutable snapshots (new lists each step)
   keep recomposition predictable. */

private const val COLS = 20
private const val ROWS = 20
private const val BASE_STEP_MS = 170L
private const val MIN_STEP_MS = 70L
private const val DOTS_PER_LEVEL = 5
private const val SPEEDUP_PER_LEVEL_MS = 15L

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
    var snake by remember { mutableStateOf(listOf(Pos(10, 10), Pos(9, 10), Pos(8, 10))) }
    var dir by remember { mutableStateOf(Dir.Right) }
    var food by remember { mutableStateOf(Pos(14, 10)) }
    var score by remember { mutableStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }

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
        food = randomFood(fresh)
        running = true
    }

    fun step() {
        val head = snake.first()
        val next = Pos(head.x + dir.dx, head.y + dir.dy)
        val hitWall = next.x !in 0 until COLS || next.y !in 0 until ROWS
        if (hitWall || next in snake) {
            alive = false
            running = false
            return
        }
        val ate = next == food
        if (ate) {
            score++
            food = randomFood(snake + next)
        }
        snake = listOf(next) + if (ate) snake else snake.dropLast(1)
    }

    // Game loop: restarts whenever `running` toggles; exits when it goes false.
    // Pace quickens as the score climbs — read live each step.
    LaunchedEffect(running) {
        while (running) {
            delay(stepMsFor(score))
            step()
        }
    }

    // Shared steering rule for swipe and D-pad: no 180° turns,
    // they'd kill the snake instantly.
    fun steer(want: Dir) {
        if (want != dir.opposite()) dir = want
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
        val level = score / DOTS_PER_LEVEL + 1
        Text("Score: $score · Level $level", style = MaterialTheme.typography.headlineSmall)
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
            // Green frame = solid walls. Board sits inset; everything
            // draws in board coordinates.
            drawRect(Color(0xFF16A34A), size = size)
            val bw = size.width * 0.02f
            val board = Size(size.width - 2 * bw, size.height - 2 * bw)
            val origin = Offset(bw, bw)
            drawRect(Color(0xFF0B1220), topLeft = origin, size = board)
            val cell = board.width / COLS
            fun px(p: Pos) = Offset(origin.x + p.x * cell, origin.y + p.y * cell)
            drawRect(
                color = Color(0xFFF87171),
                topLeft = px(food),
                size = Size(cell, cell),
            )
            snake.forEachIndexed { i, p ->
                drawRect(
                    color = if (i == 0) Color(0xFFEF4444) else Color(0xFF22C55E),
                    topLeft = px(p),
                    size = Size(cell, cell),
                )
            }
            if (!alive) drawRect(Color(0x99000000), size = size)
        }
        if (!alive) {
            Text("Game over — tap Restart", color = MaterialTheme.colorScheme.error)
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
            "Swipe on the board or use the pad to steer — the green border is solid",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
