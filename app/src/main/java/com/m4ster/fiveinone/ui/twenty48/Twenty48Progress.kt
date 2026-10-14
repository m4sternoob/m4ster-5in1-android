package com.m4ster.fiveinone.ui.twenty48

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* 2048 progression: milestone unlocks, theme collection, daily streak.
   Pure SharedPreferences — tiny, synchronous, no new dependencies.
   Unlocking is one-way; themes auto-equip when earned. */

class Twenty48Progress(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("twenty48_progress", Context.MODE_PRIVATE)

    val unlockedIds: Set<String>
        get() = prefs.getStringSet(KEY_UNLOCKED, setOf("classic")) ?: setOf("classic")

    var equippedId: String
        get() = prefs.getString(KEY_THEME, "classic") ?: "classic"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var mode: GameMode
        get() = if (prefs.getString(KEY_MODE, "easy") == "hard") GameMode.Hard else GameMode.Easy
        set(v) = prefs.edit()
            .putString(KEY_MODE, if (v == GameMode.Hard) "hard" else "easy").apply()

    val highestTile: Int get() = prefs.getInt(KEY_HIGHEST, 0)
    val gamesPlayed: Int get() = prefs.getInt(KEY_GAMES, 0)
    val streakDays: Int get() = prefs.getInt(KEY_STREAK, 0)

    fun isUnlocked(themeId: String): Boolean = themeId in unlockedIds

    /* Returns the newly unlocked theme, or null if it was already owned.
       The reward auto-equips so the player feels it immediately. */
    fun unlockMilestone(value: Int): TileTheme? {
        val theme = themeForMilestone(value) ?: return null
        if (theme.id in unlockedIds) return null
        prefs.edit().putStringSet(KEY_UNLOCKED, unlockedIds + theme.id).apply()
        equippedId = theme.id
        return theme
    }

    fun recordTile(value: Int) {
        if (value > highestTile) prefs.edit().putInt(KEY_HIGHEST, value).apply()
    }

    /* Bump games-played and maintain the daily streak. Returns the
       current streak for display. */
    fun recordGameStart(): Int {
        prefs.edit().putInt(KEY_GAMES, gamesPlayed + 1).apply()
        val today = dayStr(0)
        val last = prefs.getString(KEY_STREAK_DATE, null)
        val streak = when (last) {
            today -> streakDays
            dayStr(-1) -> streakDays + 1
            else -> 1
        }
        prefs.edit().putInt(KEY_STREAK, streak).putString(KEY_STREAK_DATE, today).apply()
        return streak
    }

    private fun dayStr(offsetDays: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(c.timeInMillis))
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_THEME = "theme"
        const val KEY_MODE = "mode"
        const val KEY_HIGHEST = "highest"
        const val KEY_GAMES = "games"
        const val KEY_STREAK = "streak"
        const val KEY_STREAK_DATE = "streak_date"
    }
}
