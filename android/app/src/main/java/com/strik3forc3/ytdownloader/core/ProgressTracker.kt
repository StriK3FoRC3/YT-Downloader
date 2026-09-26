package com.strik3forc3.ytdownloader.core

/** What an item is doing right now. Drives which UI treatment the row gets. */
enum class DownloadPhase { PENDING, EXTRACTING, DOWNLOADING, PROCESSING, DONE }

/** An item's current progress, as consumed by the UI. */
data class ItemProgress(
    val phase: DownloadPhase = DownloadPhase.PENDING,
    val fraction: Float = 0f,
    val speedBytesPerSecond: Double = 0.0,
    val etaSeconds: Long? = null,
    val postProcessor: String? = null,
)

/**
 * Folds [ProgressParser] events into a single monotonic fraction for one item.
 *
 * Two fixes over the reference (`DownloadOneAsync`, lines 1443–1470):
 *
 *  - It hardcodes `expectedStreams = audio ? 1 : 2` and counts
 *    `"[download] Destination:"` lines. When yt-dlp fetches a different number of
 *    streams the estimate is simply wrong.
 *  - It gives post-processing no share of the bar at all, so the item sits at 100%
 *    while FFmpeg does the slowest work on the device.
 *
 * Here the transfer phase owns [DOWNLOAD_SHARE] of the bar and post-processing owns the
 * rest, and the fraction is clamped monotonically so an unexpected extra stream cannot
 * make the bar run backwards.
 */
class ProgressTracker(private val expectedStreams: Int) {

    companion object {
        /** Transfer occupies this much of the bar; FFmpeg gets the remainder. */
        const val DOWNLOAD_SHARE = 0.85f

        /** Where the bar sits once post-processing has started but has no percentage. */
        private const val PROCESSING_FRACTION = 0.93f

        fun expectedStreamsFor(mode: DownloadMode): Int =
            if (mode == DownloadMode.AUDIO) 1 else 2
    }

    private var completedStreams = 0
    private var highWaterMark = 0f
    private var current = ItemProgress(phase = DownloadPhase.EXTRACTING)

    fun snapshot(): ItemProgress = current

    fun onEvent(event: ProgressParser.Event): ItemProgress {
        current = when (event) {
            is ProgressParser.Event.Download -> onDownload(event)
            is ProgressParser.Event.PostProcess -> onPostProcess(event)
        }
        return current
    }

    /** Terminal state, whether the item succeeded or failed. */
    fun finish(): ItemProgress {
        highWaterMark = 1f
        current = ItemProgress(phase = DownloadPhase.DONE, fraction = 1f)
        return current
    }

    private fun onDownload(event: ProgressParser.Event.Download): ItemProgress {
        if (event.finished) {
            completedStreams++
        }

        // Grow the denominator if yt-dlp turns out to fetch more streams than expected,
        // rather than letting the fraction exceed the download share.
        val streams = maxOf(expectedStreams, completedStreams + if (event.finished) 0 else 1)
            .coerceAtLeast(1)
        val streamFraction = if (event.finished) 0f else (event.fraction ?: 0f)
        val transferred = (completedStreams + streamFraction) / streams

        return ItemProgress(
            phase = DownloadPhase.DOWNLOADING,
            fraction = advance(transferred.coerceIn(0f, 1f) * DOWNLOAD_SHARE),
            speedBytesPerSecond = event.speedBytesPerSecond ?: 0.0,
            etaSeconds = event.etaSeconds,
        )
    }

    private fun onPostProcess(event: ProgressParser.Event.PostProcess): ItemProgress {
        // FFmpeg reports no percentage, so the bar holds while the phase label and the
        // ring's indeterminate animation carry the "still working" signal.
        val fraction = if (event.finished) advance(1f) else advance(PROCESSING_FRACTION)
        return ItemProgress(
            phase = if (event.finished) DownloadPhase.DONE else DownloadPhase.PROCESSING,
            fraction = fraction,
            postProcessor = event.postProcessor,
        )
    }

    private fun advance(candidate: Float): Float {
        highWaterMark = maxOf(highWaterMark, candidate.coerceIn(0f, 1f))
        return highWaterMark
    }
}
