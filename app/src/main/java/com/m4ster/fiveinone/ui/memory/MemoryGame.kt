package com.m4ster.fiveinone.ui.memory

/* Memory Match deck: shuffled pairs of icon ids, plus the grid sizes.
   The screen owns the UI state; this just builds decks. */

enum class MemorySize(val cols: Int, val pairs: Int, val label: String) {
    Easy(4, 6, "Easy"),
    Medium(4, 8, "Medium"),
    Hard(6, 12, "Hard"),
}

/* Each icon id 0 until pairs appears exactly twice, shuffled. */
fun newDeck(pairs: Int): List<Int> =
    ((0 until pairs) + (0 until pairs)).shuffled()
