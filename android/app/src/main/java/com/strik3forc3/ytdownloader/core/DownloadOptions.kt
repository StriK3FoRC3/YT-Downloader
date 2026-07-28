package com.strik3forc3.ytdownloader.core

/**
 * User-selectable values, per `docs/download-rules.md` §1.
 *
 * Everything in `core` is pure Kotlin with no Android dependencies so the download rules
 * can be unit-tested on a plain JVM.
 */

enum class DownloadMode { AUDIO, VIDEO }

enum class AudioFormat(val id: String) {
    MP3("mp3"),
    M4A("m4a"),
    FLAC("flac"),
    WAV("wav"),
    OGG("ogg"),
    OPUS("opus"),
    AAC("aac");

    /**
     * The name yt-dlp's `--audio-format` expects, which is a *codec* rather than a
     * container: OGG is Vorbis. Everything else passes through unchanged.
     *
     * `docs/download-rules.md` §3.
     */
    val ytDlpCodec: String get() = if (this == OGG) "vorbis" else id

    /** FLAC and WAV are lossless containers — they are decoded to, never transcoded at a bitrate. */
    val isLossless: Boolean get() = this == FLAC || this == WAV

    /** Thumbnail embedding is unsupported by these containers (reference line 1412). */
    val supportsThumbnail: Boolean get() = this != WAV && this != AAC
}

enum class VideoFormat(val id: String) {
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm"),
}

enum class Resolution(val label: String, val height: Int?) {
    HIGHEST("Highest", null),
    R4320("4320p (8K)", 4320),
    R2160("2160p (4K)", 2160),
    R1440("1440p", 1440),
    R1080("1080p", 1080),
    R720("720p", 720),
    R480("480p", 480),
    R360("360p", 360);

    /** A ceiling, never an upscale. Empty when unconstrained. */
    val heightFilter: String get() = height?.let { "[height<=$it]" } ?: ""
}

/**
 * Automatic lets the passthrough matrix decide; Fixed forces a lossy re-encode.
 *
 * `docs/download-rules.md` §5.
 */
sealed interface BitrateSetting {
    data object Automatic : BitrateSetting

    @JvmInline
    value class Fixed(val kbps: Int) : BitrateSetting

    /** As stored in a profile and shown in the picker. */
    val label: String
        get() = when (this) {
            is Automatic -> "Automatic"
            is Fixed -> "$kbps kbps"
        }

    companion object {
        val ALLOWED_KBPS = listOf(320, 256, 224, 192, 160, 128, 96, 64, 48)

        /** Automatic first, then the ladder highest to lowest. */
        val ALL: List<BitrateSetting> =
            listOf(Automatic) + ALLOWED_KBPS.map { Fixed(it) }
    }
}

/** The nine embeddable metadata fields (reference `MetadataOptionControls`, line 2094). */
enum class MetadataField(val id: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE("Date"),
    DESCRIPTION("Description"),
    SOURCE("Source"),
    GENRE("Genre"),
    TRACK("Track"),
    CHAPTERS("Chapters");

    /**
     * Chapters is driven by `--embed-chapters` rather than by blanking metadata keys, so
     * it is excluded from the "any standard field selected" test in [MetadataArgs].
     */
    val isStandard: Boolean get() = this != CHAPTERS
}

/** Probed from the source stream; `null` throughout means "unknown source". */
data class AudioSourceInfo(
    val formatId: String?,
    val codec: String?,
    val averageBitrateKbps: Double,
)

/**
 * An immutable snapshot of every option affecting one download, taken when the item is
 * queued.
 *
 * This exists specifically to fix the reference implementation's worst structural bug:
 * `DownloadOneAsync` (line 1358) reads live WinForms control state from background
 * threads while up to five downloads run, so changing a dropdown mid-session silently
 * alters in-flight items. Nothing downstream of the queue may read mutable UI state.
 */
data class DownloadRequest(
    val url: String,
    val title: String,
    val mode: DownloadMode,
    val audioFormat: AudioFormat,
    val videoFormat: VideoFormat,
    val resolution: Resolution,
    val profile: Profile,
) {
    val bitrate: BitrateSetting get() = profile.bitrate
}
