package com.jarvis.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class DisplayTile @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*?<>{}[]|"
    private val SCRAMBLE_COLORS = intArrayOf(
        Color.parseColor("#FF3333"),
        Color.parseColor("#3333FF"),
        Color.parseColor("#33FF33"),
        Color.parseColor("#FFFF33"),
        Color.parseColor("#FFFFFF")
    )
    private val DEFAULT_BG = Color.parseColor("#222222")
    private val TEXT_COLOR = Color.parseColor("#FFFFFF")

    private var currentText: String = " "
    private var displayText: String = " "
    private var isAnimating: Boolean = false
    private var isSettled: Boolean = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEFAULT_BG
        style = Paint.Style.FILL
    }

    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    private var tileRow: Int = 0
    private var tileCol: Int = 0

    fun setTilePosition(row: Int, col: Int) {
        tileRow = row
        tileCol = col
    }

    /**
     * Set the character(s) to display. Can be 1 or 2 UTF-16 code units.
     * For emojis (surrogate pairs), pass the full 2-char string.
     * If this tile holds the LOW surrogate of a pair, pass just the single char
     * and the tile will just show it as-is (no animation) since the high-surrogate
     * tile handles the pair animation.
     */
    fun setChar(text: String, animate: Boolean, delayMs: Long, onComplete: (() -> Unit)? = null) {
        // '\u0000' = blank tile (used for low surrogates consumed by emoji pairs)
        val newText = when {
            text.isEmpty() -> " "
            text == "\u0000" -> " "
            else -> text
        }

        if (newText == currentText && !animate) {
            displayText = newText
            invalidate()
            return
        }

        if (!animate) {
            currentText = newText
            displayText = newText
            isSettled = true
            isAnimating = false
            bgPaint.color = DEFAULT_BG
            textPaint.color = TEXT_COLOR
            invalidate()
            return
        }

        if (isAnimating) {
            handler.removeCallbacks(animationRunnable!!)
        }

        isAnimating = true
        isSettled = false
        currentText = newText

        handler.postDelayed({
            runScrambleAnimation(newText, onComplete)
        }, delayMs)
    }

    private fun runScrambleAnimation(targetText: String, onComplete: (() -> Unit)?) {
        val scrambleCount = 10 + Random.nextInt(5)
        var step = 0
        var colorIndex = 0

        animationRunnable = object : Runnable {
            override fun run() {
                if (step < scrambleCount) {
                    displayText = RANDOM_CHARS.random().toString()
                    textPaint.color = TEXT_COLOR
                    bgPaint.color = SCRAMBLE_COLORS[colorIndex % SCRAMBLE_COLORS.size]
                    colorIndex++
                    step++
                    invalidate()
                    handler.postDelayed(this, 70)
                } else {
                    displayText = targetText
                    bgPaint.color = DEFAULT_BG
                    textPaint.color = TEXT_COLOR
                    isSettled = true
                    isAnimating = false
                    invalidate()
                    onComplete?.invoke()
                }
            }
        }
        handler.post(animationRunnable!!)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)

        textPaint.textSize = (minOf(width, height) * 0.65f)
        val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(displayText, width / 2f, textY, textPaint)
    }

    fun reset() {
        handler.removeCallbacksAndMessages(null)
        isAnimating = false
        isSettled = false
        currentText = " "
        displayText = " "
        bgPaint.color = DEFAULT_BG
        textPaint.color = TEXT_COLOR
        invalidate()
    }
}
