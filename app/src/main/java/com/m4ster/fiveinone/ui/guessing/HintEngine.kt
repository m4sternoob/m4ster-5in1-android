package com.m4ster.fiveinone.ui.guessing

/**
 * Playful cryptic hints for the number guessing game.
 *
 * Hints never state the number outright. Instead they describe it with
 * wordplay ("a zero stacked on top of another zero"), fun facts
 * ("say hi to my Valentine!") and little math riddles.
 */
object HintEngine {

    /** What each digit looks like when you squint at it. Two variants each
     *  so hints feel fresh across rounds. */
    private val digitLooks = mapOf(
        0 to listOf("a round egg", "a cheerio"),
        1 to listOf("a single straight stick", "a candle"),
        2 to listOf("a graceful swan", "a curly wave"),
        3 to listOf("two half-hearts stacked together", "a pair of lips turned sideways"),
        4 to listOf("an open box", "a little flag on a pole"),
        5 to listOf("a round belly wearing a flat hat", "a seahorse"),
        6 to listOf("a zero with a curly tail", "a hook"),
        7 to listOf("a bent corner, like an elbow", "a cliff edge"),
        8 to listOf("a zero stacked on top of another zero", "a snowman"),
        9 to listOf("a balloon on a straight string", "a zero with a stick leg"),
    )

    /** Riddle-style associations for well-known numbers. */
    private val funFacts = mapOf(
        1 to "The loneliest number (ask Three Dog Night).",
        2 to "A pair — like twins, or shoes.",
        3 to "The Three Musketeers.",
        4 to "Seasons in a year.",
        5 to "Fingers on one hand.",
        6 to "Sides of a dice.",
        7 to "Lucky number — and days in a week.",
        9 to "Lives of a cat.",
        10 to "Fingers on both hands.",
        11 to "Players of a football team on the pitch.",
        12 to "Eggs in a dozen.",
        13 to "The unlucky seat at the table.",
        14 to "Say hi to my Valentine! (Cupid's big day.)",
        15 to "Minutes in a quarter of an hour.",
        16 to "Sweet sixteen.",
        18 to "The age you can finally vote.",
        20 to "Fingers and toes, all together.",
        21 to "Blackjack!",
        22 to "Two ducks, quack quack (a bingo call).",
        24 to "Hours in a day.",
        25 to "A quarter of a hundred.",
        26 to "Letters in the alphabet.",
        30 to "Days in an average month.",
        33 to "A vinyl LP spins at this speed.",
        40 to "Forty days and forty nights.",
        42 to "The answer to life, the universe and everything.",
        50 to "Half a century.",
        52 to "Cards in a deck (jokers excluded).",
        60 to "Seconds in a minute.",
        64 to "Squares on a chessboard.",
        77 to "Double sevens — a slot machine jackpot.",
        88 to "Two fat ladies (a bingo call).",
        90 to "Degrees in a right angle.",
        100 to "A full century.",
    )

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n % 2 == 0) return n == 2
        var i = 3
        while (i * i <= n) {
            if (n % i == 0) return false
            i += 2
        }
        return true
    }

    private fun isSquare(n: Int): Boolean {
        val r = kotlin.math.sqrt(n.toDouble()).toInt()
        return r * r == n
    }

    /** Describes the digits visually, e.g. 8 -> "a zero stacked on top of
     *  another zero". */
    private fun digitHint(n: Int): String {
        val digits = n.toString().map { it.digitToInt() }
        val looks = digits.map { d -> digitLooks.getValue(d).random() }
        return when (digits.size) {
            1 -> "Squint at it: it looks like ${looks[0]}."
            2 -> "Picture the digits: the first looks like ${looks[0]}, the second like ${looks[1]}."
            else -> "Picture the digits, one by one: ${looks.joinToString("; ")}."
        }
    }

    /** Little math riddles that never name the number. */
    private fun mathHint(n: Int): String {
        val parts = mutableListOf<String>()
        parts += if (n % 2 == 0) "It's even." else "It's odd."
        val divisor = listOf(3, 5, 7, 11).firstOrNull { n % it == 0 }
        if (divisor != null) parts += "It's divisible by $divisor."
        when {
            isSquare(n) -> parts += "It's a square number — some whole number times itself."
            isPrime(n) -> parts += "It's prime — only 1 and itself divide it."
        }
        val sum = n.toString().sumOf { it.digitToInt() }
        parts += "Its digits add up to $sum."
        val digits = n.toString()
        if (digits.length == 2 && digits[0] == digits[1]) parts += "Both its digits are twins."
        return parts.take(2).joinToString(" ")
    }

    /**
     * All hint candidates for this round, fun-first. The caller should
     * hand them out one at a time, skipping ones already shown.
     */
    fun hintsFor(target: Int, guesses: List<Int>): List<String> {
        val out = mutableListOf<String>()
        funFacts[target]?.let { out += "Fun fact: $it" }
        out += digitHint(target)
        out += mathHint(target)
        val wrong = guesses.filter { it != target }
        if (wrong.isNotEmpty()) {
            val lo = wrong.filter { it < target }.maxOrNull() ?: 1
            val hi = wrong.filter { it > target }.minOrNull() ?: 100
            if (lo < hi) out += "From your guesses so far: it's between $lo and $hi."
        }
        return out.distinct()
    }
}
