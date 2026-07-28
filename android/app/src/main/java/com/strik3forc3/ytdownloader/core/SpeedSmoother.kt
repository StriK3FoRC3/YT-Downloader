package com.strik3forc3.ytdownloader.core

/**
 * Smooths yt-dlp's reported transfer rate into something a person can read.
 *
 * yt-dlp reports instantaneous speed per chunk, which on a mobile connection swings
 * wildly — 4 MB/s to 900 KB/s and back within a second. Rendering that raw gives a
 * readout whose every digit changes on every update, so the rolling-digit animation never
 * settles and the number is unreadable. The animation is not the problem; the underlying
 * value is.
 *
 * An exponential moving average fixes the jitter, and quantising to the displayed
 * precision fixes the rest: the emitted value only changes when the *rendered string*
 * would change, so digits animate when there is something to see and hold still
 * otherwise.
 */
class SpeedSmoother(
    /** Lower reacts more slowly. 0.25 settles in about a second at typical update rates. */
    private val smoothing: Double = 0.25,
) {
    private var average: Double? = null

    /** @return the smoothed rate in bytes per second. */
    fun update(rawBytesPerSecond: Double): Double {
        if (rawBytesPerSecond <= 0.0) return average ?: 0.0

        val previous = average
        val next = if (previous == null) {
            rawBytesPerSecond
        } else {
            previous + smoothing * (rawBytesPerSecond - previous)
        }
        average = next
        return next
    }

    fun reset() {
        average = null
    }

    companion object {
        /**
         * Rounds to the precision the UI actually shows, so a change of 0.01 MB/s does not
         * trigger a re-render of digits that will look identical.
         *
         * Mirrors `Format.speed`: one decimal place above 1 MB/s, whole KB/s below.
         */
        fun quantise(bytesPerSecond: Double): Double = when {
            bytesPerSecond <= 0.0 -> 0.0
            bytesPerSecond >= 1_000_000 ->
                Math.round(bytesPerSecond / 100_000.0) * 100_000.0
            else ->
                Math.round(bytesPerSecond / 1_000.0) * 1_000.0
        }
    }
}
