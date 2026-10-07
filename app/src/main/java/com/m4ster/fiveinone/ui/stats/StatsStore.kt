package com.m4ster.fiveinone.ui.stats

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/* Tiny persisted stats: guessing wins/best/streak, the snake high
   score, the memory fewest-moves best, and the 2048 best score.
   DataStore, read as Flows, written from a coroutine scope. */

val Context.fiveInOneDataStore by preferencesDataStore("fiveinone")

data class GuessStats(val wins: Int, val best: Int, val streak: Int)

object StatsStore {
    private val GUESS_WINS = intPreferencesKey("guess_wins")
    private val GUESS_BEST = intPreferencesKey("guess_best")
    private val GUESS_STREAK = intPreferencesKey("guess_streak")
    private val SNAKE_BEST = intPreferencesKey("snake_best")
    private val MEMORY_BEST = intPreferencesKey("memory_best")
    private val TWENTY48_BEST = intPreferencesKey("twenty48_best")

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

    suspend fun clearAll(context: Context) {
        context.fiveInOneDataStore.edit { it.clear() }
    }

    fun snakeBest(context: Context): Flow<Int> =
        context.fiveInOneDataStore.data.map { p -> p[SNAKE_BEST] ?: 0 }

    suspend fun recordSnakeScore(context: Context, score: Int) {
        context.fiveInOneDataStore.edit { p ->
            if (score > (p[SNAKE_BEST] ?: 0)) p[SNAKE_BEST] = score
        }
    }

    fun memoryBest(context: Context): Flow<Int> =
        context.fiveInOneDataStore.data.map { p -> p[MEMORY_BEST] ?: 0 }

    suspend fun recordMemoryMoves(context: Context, moves: Int) {
        context.fiveInOneDataStore.edit { p ->
            val best = p[MEMORY_BEST] ?: 0
            if (best == 0 || moves < best) p[MEMORY_BEST] = moves
        }
    }

    fun twenty48Best(context: Context): Flow<Int> =
        context.fiveInOneDataStore.data.map { p -> p[TWENTY48_BEST] ?: 0 }

    suspend fun recordTwenty48Score(context: Context, score: Int) {
        context.fiveInOneDataStore.edit { p ->
            if (score > (p[TWENTY48_BEST] ?: 0)) p[TWENTY48_BEST] = score
        }
    }
}
