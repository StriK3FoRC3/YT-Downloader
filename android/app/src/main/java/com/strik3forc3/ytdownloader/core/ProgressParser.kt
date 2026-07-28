package com.strik3forc3.ytdownloader.core

/**
 * Parses yt-dlp progress lines.
 *
 * Replaces the reference's scraping of human-readable output — `ExtractPercent`
 * (line 1976), `ExtractSpeedMegabytesValue` (line 1989) and phase counting on
 * `"[download] Destination:"` (line 1447) — which is fragile against yt-dlp's display
 * changes and, more importantly, cannot see post-processing at all. The reference's bar
 * therefore pins at 100% while FFmpeg merges or transcodes, which on a phone is the
 * slowest part of the job.
 *
 * We instead ask yt-dlp for machine-readable output via `--progress-template` and parse
 * that. See [PROGRESS_TEMPLATES].
 */
object ProgressParser {

    private const val DOWNLOAD_MARKER = "@@DL@@"
    private const val POSTPROCESS_MARKER = "@@PP@@"

    /**
     * The `--progress-template` arguments that produce the lines [parse] understands.
     *
     * Fields are pipe-separated and emitted on their own lines, so they cannot be
     * confused with yt-dlp's ordinary output. `NA` is yt-dlp's placeholder for a field
     * that is not yet known.
     */
    val PROGRESS_TEMPLATES: List<YtDlpArg> = listOf(
        YtDlpArg(
            "--progress-template",
            "download:$DOWNLOAD_MARKER%(progress.status)s|%(progress.downloaded_bytes)s|" +
                "%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|" +
                "%(progress.speed)s|%(progress.eta)s",
        ),
        YtDlpArg(
            "--progress-template",
            "postprocess:$POSTPROCESS_MARKER%(progress.status)s|%(progress.postprocessor)s",
        ),
    )

    sealed interface Event {
        /** A single stream's transfer progress. */
        data class Download(
            val status: String,
            val downloadedBytes: Long?,
            val totalBytes: Long?,
            val speedBytesPerSecond: Double?,
            val etaSeconds: Long?,
        ) : Event {
            val finished: Boolean get() = status.equals("finished", ignoreCase = true)

            /** Fraction of this one stream, or null when the total size is unknown. */
            val fraction: Float?
                get() {
                    val total = totalBytes ?: return null
                    val done = downloadedBytes ?: return null
                    if (total <= 0L) return null
                    return (done.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                }
        }

        /** FFmpeg merging, transcoding or embedding — invisible to the reference. */
        data class PostProcess(val status: String, val postProcessor: String?) : Event {
            val finished: Boolean get() = status.equals("finished", ignoreCase = true)
        }
    }

    /** Returns null for any line that is not one of our templated progress lines. */
    fun parse(line: String?): Event? {
        val text = line?.trim().orEmpty()
        return when {
            text.startsWith(DOWNLOAD_MARKER) ->
                parseDownload(text.removePrefix(DOWNLOAD_MARKER))
            text.startsWith(POSTPROCESS_MARKER) ->
                parsePostProcess(text.removePrefix(POSTPROCESS_MARKER))
            else -> null
        }
    }

    private fun parseDownload(payload: String): Event.Download? {
        val parts = payload.split('|')
        if (parts.size < 6) return null
        val status = parts[0].trim()
        if (status.isEmpty()) return null

        // total_bytes is exact but often unknown; total_bytes_estimate is the fallback.
        val total = parts[2].toLongOrNull() ?: parts[3].toDoubleOrNull()?.toLong()

        return Event.Download(
            status = status,
            downloadedBytes = parts[1].toLongOrNull(),
            totalBytes = total,
            speedBytesPerSecond = parts[4].toDoubleOrNull(),
            etaSeconds = parts[5].toLongOrNull(),
        )
    }

    private fun parsePostProcess(payload: String): Event.PostProcess? {
        val parts = payload.split('|')
        val status = parts.getOrNull(0)?.trim().orEmpty()
        if (status.isEmpty()) return null
        val processor = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() && it != "NA" }
        return Event.PostProcess(status, processor)
    }
}
