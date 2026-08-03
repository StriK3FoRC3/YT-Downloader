package com.strik3forc3.ytdownloader.core

/**
 * Builds yt-dlp `--format` selector strings.
 *
 * Contract: `docs/download-rules.md` §2 and §3.
 * Ported from `AudioFormatSelector` (reference line 1542) and the inline video selector
 * construction (reference lines 1425–1440).
 */
object FormatSelector {

    /**
     * Video selectors always offer a progressive fallback chain: preferred container,
     * then any codec at the same height cap, then a single pre-muxed stream.
     */
    fun video(format: VideoFormat, resolution: Resolution): String {
        val limit = resolution.heightFilter
        val preferred = when (format) {
            VideoFormat.MP4 ->
                "bestvideo$limit[ext=mp4]+bestaudio[ext=m4a]/bestvideo$limit+bestaudio/best$limit"
            VideoFormat.WEBM ->
                "bestvideo$limit[ext=webm]+bestaudio[ext=webm]/bestvideo$limit+bestaudio/best$limit"
            // MKV holds any codec combination, so no container constraint is useful.
            VideoFormat.MKV ->
                "bestvideo$limit+bestaudio/best$limit"
        }

        // The reference's chain ends at `best<limit>`, which selects only a pre-muxed
        // format. YouTube often publishes none above 360p, and a height cap can exclude
        // the ones that do exist — leaving yt-dlp to report "Requested format is not
        // available" for a video it could plainly have downloaded. `best*` accepts any
        // format, capped then uncapped, so a real match is never missed.
        return if (limit.isEmpty()) "$preferred/best*" else "$preferred/best*$limit/best*"
    }

    /**
     * Audio selection depends on the bitrate setting as well as the target format.
     *
     * A *fixed* bitrate means a lossy re-encode happens no matter what, so the selector
     * deliberately reaches for a source in the *other* codec family: re-encoding
     * Opus→AAC loses less than AAC→AAC, which would stack two generations of the same
     * lossy transform. With Automatic the reverse holds — match the source codec so
     * [BitrateLadder] can skip the transcode entirely.
     */
    fun audio(format: AudioFormat, bitrate: BitrateSetting): String {
        val forced = bitrate is BitrateSetting.Fixed

        if (forced) {
            when (format) {
                AudioFormat.M4A, AudioFormat.AAC ->
                    return "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best"
                AudioFormat.OPUS ->
                    return "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best"
                else -> Unit
            }
        }

        return when (format) {
            AudioFormat.M4A, AudioFormat.AAC ->
                "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio/best"
            AudioFormat.OPUS ->
                "bestaudio[acodec^=opus]/bestaudio[ext=webm]/bestaudio/best"
            // MP3, FLAC, WAV and OGG have no matching YouTube source stream, so there is
            // nothing to steer towards.
            else -> "bestaudio/best"
        }
    }
}
