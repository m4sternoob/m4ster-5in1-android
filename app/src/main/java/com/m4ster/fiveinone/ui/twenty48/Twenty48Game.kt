package com.m4ster.fiveinone.ui.twenty48

import kotlin.random.Random

/* 2048: swipe the 4x4 board, equal tiles merge, chase the 2048 tile.
   Pure board logic lives here so it stays testable; the screen owns
   the UI state. A board is 16 ints in row-major order, 0 is empty. */

const val GRID_SIZE = 4
private const val CELLS = GRID_SIZE * GRID_SIZE

enum class Direction { Up, Down, Left, Right }

/* One swipe's outcome: the new board, the score gained from merges,
   and whether anything actually moved (no move, no new tile). */
data class MoveResult(val board: List<Int>, val gained: Int, val moved: Boolean)

/* Fresh board: two starting tiles. */
fun newBoard(): List<Int> = spawnTile(spawnTile(List(CELLS) { 0 }))

fun move(board: List<Int>, dir: Direction): MoveResult {
    val next = board.toMutableList()
    var gained = 0
    for (line in linesFor(dir)) {
        val (merged, lineGained) = mergeLine(line.map { board[it] })
        gained += lineGained
        line.forEachIndexed { i, cell -> next[cell] = merged[i] }
    }
    return MoveResult(next, gained, next != board)
}

/* The four cell-index lines in swipe order for a direction. Merging
   always pulls toward the front of each line. */
private fun linesFor(dir: Direction): List<List<Int>> = when (dir) {
    Direction.Left -> rows()
    Direction.Right -> rows().map { it.reversed() }
    Direction.Up -> cols()
    Direction.Down -> cols().map { it.reversed() }
}

private fun rows(): List<List<Int>> =
    (0 until GRID_SIZE).map { r -> (0 until GRID_SIZE).map { c -> r * GRID_SIZE + c } }

private fun cols(): List<List<Int>> =
    (0 until GRID_SIZE).map { c -> (0 until GRID_SIZE).map { r -> r * GRID_SIZE + c } }

/* Slide one line toward its front: compact, merge each pair once,
   compact again, pad with empties. Returns the line and points gained. */
private fun mergeLine(line: List<Int>): Pair<List<Int>, Int> {
    val tiles = line.filter { it != 0 }.toMutableList()
    var gained = 0
    var i = 0
    while (i < tiles.size - 1) {
        if (tiles[i] == tiles[i + 1]) {
            tiles[i] *= 2
            gained += tiles[i]
            tiles.removeAt(i + 1)
        }
        i++
    }
    while (tiles.size < GRID_SIZE) tiles += 0
    return tiles to gained
}

/* Drop a 2 (90%) or 4 (10%) onto a random empty cell. */
fun spawnTile(board: List<Int>, random: Random = Random): List<Int> {
    val empty = board.indices.filter { board[it] == 0 }
    if (empty.isEmpty()) return board
    val next = board.toMutableList()
    next[empty.random(random)] = if (random.nextFloat() < 0.9f) 2 else 4
    return next
}

/* Any legal move left: an empty cell, or two equal neighbours. */
fun canMove(board: List<Int>): Boolean {
    if (board.any { it == 0 }) return true
    for (r in 0 until GRID_SIZE) {
        for (c in 0 until GRID_SIZE) {
            val v = board[r * GRID_SIZE + c]
            if (c + 1 < GRID_SIZE && board[r * GRID_SIZE + c + 1] == v) return true
            if (r + 1 < GRID_SIZE && board[(r + 1) * GRID_SIZE + c] == v) return true
        }
    }
    return false
}

/* Hint: the move leaving the most open space, ties broken by score.
   A fuller board after the move is a worse board. Null when stuck. */
fun suggestMove(board: List<Int>): Direction? {
    var best: Direction? = null
    var bestRank = Int.MIN_VALUE
    for (dir in Direction.values()) {
        val result = move(board, dir)
        if (!result.moved) continue
        val rank = result.board.count { it == 0 } * 100 + result.gained
        if (rank > bestRank) {
            bestRank = rank
            best = dir
        }
    }
    return best
}
