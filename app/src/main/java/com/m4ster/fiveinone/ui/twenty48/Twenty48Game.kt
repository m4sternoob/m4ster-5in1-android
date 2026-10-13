package com.m4ster.fiveinone.ui.twenty48

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/* 2048: swipe the 4x4 board, equal tiles merge, chase the 2048 tile.
   Pure board logic lives here so it stays testable. Every tile carries
   a stable ID so the UI can animate each tile gliding from its old
   cell to its new one, pop merges, and spring in fresh spawns.
   A board is 16 cells in row-major order, null is empty.
   Game rules are unchanged — only the model gained identities. */

const val GRID_SIZE = 4
private const val CELLS = GRID_SIZE * GRID_SIZE

enum class Direction { Up, Down, Left, Right }

/* One tile: a stable identity plus its face value. */
data class Tile(val id: Long, val value: Int)

/* One tile's journey during a swipe: which tile (carrying its final
   value), from which cell (-1 = freshly spawned), to which cell.
   Absorbed tiles slide into a merge then vanish; merge results pop. */
data class TileAnim(
    val tile: Tile,
    val from: Int,
    val to: Int,
    val absorbed: Boolean,
    val isMergeResult: Boolean,
)

/* A merge worth celebrating: the cell and the resulting value,
   used for the "+N" float-ups. */
data class MergePop(val cell: Int, val value: Int)

/* One swipe's outcome: the new board (spawn already applied),
   per-tile animation info, merge events, points gained, and whether
   anything actually moved (no move, no new tile). */
data class BoardMove(
    val board: List<Tile?>,
    val anims: List<TileAnim>,
    val merges: List<MergePop>,
    val gained: Int,
    val moved: Boolean,
    val spawned: Tile?,
)

/* Legacy Int-board API kept for compatibility; delegates to tiles. */
data class MoveResult(val board: List<Int>, val gained: Int, val moved: Boolean)

private val idCounter = AtomicLong(1)

/* Fresh IDs. The counter is process-wide so IDs never collide across
   games; tests can pass their own generator for determinism. */
fun nextTileId(): Long = idCounter.getAndIncrement()

/* Fresh board: two starting tiles. */
fun newTileBoard(idGen: () -> Long = ::nextTileId): List<Tile?> {
    var b: List<Tile?> = List(CELLS) { null }
    b = spawnTile2(b, idGen).board
    b = spawnTile2(b, idGen).board
    return b
}

data class SpawnResult(val board: List<Tile?>, val tile: Tile?, val cell: Int)

/* Drop a 2 (90%) or 4 (10%) onto a random empty cell. */
fun spawnTile2(
    board: List<Tile?>,
    idGen: () -> Long = ::nextTileId,
    random: Random = Random,
): SpawnResult {
    val empty = board.indices.filter { board[it] == null }
    if (empty.isEmpty()) return SpawnResult(board, null, -1)
    val cell = empty.random(random)
    val tile = Tile(idGen(), if (random.nextFloat() < 0.9f) 2 else 4)
    val next = board.toMutableList()
    next[cell] = tile
    return SpawnResult(next, tile, cell)
}

/* Slide every tile toward the swipe direction, merging equal pairs.
   Each surviving tile keeps its ID; merge results keep the first
   tile's ID with the doubled value; absorbed tiles are flagged. */
fun moveTiles(
    board: List<Tile?>,
    dir: Direction,
    idGen: () -> Long = ::nextTileId,
): BoardMove {
    val cellOf = board.mapIndexedNotNull { i, t -> t?.let { it.id to i } }.toMap()
    val next = arrayOfNulls<Tile>(CELLS)
    val anims = mutableListOf<TileAnim>()
    val merges = mutableListOf<MergePop>()
    var gained = 0
    for (line in linesFor(dir)) {
        val tiles = line.mapNotNull { board[it] }
        val placed = mutableListOf<Tile>()
        var i = 0
        while (i < tiles.size) {
            val t = tiles[i]
            val from = cellOf[t.id] ?: -1
            if (i + 1 < tiles.size && tiles[i + 1].value == t.value) {
                val u = tiles[i + 1]
                val fromU = cellOf[u.id] ?: -1
                val merged = t.copy(value = t.value * 2)
                gained += merged.value
                val target = line[placed.size]
                anims += TileAnim(merged, from, target, absorbed = false, isMergeResult = true)
                anims += TileAnim(u, fromU, target, absorbed = true, isMergeResult = false)
                placed += merged
                merges += MergePop(target, merged.value)
                i += 2
            } else {
                val target = line[placed.size]
                anims += TileAnim(t, from, target, absorbed = false, isMergeResult = false)
                placed += t
                i++
            }
        }
        line.forEachIndexed { idx, cell -> next[cell] = placed.getOrNull(idx) }
    }
    val movedBoard: List<Tile?> = next.toList()
    val moved = movedBoard != board
    var finalBoard = movedBoard
    var spawned: Tile? = null
    val allAnims = anims.toMutableList()
    if (moved) {
        val s = spawnTile2(finalBoard, idGen)
        finalBoard = s.board
        spawned = s.tile
        if (s.tile != null) {
            allAnims += TileAnim(s.tile, -1, s.cell, absorbed = false, isMergeResult = false)
        }
    }
    return BoardMove(
        board = finalBoard,
        anims = allAnims,
        merges = merges,
        gained = gained,
        moved = moved,
        spawned = spawned,
    )
}

/* Any legal move left: an empty cell, or two equal neighbours. */
fun canMove(board: List<Tile?>): Boolean {
    if (board.any { it == null }) return true
    for (r in 0 until GRID_SIZE) {
        for (c in 0 until GRID_SIZE) {
            val v = board[r * GRID_SIZE + c]?.value ?: 0
            if (c + 1 < GRID_SIZE && board[r * GRID_SIZE + c + 1]?.value == v) return true
            if (r + 1 < GRID_SIZE && board[(r + 1) * GRID_SIZE + c]?.value == v) return true
        }
    }
    return false
}

/* Hint: the move leaving the most open space, ties broken by score.
   A fuller board after the move is a worse board. Null when stuck. */
fun suggestMove(board: List<Tile?>, idGen: () -> Long = ::nextTileId): Direction? {
    var best: Direction? = null
    var bestRank = Int.MIN_VALUE
    for (dir in Direction.values()) {
        val result = moveTiles(board, dir, idGen)
        if (!result.moved) continue
        val rank = result.board.count { it == null } * 100 + result.gained
        if (rank > bestRank) {
            bestRank = rank
            best = dir
        }
    }
    return best
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

/* ---- Legacy Int-board wrappers (unchanged behavior) ---- */

fun newBoard(): List<Int> = newTileBoard().map { it?.value ?: 0 }

fun move(board: List<Int>, dir: Direction): MoveResult {
    val tiles = board.map { v -> if (v == 0) null else Tile(nextTileId(), v) }
    val r = moveTiles(tiles, dir)
    return MoveResult(r.board.map { it?.value ?: 0 }, r.gained, r.moved)
}

fun spawnTile(board: List<Int>, random: Random = Random): List<Int> {
    val tiles = board.map { v -> if (v == 0) null else Tile(nextTileId(), v) }
    return spawnTile2(tiles, ::nextTileId, random).board.map { it?.value ?: 0 }
}

fun canMove(board: List<Int>): Boolean =
    canMove(board.map { v -> if (v == 0) null else Tile(-1, v) })

fun suggestMove(board: List<Int>): Direction? =
    suggestMove(board.map { v -> if (v == 0) null else Tile(nextTileId(), v) })
