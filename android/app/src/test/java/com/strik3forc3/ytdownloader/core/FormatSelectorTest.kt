package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §2 and §3. */
class FormatSelectorTest {

    // §2 — video

    @Test
    fun `mp4 at 1080p matches the reference selector`() {
        assertThat(FormatSelector.video(VideoFormat.MP4, Resolution.R1080)).isEqualTo(
            "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/" +
                "bestvideo[height<=1080]+bestaudio/best[height<=1080]/" +
                "best*[height<=1080]/best*"
        )
    }

    @Test
    fun `webm at 720p prefers webm on both tracks`() {
        assertThat(FormatSelector.video(VideoFormat.WEBM, Resolution.R720)).isEqualTo(
            "bestvideo[height<=720][ext=webm]+bestaudio[ext=webm]/" +
                "bestvideo[height<=720]+bestaudio/best[height<=720]/" +
                "best*[height<=720]/best*"
        )
    }

    @Test
    fun `mkv applies no container constraint`() {
        assertThat(FormatSelector.video(VideoFormat.MKV, Resolution.R1440))
            .isEqualTo("bestvideo[height<=1440]+bestaudio/best[height<=1440]/best*[height<=1440]/best*")
    }

    @Test
    fun `highest resolution omits the height filter entirely`() {
        assertThat(FormatSelector.video(VideoFormat.MP4, Resolution.HIGHEST)).isEqualTo(
            "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best/best*"
        )
        assertThat(FormatSelector.video(VideoFormat.MKV, Resolution.HIGHEST))
            .isEqualTo("bestvideo+bestaudio/best/best*")
    }

    @Test
    fun `8k and 4k labels resolve to their numeric heights`() {
        assertThat(Resolution.R4320.heightFilter).isEqualTo("[height<=4320]")
        assertThat(Resolution.R2160.heightFilter).isEqualTo("[height<=2160]")
    }

    // §3 — audio

    @Test
    fun `automatic bitrate steers towards the matching source codec`() {
        val auto = BitrateSetting.Automatic
        assertThat(FormatSelector.audio(AudioFormat.M4A, auto))
            .isEqualTo("bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
        assertThat(FormatSelector.audio(AudioFormat.AAC, auto))
            .isEqualTo("bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
        assertThat(FormatSelector.audio(AudioFormat.OPUS, auto))
            .isEqualTo("bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best")
    }

    @Test
    fun `fixed bitrate steers away from the matching source codec`() {
        // A forced bitrate re-encodes regardless, so picking the *other* family avoids
        // stacking two generations of the same lossy transform.
        val fixed = BitrateSetting.Fixed(192)
        assertThat(FormatSelector.audio(AudioFormat.M4A, fixed))
            .isEqualTo("bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best")
        assertThat(FormatSelector.audio(AudioFormat.OPUS, fixed))
            .isEqualTo("bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best")
    }

    @Test
    fun `formats with no youtube-native source always take bestaudio`() {
        for (format in listOf(AudioFormat.MP3, AudioFormat.FLAC, AudioFormat.WAV, AudioFormat.OGG)) {
            assertThat(FormatSelector.audio(format, BitrateSetting.Automatic))
                .isEqualTo("bestaudio/best")
            assertThat(FormatSelector.audio(format, BitrateSetting.Fixed(320)))
                .isEqualTo("bestaudio/best")
        }
    }

    @Test
    fun `ogg maps to the vorbis codec name and others pass through`() {
        assertThat(AudioFormat.OGG.ytDlpCodec).isEqualTo("vorbis")
        assertThat(AudioFormat.MP3.ytDlpCodec).isEqualTo("mp3")
        assertThat(AudioFormat.M4A.ytDlpCodec).isEqualTo("m4a")
    }
}
