package galkon.common

import kotlin.random.Random

// ---- Seed encoding (Long ↔ A-Z0-9 base-36 string) ----

private const val SEED_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val BASE = 36

/** Encode a Long seed to a human-readable A-Z0-9 string. */
fun seedToString(seed: Long): String {
    if (seed == 0L) return "0"
    var n = if (seed < 0) seed.toULong() else seed.toULong()
    val chars = mutableListOf<Char>()
    while (n > 0u) {
        chars.add(SEED_ALPHABET[(n % BASE.toUInt()).toInt()])
        n /= BASE.toUInt()
    }
    return chars.reversed().joinToString("")
}

/** Decode a human-readable A-Z0-9 string back to a Long seed. */
fun stringToSeed(s: String): Long {
    var n = 0uL
    for (c in s.uppercase()) {
        val digit = SEED_ALPHABET.indexOf(c)
        require(digit >= 0) { "Invalid seed character: $c" }
        n = n * BASE.toUInt() + digit.toUInt()
    }
    return n.toLong()
}