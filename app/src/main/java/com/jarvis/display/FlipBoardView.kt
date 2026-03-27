package com.jarvis.display

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout

class FlipBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    companion object {
        const val COLS = 22
        const val ROWS = 5
        const val TOTAL_TILES = COLS * ROWS
    }

    private val tiles = arrayOfNulls<DisplayTile>(TOTAL_TILES)
    private var lastMessageId: String? = null
    private var isMuted: Boolean = false

    var onAllAnimationsComplete: (() -> Unit)? = null

    init {
        columnCount = COLS
        rowCount = ROWS

        val tileWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        val tileHeight = ViewGroup.LayoutParams.WRAP_CONTENT

        for (index in 0 until TOTAL_TILES) {
            val row = index / COLS
            val col = index % COLS
            val tile = DisplayTile(context).apply {
                setTilePosition(row, col)
            }
            tiles[index] = tile

            val params = LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = spec(col, 1f)
                rowSpec = spec(row)
                setMargins(2, 2, 2, 2)
            }
            addView(tile, params)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun displayMessages(messages: List<Message>, onMessageChanged: (Boolean) -> Unit) {
        if (messages.isEmpty()) {
            showDefaultMessage()
            return
        }

        val message = messages[0]
        val changed = message.id != lastMessageId
        lastMessageId = message.id

        val textRows = message.text
        val flatText = StringBuilder()
        for (row in textRows) {
            flatText.append(row.padEnd(COLS).substring(0, COLS))
        }
        // Pad with empty rows if needed
        while (flatText.length < TOTAL_TILES) {
            flatText.append(" ".repeat(TOTAL_TILES - flatText.length))
        }

        var completedCount = 0
        val totalToAnimate = if (changed) TOTAL_TILES else 0

        for (index in 0 until TOTAL_TILES) {
            val tile = tiles[index]!!
            val targetChar = flatText[index]
            val row = index / COLS
            val col = index % COLS
            val delay = (row * COLS + col) * 25L

            if (changed) {
                tile.setChar(targetChar, true, delay) {
                    completedCount++
                    if (completedCount >= totalToAnimate) {
                        onAllAnimationsComplete?.invoke()
                    }
                }
            } else {
                tile.setChar(targetChar, false, 0)
            }
        }

        if (!changed) {
            onMessageChanged(false)
        } else {
            onMessageChanged(true)
        }
    }

    fun showDefaultMessage() {
        val defaultText = buildDefaultDisplay()
        for (index in 0 until TOTAL_TILES) {
            val tile = tiles[index]!!
            tile.setChar(defaultText[index], false, 0)
        }
    }

    private fun buildDefaultDisplay(): String {
        val lines = listOf(
            "                       ",
            "     JARVIS ONLINE     ",
            "                       ",
            "                       ",
            "                       "
        )
        val sb = StringBuilder()
        for (line in lines) {
            sb.append(line.padEnd(COLS).substring(0, COLS))
        }
        return sb.toString()
    }

    fun getTile(row: Int, col: Int): DisplayTile? {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return null
        return tiles[row * COLS + col]
    }
}
