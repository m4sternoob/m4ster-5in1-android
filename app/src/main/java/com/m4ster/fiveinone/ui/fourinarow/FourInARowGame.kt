package com.m4ster.fiveinone.ui.fourinarow

import kotlin.math.abs

/* Connect Four on a 7-wide, 6-tall board: tap a column to drop your
   disc, first to line up four wins. Pure logic; the screen owns the
   Compose state. Row 0 is the top, so discs land at the lowest empty row. */

const val FOUR_COLS = 7
const val FOUR_ROWS = 6

const val PLAYER = 'X'
const val CPU = 'O'
private const val EMPTY = ' '

fun newFourBoard(): CharArray = CharArray(FOUR_COLS * FOUR_ROWS) { EMPTY }

/* Lowest empty row in a column, or -1 when the column is full. */
fun dropRow(board: CharArray, col: Int): Int {
    for (row in FOUR_ROWS - 1 downTo 0) {
        if (board[row * FOUR_COLS + col] == EMPTY) return row
    }
    return -1
}

/* Board copy with mark dropped into the column, or null when full. */
fun dropDisc(board: CharArray, col: Int, mark: Char): CharArray? {
    val row = dropRow(board, col)
    if (row < 0) return null
    return board.copyOf().also { it[row * FOUR_COLS + col] = mark }
}

fun other(mark: Char) = if (mark == PLAYER) CPU else PLAYER

/* The four winning discs, or null. Checks right, down, and both
   diagonals from every occupied cell. */
fun winningLineOf(board: CharArray): IntArray? {
    for (r in 0 until FOUR_ROWS) {
        for (c in 0 until FOUR_COLS) {
            val i = r * FOUR_COLS + c
            val mark = board[i]
            if (mark == EMPTY) continue
            if (c + 3 < FOUR_COLS && (1..3).all { board[i + it] == mark })
                return intArrayOf(i, i + 1, i + 2, i + 3)
            if (r + 3 < FOUR_ROWS && (1..3).all { board[i + it * FOUR_COLS] == mark })
                return intArrayOf(i, i + FOUR_COLS, i + 2 * FOUR_COLS, i + 3 * FOUR_COLS)
            if (c + 3 < FOUR_COLS && r + 3 < FOUR_ROWS &&
                (1..3).all { board[i + it * (FOUR_COLS + 1)] == mark })
                return intArrayOf(i, i + FOUR_COLS + 1, i + 2 * (FOUR_COLS + 1), i + 3 * (FOUR_COLS + 1))
            if (c - 3 >= 0 && r + 3 < FOUR_ROWS &&
                (1..3).all { board[i + it * (FOUR_COLS - 1)] == mark })
                return intArrayOf(i, i + FOUR_COLS - 1, i + 2 * (FOUR_COLS - 1), i + 3 * (FOUR_COLS - 1))
        }
    }
    return null
}

fun fourBoardFull(board: CharArray): Boolean = EMPTY !in board

/* A column that completes four for mark right now, or -1. */
fun winningMove(board: CharArray, mark: Char): Int {
    for (col in 0 until FOUR_COLS) {
        dropDisc(board, col, mark)?.let { if (winningLineOf(it) != null) return col }
    }
    return -1
}

/* The column the brain plays for mark: takes the win, blocks the
   opponent's win, otherwise prefers the center columns. */
fun suggestColumn(board: CharArray, mark: Char): Int {
    val open = (0 until FOUR_COLS).filter { dropRow(board, it) >= 0 }
    if (open.isEmpty()) return -1
    val foe = other(mark)
    winningMove(board, mark).takeIf { it >= 0 }?.let { return it }
    winningMove(board, foe).takeIf { it >= 0 }?.let { return it }
    return open.minBy { abs(it - FOUR_COLS / 2) }
}

/* Simple CPU: Easy drops anywhere, Hard plays the brain above. */
fun pickCpuMove(board: CharArray, hard: Boolean): Int {
    if (!hard) {
        val open = (0 until FOUR_COLS).filter { dropRow(board, it) >= 0 }
        return open.randomOrNull() ?: -1
    }
    return suggestColumn(board, CPU)
}
