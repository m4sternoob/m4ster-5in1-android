package com.m4ster.fiveinone.ui.guessing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/* Classic 1–100 guessing. Distance bands drive the hot/cold hint. */

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
    var target by remember { mutableStateOf((1..100).random()) }
    var text by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("I'm thinking of a number between 1 and 100.") }
    var won by remember { mutableStateOf(false) }

    fun newGame() {
        target = (1..100).random()
        text = ""
        attempts = 0
        won = false
        message = "I'm thinking of a number between 1 and 100."
    }

    fun submit() {
        if (won) return
        val guess = text.toIntOrNull()
        if (guess == null || guess !in 1..100) {
            message = "Enter a number from 1 to 100."
            return
        }
        attempts++
        val d = abs(guess - target)
        if (d == 0) {
            won = true
            message = "You got it in $attempts tries!"
        } else {
            message = if (guess < target) "Higher — ${heatHint(d)}" else "Lower — ${heatHint(d)}"
            text = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Attempts: $attempts", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) text = it },
            label = { Text("Your guess") },
            singleLine = true,
            enabled = !won,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = ::submit, enabled = !won) { Text("Guess") }
            OutlinedButton(onClick = ::newGame) { Text("New game") }
        }
    }
}
