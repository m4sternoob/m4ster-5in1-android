package com.m4ster.fiveinone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.m4ster.fiveinone.BuildConfig
import com.m4ster.fiveinone.ui.stats.StatsStore
import kotlinx.coroutines.launch

/* Settings: one screen, two jobs — wipe local stats, and say what
   the app is (offline, no accounts, no ads). */

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.titleLarge)
        Text(
            "Wins, streaks, and high scores live on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            if (confirmReset) {
                scope.launch {
                    StatsStore.clearAll(context)
                    confirmReset = false
                    resetDone = true
                }
            } else {
                confirmReset = true
            }
        }) {
            Text(if (confirmReset) "Tap again to confirm" else "Reset stats")
        }
        if (resetDone) {
            Text("Stats cleared.", color = MaterialTheme.colorScheme.primary)
        }
        Text("About", style = MaterialTheme.typography.titleLarge)
        Text(
            "5IN1 v${BuildConfig.VERSION_NAME} — five games, fully offline.\nNo accounts, no ads, no tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
