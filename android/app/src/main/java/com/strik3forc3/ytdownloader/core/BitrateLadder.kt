package com.strik3forc3.ytdownloader.core

/**
 * Decides whether an audio download re-encodes, and at what bitrate.
 *
 * Contract: `docs/download-rules.md` §4 and §5.
 * Ported from `AudioTranscodeBitrate` (reference line 1561), `IsNativeAudioCodec`
 * (line 1590) and `ParseBitrateSetting` (line 1582).
 *
 * This is the most performance-relevant rule in the app. On a phone the difference
 * between passthrough and transcode is a stream copy versus a sustained CPU load that
 * drains battery and thermally throttles everything else.
 */
object BitrateLadder {

    /** `0` means "emit no `--audio-quality` flag", i.e. do not force a re-encode. */
    const val NO_TRANSCODE = 0

    private val FIXED_BITRATE = Regex(
        """^\s*(48|64|96|128|160|192|224|256|320)\s*kbps\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Parses a persisted bitrate label. Anything unrecognised — including "Automatic" — is [NO_TRANSCODE]. */
    fun parse(value: String?): BitrateSetting {
        val kbps = FIXED_BITRATE.find(value.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return if (kbps != null) BitrateSetting.Fixed(kbps) else BitrateSetting.Automatic
    }

    /**
     * True when the source stream can be remuxed into the target container without
     * re-encoding. Matched by substring because yt-dlp reports codecs with profile
     * suffixes (`mp4a.40.2`, `opus`, `vorbis`).
     */
    fun isPassthrough(targetCodec: String, sourceCodec: String?): Boolean {
        val codec = sourceCodec.orEmpty().lowercase()
        if (codec.isEmpty()) return false
        return when (targetCodec) {
            "m4a", "aac" -> codec.contains("aac") || codec.contains("mp4a")
            "opus" -> codec.contains("opus")
            "vorbis" -> codec.contains("vorbis")
            "mp3" -> codec.contains("mp3")
            else -> false
        }
    }

    /**
     * Resolves the `--audio-quality` value in kbps, or [NO_TRANSCODE].
     *
     * @param targetCodec the yt-dlp codec name, i.e. [AudioFormat.ytDlpCodec].
     * @param source probed source info, or `null` when probing failed.
     */
    fun resolve(
        targetCodec: String,
        source: AudioSourceInfo?,
        bitrate: BitrateSetting,
    ): Int {
        // Lossless targets are decoded to, never rate-limited.
        if (targetCodec == "flac" || targetCodec == "wav") return NO_TRANSCODE

        if (bitrate is BitrateSetting.Fixed) return bitrate.kbps

        if (isPassthrough(targetCodec, source?.codec)) return NO_TRANSCODE

        val sourceKbps = source?.averageBitrateKbps ?: 0.0

        if (targetCodec == "mp3") {
            // Note MP3 folds the unknown-source case in with "<= 160", unlike the branch
            // below. Preserved deliberately from the reference; see rules §5.
            return when {
                sourceKbps <= 0.0 || sourceKbps <= 160.0 -> 192
                sourceKbps <= 224.0 -> 256
                else -> 320
            }
        }

        return when {
            sourceKbps <= 0.0 -> 160
            sourceKbps <= 64.0 -> 96
            sourceKbps <= 160.0 -> 160
            sourceKbps <= 224.0 -> 224
            else -> 256
        }
    }

    /**
     * Whether probing the source stream can change the outcome.
     *
     * The reference probes before *every* audio download (`ProbeAudioSourceAsync`,
     * line 1501) — a second full YouTube extraction, including JS challenge solving —
     * even when the answer is already determined:
     *
     *  - a lossless target ignores source bitrate entirely;
     *  - a fixed bitrate overrides whatever the source turns out to be.
     *
     * Skipping those cases removes a whole network round-trip and extraction from a
     * large share of downloads, which on a phone is radio time and battery.
     */
    fun needsSourceProbe(format: AudioFormat, bitrate: BitrateSetting): Boolean {
        if (format.isLossless) return false
        if (bitrate is BitrateSetting.Fixed) return false
        return true
    }

    /**
     * Whether the given selection will re-encode. Drives the "this will re-encode"
     * warning shown before the user commits — the reference only reveals this after the
     * fact, in the log.
     */
    fun willTranscode(
        format: AudioFormat,
        source: AudioSourceInfo?,
        bitrate: BitrateSetting,
    ): Boolean {
        if (format.isLossless) return true // decode to PCM/FLAC is still real work
        return resolve(format.ytDlpCodec, source, bitrate) != NO_TRANSCODE
    }
}
