package com.jarvis.display

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import kotlin.random.Random

/**
 * Custom flip-board display: a grid of tiles that animate character changes.
 * Each tile flips when its character changes, creating the classic split-flap effect.
 */
class FlipBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    companion object {
        const val COLS = 22
        const val ROWS = 25
        const val TOTAL_TILES = COLS * ROWS
        private const val ANIM_STAGGER_MS = 1L
        private const val ANIM_DURATION_MS = 400L
    }

    private val tileViews = arrayOfNulls<DisplayTile>(TOTAL_TILES)
    private var currentContent: String = ""
    private var pendingCount = 0
    var onAnimationComplete: (() -> Unit)? = null

    init {
        isClickable = false
        isFocusable = false
        for (i in 0 until TOTAL_TILES) {
            val tile = DisplayTile(context).apply {
                isClickable = false
                isFocusable = false
            }
            tileViews[i] = tile
            addView(tile)
        }
        // Start all black
        post {
            for (tile in tileViews) {
                tile?.setChar(" ", false, 0)
            }
        }
    }

    /**
     * Extract 1 or 2 UTF-16 code units for tile at col.
     * Emojis (code points > U+FFFF) are encoded as surrogate pairs in UTF-16.
     */
    private fun getTileText(content: String, col: Int): String {
        if (col >= content.length) return " "
        val ch = content[col]
        return when {
            ch == '\u0000' -> " "
            ch.isHighSurrogate() && col + 1 < content.length && content[col + 1].isLowSurrogate() ->
                content.substring(col, col + 2)
            else -> ch.toString()
        }
    }

    /**
     * Display a matrix — only tiles in `changes` animate.
     * Other tiles stay in their current state.
     */
    fun displayMatrix(matrix: Matrix) {
        val newContent = matrix.toFlatChars()
        val changedIndices = matrix.changes ?: (0 until TOTAL_TILES).toSet()

        val actuallyChanged = changedIndices.filter { i ->
            getTileText(newContent, i) != getTileText(currentContent, i)
        }.toSet()

        if (actuallyChanged.isEmpty()) {
            currentContent = newContent
            onAnimationComplete?.invoke()
            return
        }

        currentContent = newContent
        pendingCount = actuallyChanged.size

        for (i in actuallyChanged) {
            val tile = tileViews[i]!!
            val targetText = getTileText(newContent, i)
            val delay = i * ANIM_STAGGER_MS
            tile.setChar(targetText, true, delay) {
                pendingCount--
                if (pendingCount <= 0) {
                    onAnimationComplete?.invoke()
                }
            }
        }
    }

    /** Show default "JARVIS ONLINE" matrix with wave animation */
    fun showDefaultMatrix() {
        val defaultMatrix = Matrix(
            id = "default",
            rows = buildDefaultRows("JARVIS ONLINE"),
            durationSeconds = 8
        )
        displayMatrix(defaultMatrix)
    }

    private fun buildDefaultRows(message: String): List<String> {
        val startCol = (COLS - message.length) / 2
        return (0 until ROWS).map { row ->
            String(CharArray(COLS) { col ->
                if (row == ROWS / 2 && col in startCol until startCol + message.length) {
                    message[col - startCol]
                } else ' '
            })
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        val cellSize = minOf(w / COLS, h / ROWS)
        val offsetX = (w - cellSize * COLS) / 2
        val offsetY = (h - cellSize * ROWS) / 2
        var i = 0
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                tileViews[i]?.let { tile ->
                    tile.layout(
                        offsetX + col * cellSize + 1,
                        offsetY + row * cellSize + 1,
                        offsetX + col * cellSize + cellSize - 1,
                        offsetY + row * cellSize + cellSize - 1
                    )
                }
                i++
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val cellSize = minOf(w / COLS, h / ROWS)
        for (tile in tileViews) {
            tile?.measure(
                MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(w, h)
    }
}
