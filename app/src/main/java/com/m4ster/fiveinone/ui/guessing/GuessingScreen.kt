package com.m4ster.fiveinone.ui.guessing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4ster.fiveinone.BuildConfig
import com.m4ster.fiveinone.ui.stats.GuessStats
import com.m4ster.fiveinone.ui.stats.StatsStore
import com.m4ster.fiveinone.ui.theme.AccentBlue
import com.m4ster.fiveinone.ui.theme.AccentRed
import kotlinx.coroutines.launch
import kotlin.math.abs

/* Number guessing with teeth: three difficulties with attempt limits, a
   custom digit keypad, an animated hot/cold meter, and persisted stats.
   The Hint button serves cryptic wordplay hints from HintEngine:
   3 per round, never naming the number outright. */

private const val MAX_HINTS = 3

private enum class Difficulty(val label: String, val max: Int, val maxAttempts: Int) {
    Easy("Easy", 50, 10),
    Medium("Medium", 100, 8),
    Hard("Hard", 200, 12),
}

private fun heatHint(distance: Int): String = when {
    distance == 0 -> "Correct!"
    distance <= 3 -> "Burning hot!"
    distance <= 8 -> "Hot"
    distance <= 15 -> "Warm"
    distance <= 30 -> "Cold"
    else -> "Ice cold"
}

@Composable
fun GuessingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by StatsStore.guessStats(context).collectAsState(initial = GuessStats(0, 0, 0))

    var difficulty by remember { mutableStateOf(Difficulty.Medium) }
    var target by remember { mutableStateOf((1..difficulty.max).random()) }
    var input by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var guesses by remember { mutableStateOf(listOf<Int>()) }
    var shownHints by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("I'm thinking of a number between 1 and ${difficulty.max}.") }
    var won by remember { mutableStateOf(false) }
    var lost by remember { mutableStateOf(false) }
    val over = won || lost
    val hintsLeft = MAX_HINTS - shownHints.size

    fun newGame(diff: Difficulty = difficulty) {
        difficulty = diff
        target = (1..diff.max).random()
        input = ""
        attempts = 0
        guesses = listOf()
        shownHints = listOf()
        won = false
        lost = false
        message = "I'm thinking of a number between 1 and ${diff.max}."
    }

    fun submit() {
        if (over) return
        val guess = input.toIntOrNull() ?: return
        attempts++
        guesses = guesses + guess
        input = ""
        val d = abs(guess - target)
        if (d == 0) {
            won = true
            message = if (attempts == 1) "You got it in 1 try!" else "You got it in $attempts tries!"
            scope.launch { StatsStore.recordGuessWin(context, attempts) }
        } else if (attempts >= difficulty.maxAttempts) {
            lost = true
            message = "Out of attempts — it was $target."
            scope.launch { StatsStore.recordGuessLoss(context) }
        } else {
            message = if (guess < target) "Higher — ${heatHint(d)}" else "Lower — ${heatHint(d)}"
        }
    }

    fun giveUp() {
        if (over) return
        lost = true
        message = "It was $target. Better luck next round."
        scope.launch { StatsStore.recordGuessLoss(context) }
    }

    fun askHint() {
        if (over || hintsLeft <= 0) return
        val next = HintEngine.hintsFor(target, guesses).firstOrNull { it !in shownHints }
        if (next != null) shownHints = shownHints + next
    }

    fun pressKey(key: String) {
        if (over) return
        when (key) {
            "back" -> input = input.dropLast(1)
            "go" -> submit()
            else -> {
                val next = input + key
                val n = next.toIntOrNull() ?: return
                if (n in 1..difficulty.max && next.length <= 3) input = next
            }
        }
    }

    // Hot/cold meter: 0 = ice cold, 1 = on the number. Animates on each guess.
    val lastDistance = guesses.lastOrNull()?.let { abs(it - target) }
    val heatTarget = if (lastDistance == null) 0f
        else (1f - lastDistance / difficulty.max.toFloat()).coerceIn(0f, 1f)
    val heat by animateFloatAsState(heatTarget, label = "heat")
    val heatColor = lerp(AccentBlue, AccentRed, heat)

    @Composable
    fun Keypad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "back", "0", "go")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..3) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val k = keys[r * 3 + c]
                        OutlinedButton(
                            onClick = { pressKey(k) },
                            modifier = Modifier.size(88.dp, 54.dp),
                        ) {
                            when (k) {
                                "back" -> Icon(Icons.Filled.Backspace, contentDescription = "Backspace")
                                "go" -> Icon(Icons.Filled.Check, contentDescription = "Guess")
                                else -> Text(k, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val bestLabel = if (stats.best == 0) "–" else "${stats.best}"
        Text(
            "Wins ${stats.wins} · Best $bestLabel · Streak ${stats.streak}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { d ->
                FilterChip(
                    selected = d == difficulty,
                    onClick = { newGame(d) },
                    label = { Text(d.label) },
                )
            }
        }
        Text(
            "Attempts: $attempts/${difficulty.maxAttempts}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Text(
            input.ifEmpty { "–" },
            fontSize = 40.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        if (lastDistance != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Cold", style = MaterialTheme.typography.labelSmall)
                    Text(heatHint(lastDistance), style = MaterialTheme.typography.labelSmall)
                    Text("Hot", style = MaterialTheme.typography.labelSmall)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(heat)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(heatColor),
                    )
                }
            }
        }
        Keypad()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The noHints flavor leaves the game pure - no hint button at all.
            if (BuildConfig.HINTS_ENABLED) {
                OutlinedButton(onClick = ::askHint, enabled = !over && hintsLeft > 0) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null)
                    Text("Hint ($hintsLeft)")
                }
            }
            OutlinedButton(onClick = ::giveUp, enabled = !over) { Text("Give up") }
            OutlinedButton(onClick = { newGame() }) { Text("New game") }
        }
        AnimatedVisibility(
            visible = shownHints.isNotEmpty(),
            enter = slideInVertically { it / 2 } + fadeIn(),
        ) {
            shownHints.lastOrNull()?.let { hint ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(hint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
