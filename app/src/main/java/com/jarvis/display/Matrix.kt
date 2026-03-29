package com.jarvis.display

/**
 * Represents a display matrix — a 2D grid of characters to show on the flip board.
 *
 * Display grid: COLS×ROWS = 22×25 = 550 tiles.
 * Each tile shows one character (or a 2-char emoji via StaticLayout).
 *
 * Architecture:
 * - Regular char → 1 tile
 * - Emoji (UTF-16 surrogate pair) → 1 tile with full emoji; next tile = '\u0000' (blank)
 * - '\u0000' tile → shown as blank (used for consumed/low-surrogate positions)
 *
 * Emoji clipping rule:
 * - Emojis at odd column or that would span past COLS-1 are clipped (→ blank tile)
 * - This prevents garbage from orphan low surrogates in adjacent tiles
 */
data class Matrix(
    val id: String,
    val rows: List<String>,
    val durationSeconds: Int = 8,
    val changes: Set<Int>? = null
) {
    companion object {
        const val COLS = 22
        const val ROWS = 25

        /** Get display text for tile at flat-string position i. */
        fun getTileText(flat: String, i: Int): String {
            if (i >= flat.length) return " "
            val ch = flat[i]
            // Blank consumed tile
            if (ch == '\u0000') return " "
            // Check for surrogate pair (emoji)
            if (ch.isHighSurrogate() && i + 1 < flat.length && flat[i + 1].isLowSurrogate()) {
                return flat.substring(i, i + 2)
            }
            return ch.toString()
        }
    }

    /**
     * Flatten to exactly COLS * ROWS tiles.
     * Returns a String where each Char is one tile (or '\u0000' for blanks).
     */
    fun toFlatChars(): String {
        val tiles = CharArray(COLS * ROWS) { '\u0000' }
        var flatIdx = 0

        for (row in rows) {
            var srcIdx = 0  // source character index in row string

            while (srcIdx < COLS && flatIdx < COLS * ROWS) {
                val srcChar = if (srcIdx < row.length) row[srcIdx] else '\u0000'

                if (srcChar.isHighSurrogate() &&
                    srcIdx + 1 < row.length &&
                    row[srcIdx + 1].isLowSurrogate()) {
                    // Full emoji in source
                    val emojiFits = (srcIdx % 2 == 0) && (srcIdx + 1 < COLS)
                    if (emojiFits) {
                        tiles[flatIdx] = srcChar             // high surrogate
                        tiles[flatIdx + 1] = row[srcIdx + 1] // low surrogate (adjacent tile = consumed)
                        flatIdx += 2
                        srcIdx += 2
                    } else {
                        // Clip: show blank, skip both surrogates
                        tiles[flatIdx] = '\u0000'
                        flatIdx++
                        srcIdx += 2
                    }
                } else {
                    tiles[flatIdx] = srcChar
                    flatIdx++
                    srcIdx++
                }
            }
        }

        return String(tiles)
    }
}
