package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressTrackerTest {

    private fun downloading(done: Long, total: Long, speed: Double = 0.0) =
        ProgressParser.Event.Download("downloading", done, total, speed, null)

    private fun streamFinished() =
        ProgressParser.Event.Download("finished", 100, 100, null, null)

    @Test
    fun `single-stream audio maps transfer onto the download share`() {
        val tracker = ProgressTracker(expectedStreams = 1)
        assertThat(tracker.onEvent(downloading(50, 100)).fraction)
            .isWithin(0.001f).of(0.5f * ProgressTracker.DOWNLOAD_SHARE)
    }

    @Test
    fun `two-stream video splits the download share between streams`() {
        val tracker = ProgressTracker(expectedStreams = 2)

        // Halfway through the first of two streams is a quarter of the transfer phase.
        assertThat(tracker.onEvent(downloading(50, 100)).fraction)
            .isWithin(0.001f).of(0.25f * ProgressTracker.DOWNLOAD_SHARE)

        tracker.onEvent(streamFinished())
        assertThat(tracker.onEvent(downloading(50, 100)).fraction)
            .isWithin(0.001f).of(0.75f * ProgressTracker.DOWNLOAD_SHARE)
    }

    @Test
    fun `transfer never fills the whole bar`() {
        val tracker = ProgressTracker(expectedStreams = 1)
        val progress = tracker.onEvent(streamFinished())
        assertThat(progress.fraction).isAtMost(ProgressTracker.DOWNLOAD_SHARE)
        assertThat(progress.fraction).isLessThan(1f)
    }

    @Test
    fun `post-processing is visible rather than pinned at complete`() {
        // The reference's core progress defect: FFmpeg work is invisible.
        val tracker = ProgressTracker(expectedStreams = 1)
        tracker.onEvent(streamFinished())

        val processing = tracker.onEvent(
            ProgressParser.Event.PostProcess("started", "FFmpegExtractAudio")
        )
        assertThat(processing.phase).isEqualTo(DownloadPhase.PROCESSING)
        assertThat(processing.postProcessor).isEqualTo("FFmpegExtractAudio")
        assertThat(processing.fraction).isGreaterThan(ProgressTracker.DOWNLOAD_SHARE)
        assertThat(processing.fraction).isLessThan(1f)

        val done = tracker.onEvent(
            ProgressParser.Event.PostProcess("finished", "FFmpegExtractAudio")
        )
        assertThat(done.phase).isEqualTo(DownloadPhase.DONE)
        assertThat(done.fraction).isEqualTo(1f)
    }

    @Test
    fun `an unexpected extra stream cannot push the bar backwards`() {
        val tracker = ProgressTracker(expectedStreams = 1)
        tracker.onEvent(downloading(90, 100))
        val before = tracker.snapshot().fraction

        // yt-dlp turns out to fetch a second stream after all.
        tracker.onEvent(streamFinished())
        val after = tracker.onEvent(downloading(1, 100)).fraction

        assertThat(after).isAtLeast(before)
    }

    @Test
    fun `fraction is monotonic across a noisy event stream`() {
        val tracker = ProgressTracker(expectedStreams = 2)
        val events = listOf(
            downloading(10, 100), downloading(50, 100), downloading(30, 100),
            streamFinished(),
            downloading(20, 100), downloading(5, 100), downloading(80, 100),
            streamFinished(),
            ProgressParser.Event.PostProcess("started", "FFmpegMerger"),
            ProgressParser.Event.PostProcess("finished", "FFmpegMerger"),
        )

        var previous = 0f
        for (event in events) {
            val fraction = tracker.onEvent(event).fraction
            assertThat(fraction).isAtLeast(previous)
            previous = fraction
        }
        assertThat(previous).isEqualTo(1f)
    }

    @Test
    fun `speed and eta pass through`() {
        val tracker = ProgressTracker(expectedStreams = 1)
        val progress = tracker.onEvent(
            ProgressParser.Event.Download("downloading", 10, 100, 1_500_000.0, 42)
        )
        assertThat(progress.speedBytesPerSecond).isWithin(0.1).of(1_500_000.0)
        assertThat(progress.etaSeconds).isEqualTo(42)
    }

    @Test
    fun `finish forces the terminal state`() {
        val tracker = ProgressTracker(expectedStreams = 2)
        tracker.onEvent(downloading(10, 100))
        val done = tracker.finish()
        assertThat(done.phase).isEqualTo(DownloadPhase.DONE)
        assertThat(done.fraction).isEqualTo(1f)
    }

    @Test
    fun `expected stream count follows the mode`() {
        assertThat(ProgressTracker.expectedStreamsFor(DownloadMode.AUDIO)).isEqualTo(1)
        assertThat(ProgressTracker.expectedStreamsFor(DownloadMode.VIDEO)).isEqualTo(2)
    }
}
