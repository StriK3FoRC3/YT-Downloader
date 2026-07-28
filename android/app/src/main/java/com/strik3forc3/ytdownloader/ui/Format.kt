package com.strik3forc3.ytdownloader.ui

import java.util.Locale
import kotlin.math.roundToLong

/** Display formatting. Kept together so the same number never renders two ways. */
object Format {

    /** Matches the reference's MB/s readout, switching down to KB/s on slow links. */
    fun speed(bytesPerSecond: Double): String = when {
        bytesPerSecond <= 0 -> "—"
        bytesPerSecond >= 1_000_000 ->
            String.format(Locale.US, "%.1f MB/s", bytesPerSecond / 1_000_000.0)
        else -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1_000.0)
    }

    /** `mm:ss`, rolling over to `h:mm:ss` past an hour. */
    fun elapsed(millis: Long): String {
        val totalSeconds = (millis / 1000.0).roundToLong().coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun eta(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        return "${elapsed(seconds * 1000)} left"
    }

    fun percent(fraction: Float): String =
        "${(fraction.coerceIn(0f, 1f) * 100).roundToLong()}%"
}
