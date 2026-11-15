package com.openfocushq.openfocus.blocking

/**
 * Counts how long the user has actually spent on the pause screen, fed with frame timestamps.
 * Gaps longer than [maxGapMs] between frames (screen covered, app stalled) aren't counted, so
 * the wait can't be "served" while the pause screen isn't visible.
 */
class WaitTimer(val totalMs: Long, initialElapsedMs: Long = 0, private val maxGapMs: Long = 250) {
    var elapsedMs: Long = initialElapsedMs.coerceIn(0, totalMs.coerceAtLeast(0))
        private set
    private var lastFrameMs: Long? = null

    /** Feed the current frame time; returns the updated elapsed time. */
    fun onFrame(nowMs: Long): Long {
        lastFrameMs?.let { last -> elapsedMs = (elapsedMs + (nowMs - last).coerceIn(0, maxGapMs)).coerceAtMost(totalMs) }
        lastFrameMs = nowMs
        return elapsedMs
    }

    val done: Boolean get() = elapsedMs >= totalMs
    val progress: Float get() = if (totalMs <= 0) 1f else elapsedMs.toFloat() / totalMs
    val secondsLeft: Int get() = ((totalMs - elapsedMs + 999) / 1000).toInt()
}
