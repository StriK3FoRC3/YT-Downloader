package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §4 and §5. */
class BitrateLadderTest {

    private fun source(codec: String?, kbps: Double) = AudioSourceInfo("140", codec, kbps)

    // §5 — parsing

    @Test
    fun `parse accepts the allowed ladder values case-insensitively`() {
        assertThat(BitrateLadder.parse("320 kbps")).isEqualTo(BitrateSetting.Fixed(320))
        assertThat(BitrateLadder.parse("48 KBPS")).isEqualTo(BitrateSetting.Fixed(48))
        assertThat(BitrateLadder.parse("  192 kbps  ")).isEqualTo(BitrateSetting.Fixed(192))
    }

    @Test
    fun `parse rejects anything off the ladder`() {
        for (input in listOf("Automatic", "", "200 kbps", "320", "kbps", "-320 kbps")) {
            assertThat(BitrateLadder.parse(input)).isEqualTo(BitrateSetting.Automatic)
        }
        assertThat(BitrateLadder.parse(null)).isEqualTo(BitrateSetting.Automatic)
    }

    // §4 — passthrough matrix

    @Test
    fun `passthrough matches codec families by substring`() {
        assertThat(BitrateLadder.isPassthrough("m4a", "mp4a.40.2")).isTrue()
        assertThat(BitrateLadder.isPassthrough("aac", "aac")).isTrue()
        assertThat(BitrateLadder.isPassthrough("opus", "opus")).isTrue()
        assertThat(BitrateLadder.isPassthrough("vorbis", "vorbis")).isTrue()
        assertThat(BitrateLadder.isPassthrough("mp3", "mp3")).isTrue()
    }

    @Test
    fun `passthrough rejects mismatched families and unknown sources`() {
        assertThat(BitrateLadder.isPassthrough("mp3", "opus")).isFalse()
        assertThat(BitrateLadder.isPassthrough("opus", "mp4a.40.2")).isFalse()
        assertThat(BitrateLadder.isPassthrough("m4a", null)).isFalse()
        assertThat(BitrateLadder.isPassthrough("m4a", "")).isFalse()
        // Lossless targets are never a passthrough.
        assertThat(BitrateLadder.isPassthrough("flac", "flac")).isFalse()
    }

    // §5 — the ladder

    @Test
    fun `lossless targets never carry a bitrate`() {
        for (codec in listOf("flac", "wav")) {
            assertThat(BitrateLadder.resolve(codec, source("opus", 128.0), BitrateSetting.Automatic))
                .isEqualTo(BitrateLadder.NO_TRANSCODE)
            // Even an explicit fixed bitrate is ignored for a lossless container.
            assertThat(BitrateLadder.resolve(codec, source("opus", 128.0), BitrateSetting.Fixed(320)))
                .isEqualTo(BitrateLadder.NO_TRANSCODE)
        }
    }

    @Test
    fun `a fixed bitrate overrides passthrough`() {
        assertThat(BitrateLadder.resolve("opus", source("opus", 128.0), BitrateSetting.Fixed(96)))
            .isEqualTo(96)
    }

    @Test
    fun `matching source codec skips the transcode`() {
        assertThat(BitrateLadder.resolve("opus", source("opus", 128.0), BitrateSetting.Automatic))
            .isEqualTo(BitrateLadder.NO_TRANSCODE)
        assertThat(BitrateLadder.resolve("m4a", source("mp4a.40.2", 128.0), BitrateSetting.Automatic))
            .isEqualTo(BitrateLadder.NO_TRANSCODE)
    }

    @Test
    fun `automatic targets the best mp3 the encoder can do`() {
        // Divergence from the reference, which scales to the source and lands on 192 for
        // a typical ~130 kbps Opus stream. A lossy-to-lossy transcode needs headroom.
        val auto = BitrateSetting.Automatic
        assertThat(BitrateLadder.resolve("mp3", source("opus", 130.0), auto)).isEqualTo(320)
        assertThat(BitrateLadder.resolve("mp3", source("opus", 64.0), auto)).isEqualTo(320)
        assertThat(BitrateLadder.resolve("mp3", source("opus", 256.0), auto)).isEqualTo(320)
    }

    @Test
    fun `automatic targets the top of the ladder for other lossy formats`() {
        val auto = BitrateSetting.Automatic
        assertThat(BitrateLadder.resolve("opus", source("mp4a", 128.0), auto)).isEqualTo(256)
        assertThat(BitrateLadder.resolve("vorbis", source("mp4a", 64.0), auto)).isEqualTo(256)
    }

    @Test
    fun `an unknown source still gets the best target`() {
        // A failed probe must not quietly downgrade the result.
        val auto = BitrateSetting.Automatic
        assertThat(BitrateLadder.resolve("mp3", null, auto)).isEqualTo(320)
        assertThat(BitrateLadder.resolve("opus", null, auto)).isEqualTo(256)
    }

    @Test
    fun `source bitrate no longer changes the automatic target`() {
        val auto = BitrateSetting.Automatic
        val low = BitrateLadder.resolve("mp3", source("opus", 48.0), auto)
        val high = BitrateLadder.resolve("mp3", source("opus", 320.0), auto)
        assertThat(low).isEqualTo(high)
    }

    @Test
    fun `probing is skipped when it cannot change the outcome`() {
        // The reference probes before every audio download regardless; these two cases
        // are decided before the source is even known.
        assertThat(BitrateLadder.needsSourceProbe(AudioFormat.FLAC, BitrateSetting.Automatic)).isFalse()
        assertThat(BitrateLadder.needsSourceProbe(AudioFormat.WAV, BitrateSetting.Automatic)).isFalse()
        assertThat(BitrateLadder.needsSourceProbe(AudioFormat.MP3, BitrateSetting.Fixed(192))).isFalse()
    }

    @Test
    fun `probing is required for an automatic lossy target`() {
        for (format in listOf(AudioFormat.MP3, AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.OGG, AudioFormat.AAC)) {
            assertThat(BitrateLadder.needsSourceProbe(format, BitrateSetting.Automatic)).isTrue()
        }
    }

    // Drives the pre-commit warning the Windows app never shows.

    @Test
    fun `willTranscode distinguishes a stream copy from real work`() {
        val auto = BitrateSetting.Automatic
        assertThat(BitrateLadder.willTranscode(AudioFormat.OPUS, source("opus", 128.0), auto)).isFalse()
        assertThat(BitrateLadder.willTranscode(AudioFormat.MP3, source("opus", 128.0), auto)).isTrue()
        assertThat(BitrateLadder.willTranscode(AudioFormat.FLAC, source("opus", 128.0), auto)).isTrue()
        assertThat(BitrateLadder.willTranscode(AudioFormat.OPUS, source("opus", 128.0), BitrateSetting.Fixed(96)))
            .isTrue()
    }
}
