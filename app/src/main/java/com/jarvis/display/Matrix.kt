package com.jarvis.display

/**
 * Represents a display matrix — a 2D grid of characters to show on the flip board.
 * @param changes Indices of tiles that should animate from previous state. null = all tiles animate.
 */
data class Matrix(
    val id: String,
    val rows: List<String>,  // each string = one row, padded to COLS chars
    val durationSeconds: Int = 8,
    val changes: Set<Int>? = null  // null = all tiles animate; set of indices = only those animate
) {
    companion object {
        const val COLS = 22
        const val ROWS = 25
    }

    fun toFlatChars(): String {
        val sb = StringBuilder()
        for (row in rows) {
            sb.append(row.padEnd(COLS).substring(0, COLS))
        }
        while (sb.length < COLS * ROWS) {
            sb.append(" ".repeat(COLS * ROWS - sb.length))
        }
        return sb.toString()
    }
}
