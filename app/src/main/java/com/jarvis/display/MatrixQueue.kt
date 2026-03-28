package com.jarvis.display

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * Manages the queue of display matrices and loops through them.
 * Handles timing between matrices — no server polling needed during playback.
 */
class MatrixQueue(
    private val flipBoard: FlipBoardView,
    private val onMatrixChanged: () -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private val matrices = mutableListOf<Matrix>()
    private var currentIndex = 0
    private var isRunning = false
    private var previousContent: String = ""

    companion object {
        // Test matrices for local testing — clean text states
        fun defaultTestMatrices(): List<Matrix> = listOf(
            Matrix(id = "1", rows = buildRows("JARVIS ONLINE", 25), durationSeconds = 6),
            Matrix(id = "2", rows = buildRows("MESSAGE 2 - OK", 25), durationSeconds = 6),
            Matrix(id = "3", rows = buildRows("TEST 3 - FINAL", 25), durationSeconds = 6)
        )

        private fun buildRows(message: String, totalRows: Int): List<String> {
            val startCol = (Matrix.COLS - message.length) / 2
            return (0 until totalRows).map { row ->
                String(CharArray(Matrix.COLS) { col ->
                    if (row == totalRows / 2 && col in startCol until startCol + message.length) {
                        message[col - startCol]
                    } else ' '
                })
            }
        }
    }

    /** Load matrices and start the display loop */
    fun loadAndPlay(newMatrices: List<Matrix>) {
        matrices.clear()
        matrices.addAll(newMatrices)
        currentIndex = 0
        if (!isRunning) {
            isRunning = true
            // Init with black screen
            previousContent = String(CharArray(Matrix.COLS * Matrix.ROWS) { ' ' })
            playCurrent()
        }
    }

    /** Start with default test matrices (no server needed) */
    fun startWithTestData() {
        loadAndPlay(defaultTestMatrices())
    }

    private fun playCurrent() {
        if (matrices.isEmpty()) return

        val matrix = matrices[currentIndex]
        val newContent = matrix.toFlatChars()

        // Compute which tiles actually changed from previous state
        val changedIndices = mutableSetOf<Int>()
        for (i in newContent.indices) {
            if (i >= previousContent.length || newContent[i] != previousContent[i]) {
                changedIndices.add(i)
            }
        }

        // Build display matrix with only changed tiles as "changes"
        val displayMatrix = Matrix(
            id = matrix.id,
            rows = matrix.rows,
            durationSeconds = matrix.durationSeconds,
            changes = changedIndices
        )

        flipBoard.displayMatrix(displayMatrix)
        previousContent = newContent

        onMatrixChanged()

        // Animation time: stagger across changed tiles + 400ms settle
        val animMs = if (changedIndices.isNotEmpty()) {
            (changedIndices.maxOrNull()!! * 1L) + 400L
        } else {
            400L
        }
        val displayMs = (matrix.durationSeconds * 1000L) - animMs

        handler.postDelayed({
            currentIndex = (currentIndex + 1) % matrices.size
            playCurrent()
        }, maxOf(displayMs, 500L))
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
