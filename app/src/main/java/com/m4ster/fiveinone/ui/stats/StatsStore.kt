package com.m4ster.fiveinone.ui.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/* Tiny persisted stats: guessing wins/best/streak and the snake high
   score. DataStore, read as Flows, written from a coroutine scope. */

val Context.fiveInOneDataStore by preferencesDataStore("fiveinone")

data class GuessStats(val wins: Int, val best: Int, val streak: Int)

object StatsStore {
    private val GUESS_WINS = intPreferencesKey("guess_wins")
    private val GUESS_BEST = intPreferencesKey("guess_best")
    private val GUESS_STREAK = intPreferencesKey("guess_streak")
    private val SNAKE_BEST = intPreferencesKey("snake_best")

    fun guessStats(context: Context): Flow<GuessStats> =
        context.fiveInOneDataStore.data.map { p ->
            GuessStats(
                wins = p[GUESS_WINS] ?: 0,
                best = p[GUESS_BEST] ?: 0,
                streak = p[GUESS_STREAK] ?: 0,
            )
        }

    suspend fun recordGuessWin(context: Context, attempts: Int) {
        context.fiveInOneDataStore.edit { p ->
            p[GUESS_WINS] = (p[GUESS_WINS] ?: 0) + 1
            val best = p[GUESS_BEST] ?: 0
            p[GUESS_BEST] = if (best == 0) attempts else minOf(best, attempts)
            p[GUESS_STREAK] = (p[GUESS_STREAK] ?: 0) + 1
        }
    }

    suspend fun recordGuessLoss(context: Context) {
        context.fiveInOneDataStore.edit { p ->
            p[GUESS_STREAK] = 0
        }
    }

    fun snakeBest(context: Context): Flow<Int> =
        context.fiveInOneDataStore.data.map { p -> p[SNAKE_BEST] ?: 0 }

    suspend fun recordSnakeScore(context: Context, score: Int) {
        context.fiveInOneDataStore.edit { p ->
            if (score > (p[SNAKE_BEST] ?: 0)) p[SNAKE_BEST] = score
        }
    }
}
