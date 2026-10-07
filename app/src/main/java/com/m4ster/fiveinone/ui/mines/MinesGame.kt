package com.m4ster.fiveinone.ui.mines

import kotlin.math.abs
import kotlin.random.Random

/* Minesweeper: tap to reveal, long-press to flag. Pure board logic
   lives here; the screen owns the UI state. Cells are indexed
   row-major; a board is a flat list of MineCells. */

enum class MineDifficulty(val label: String, val rows: Int, val cols: Int, val mines: Int) {
    Easy("Easy", 8, 8, 10),
    Medium("Medium", 10, 10, 20),
    Hard("Hard", 12, 12, 32),
}

data class MineCell(val mined: Boolean, val adjacent: Int)

/* Fresh minefield: mines stay clear of the first-tapped cell and its
   neighbours, so the opening tap always reveals breathing room. */
fun newMinefield(d: MineDifficulty, safeCell: Int, random: Random = Random): List<MineCell> {
    val cells = d.rows * d.cols
    val safeZone = (0 until cells).filter { it == safeCell || touches(it, safeCell, d.cols) }.toSet()
    val spots = (0 until cells)
        .filter { it !in safeZone }
        .shuffled(random)
        .take(d.mines)
        .toSet()
    return (0 until cells).map { i ->
        val mined = i in spots
        MineCell(mined, if (mined) 0 else spots.count { touches(i, it, d.cols) })
    }
}

/* Chebyshev neighbours: the eight surrounding cells, not self. */
private fun touches(a: Int, b: Int, cols: Int): Boolean {
    if (a == b) return false
    return abs(a / cols - b / cols) <= 1 && abs(a % cols - b % cols) <= 1
}

/* Reveal cell i. A numbered cell opens alone; an empty cell
   flood-fills through neighbouring empties. Hitting a mine just
   adds it to the revealed set — the screen handles the boom. */
fun reveal(d: MineDifficulty, board: List<MineCell>, revealed: Set<Int>, i: Int): Set<Int> {
    if (board[i].mined) return revealed + i
    val open = revealed.toMutableSet()
    val stack = ArrayDeque(listOf(i))
    while (stack.isNotEmpty()) {
        val cell = stack.removeLast()
        if (!open.add(cell)) continue
        if (board[cell].adjacent == 0 && !board[cell].mined) {
            val r = cell / d.cols
            val c = cell % d.cols
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val r2 = r + dr
                    val c2 = c + dc
                    if (r2 in 0 until d.rows && c2 in 0 until d.cols) {
                        val n = r2 * d.cols + c2
                        if (n !in open) stack.add(n)
                    }
                }
            }
        }
    }
    return open
}
