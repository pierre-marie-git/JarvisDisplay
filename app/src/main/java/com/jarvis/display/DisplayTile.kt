package com.jarvis.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
        Color.parseColor("#FF3333"), // red
        Color.parseColor("#3333FF"), // blue
        Color.parseColor("#33FF33"), // green
        Color.parseColor("#FFFF33"), // yellow
        Color.parseColor("#FFFFFF")  // white
    )
    private val DEFAULT_BG = Color.parseColor("#222222")
    private val TEXT_COLOR = Color.parseColor("#FFFFFF")

    private var currentChar: Char = ' '
    private var displayChar: Char = ' '
    private var isAnimating = false
    private var isSettled = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOR
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
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

    fun setChar(c: Char, animate: Boolean, delayMs: Long, onComplete: (() -> Unit)? = null) {
        val newChar = if (c == '\u0000') ' ' else c

        if (newChar == currentChar && !animate) {
            displayChar = newChar
            invalidate()
            return
        }

        if (!animate) {
            currentChar = newChar
            displayChar = newChar
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
        currentChar = newChar

        handler.postDelayed({
            runScrambleAnimation(newChar, onComplete)
        }, delayMs)
    }

    private fun runScrambleAnimation(targetChar: Char, onComplete: (() -> Unit)?) {
        val scrambleCount = 10 + Random.nextInt(5) // 10-14
        var step = 0
        var colorIndex = 0

        animationRunnable = object : Runnable {
            override fun run() {
                if (step < scrambleCount) {
                    // Scramble phase
                    displayChar = RANDOM_CHARS[Random.nextInt(RANDOM_CHARS.length)]
                    textPaint.color = TEXT_COLOR
                    bgPaint.color = SCRAMBLE_COLORS[colorIndex % SCRAMBLE_COLORS.size]
                    colorIndex++
                    step++
                    invalidate()
                    handler.postDelayed(this, 70)
                } else {
                    // Settle phase
                    displayChar = targetChar
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
        canvas.drawText(displayChar.toString(), width / 2f, textY, textPaint)
    }

    fun reset() {
        handler.removeCallbacksAndMessages(null)
        isAnimating = false
        isSettled = false
        currentChar = ' '
        displayChar = ' '
        bgPaint.color = DEFAULT_BG
        textPaint.color = TEXT_COLOR
        invalidate()
    }
}
