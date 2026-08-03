package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressParserTest {

    private fun dl(payload: String) = ProgressParser.parse("@@DL@@$payload")
    private fun pp(payload: String) = ProgressParser.parse("@@PP@@$payload")

    @Test
    fun `ignores ordinary yt-dlp output`() {
        assertThat(ProgressParser.parse("[youtube] abc: Downloading webpage")).isNull()
        assertThat(ProgressParser.parse("[download] 45.2% of 3.42MiB at 1.20MiB/s")).isNull()
        assertThat(ProgressParser.parse("")).isNull()
        assertThat(ProgressParser.parse(null)).isNull()
    }

    @Test
    fun `parses a downloading event`() {
        val event = dl("downloading|524288|1048576|NA|262144.0|2") as ProgressParser.Event.Download
        assertThat(event.status).isEqualTo("downloading")
        assertThat(event.downloadedBytes).isEqualTo(524288)
        assertThat(event.totalBytes).isEqualTo(1048576)
        assertThat(event.speedBytesPerSecond).isWithin(0.1).of(262144.0)
        assertThat(event.etaSeconds).isEqualTo(2)
        assertThat(event.fraction).isWithin(0.001f).of(0.5f)
        assertThat(event.finished).isFalse()
    }

    @Test
    fun `falls back to the estimated total when the exact one is unknown`() {
        val event = dl("downloading|500|NA|2000.0|NA|NA") as ProgressParser.Event.Download
        assertThat(event.totalBytes).isEqualTo(2000)
        assertThat(event.fraction).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `handles NA in every optional field`() {
        val event = dl("downloading|NA|NA|NA|NA|NA") as ProgressParser.Event.Download
        assertThat(event.downloadedBytes).isNull()
        assertThat(event.totalBytes).isNull()
        assertThat(event.speedBytesPerSecond).isNull()
        assertThat(event.etaSeconds).isNull()
        assertThat(event.fraction).isNull()
    }

    @Test
    fun `recognises a finished stream`() {
        val event = dl("finished|1048576|1048576|NA|NA|NA") as ProgressParser.Event.Download
        assertThat(event.finished).isTrue()
    }

    @Test
    fun `guards against a zero total`() {
        val event = dl("downloading|0|0|NA|NA|NA") as ProgressParser.Event.Download
        assertThat(event.fraction).isNull()
    }

    @Test
    fun `parses post-processing events`() {
        val started = pp("started|FFmpegExtractAudio") as ProgressParser.Event.PostProcess
        assertThat(started.postProcessor).isEqualTo("FFmpegExtractAudio")
        assertThat(started.finished).isFalse()

        val done = pp("finished|FFmpegExtractAudio") as ProgressParser.Event.PostProcess
        assertThat(done.finished).isTrue()
    }

    @Test
    fun `treats NA postprocessor as absent`() {
        val event = pp("started|NA") as ProgressParser.Event.PostProcess
        assertThat(event.postProcessor).isNull()
    }

    @Test
    fun `rejects malformed payloads`() {
        assertThat(dl("too|few|fields")).isNull()
        assertThat(dl("|1|2|3|4|5")).isNull()
        assertThat(pp("")).isNull()
    }

    @Test
    fun `templates cover both download and postprocess channels`() {
        val argv = ProgressParser.PROGRESS_TEMPLATES.toArgv()
        assertThat(argv.count { it == "--progress-template" }).isEqualTo(2)
        assertThat(argv.any { it.startsWith("download:") }).isTrue()
        assertThat(argv.any { it.startsWith("postprocess:") }).isTrue()
    }
}
