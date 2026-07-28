package com.strik3forc3.ytdownloader.core

/**
 * A yt-dlp argument and its values. Flags and bare positionals carry no values; most
 * options carry one; `--replace-in-metadata` carries three (field, pattern, replacement).
 *
 * Modelling arguments as structured tokens rather than a concatenated command string is
 * the fix for the reference's hand-rolled `Quote()` (line 2066), which escapes `"` but
 * not the backslashes preceding it — so a title or URL containing `\"` or a trailing
 * backslash can corrupt the command line. Nothing here is ever quoted, joined or
 * shell-parsed; every value reaches the process as a discrete argv entry.
 */
data class YtDlpArg(val option: String, val values: List<String> = emptyList()) {
    constructor(option: String, value: String) : this(option, listOf(value))

    fun toArgv(): List<String> = buildList {
        add(option)
        addAll(values)
    }
}

fun List<YtDlpArg>.toArgv(): List<String> = flatMap { it.toArgv() }

/**
 * Assembles the rule-derived yt-dlp arguments for one download.
 *
 * Contract: `docs/download-rules.md` §2–§8.
 * Ported from `DownloadOneAsync` (reference lines 1381–1443).
 *
 * Transport concerns — progress template, cookies, the output directory — are supplied by
 * the caller, so this stays a pure function of the request plus probed source info and can
 * be tested without Android or a network.
 */
object ArgsBuilder {

    /**
     * @param request the immutable snapshot taken when the item was queued.
     * @param audioSource probed source info, or `null` when probing failed.
     * @param outputTemplate the fully-qualified `--output` template.
     */
    fun build(
        request: DownloadRequest,
        audioSource: AudioSourceInfo?,
        outputTemplate: String,
    ): List<YtDlpArg> {
        val args = mutableListOf<YtDlpArg>()
        val profile = request.profile

        args += YtDlpArg("--ignore-config")
        args += YtDlpArg("--no-playlist")
        args += YtDlpArg("--newline")
        // Stricter than Android requires, but it keeps names portable across SAF targets
        // including FAT32 external SD cards.
        args += YtDlpArg("--windows-filenames")
        args += YtDlpArg("--break-match-filters", "!is_live")

        args += MetadataArgs.build(profile.metadataEnabled, profile.metadataFields)
            .toArgPairs()

        if (profile.cleanTitles) {
            for (rule in profile.rules) {
                args += replaceInTitle(TitleCleanup.bracketedPattern(rule))
            }
            if (profile.removeArtistPrefix) {
                args += replaceInTitle(TitleCleanup.ARTIST_PREFIX_PATTERN)
            }
        }

        args += YtDlpArg("--output", outputTemplate)

        when (request.mode) {
            DownloadMode.AUDIO -> args += audioArgs(request, audioSource)
            DownloadMode.VIDEO -> args += videoArgs(request)
        }

        args += YtDlpArg(request.url)
        return args
    }

    private fun audioArgs(
        request: DownloadRequest,
        audioSource: AudioSourceInfo?,
    ): List<YtDlpArg> {
        val args = mutableListOf<YtDlpArg>()
        val format = request.audioFormat

        if (request.profile.embedThumbnail && format.supportsThumbnail) {
            args += YtDlpArg("--embed-thumbnail")
        }

        args += YtDlpArg("--extract-audio")
        args += YtDlpArg("--format", FormatSelector.audio(format, request.bitrate))
        args += YtDlpArg("--audio-format", format.ytDlpCodec)

        val kbps = BitrateLadder.resolve(format.ytDlpCodec, audioSource, request.bitrate)
        if (kbps != BitrateLadder.NO_TRANSCODE) {
            args += YtDlpArg("--audio-quality", "${kbps}K")
        }

        return args
    }

    private fun videoArgs(request: DownloadRequest): List<YtDlpArg> {
        val args = mutableListOf<YtDlpArg>()

        if (request.profile.embedThumbnail) {
            args += YtDlpArg("--embed-thumbnail")
        }

        args += YtDlpArg("--format", FormatSelector.video(request.videoFormat, request.resolution))
        args += YtDlpArg("--merge-output-format", request.videoFormat.id)

        return args
    }

    /**
     * Builds the queue-enumeration arguments for one input link
     * (reference `BuildQueueAsync`, line 1257).
     */
    fun buildQueueProbe(url: String): List<YtDlpArg> = listOf(
        YtDlpArg("--ignore-config"),
        YtDlpArg("--flat-playlist"),
        YtDlpArg("--print", QueueParser.PRINT_TEMPLATE),
        YtDlpArg(url),
    )

    /** `--replace-in-metadata FIELD PATTERN REPLACEMENT`, deleting the match. */
    private fun replaceInTitle(pattern: String): YtDlpArg =
        YtDlpArg("--replace-in-metadata", listOf("title", pattern, ""))

    /**
     * [MetadataArgs] emits a flat token list; regroup it so option/value pairs survive
     * into [YtDlpArg]. Only `--parse-metadata` takes a value.
     */
    private fun List<String>.toArgPairs(): List<YtDlpArg> {
        val result = mutableListOf<YtDlpArg>()
        var i = 0
        while (i < size) {
            val token = this[i]
            if (token == "--parse-metadata" && i + 1 < size) {
                result += YtDlpArg(token, this[i + 1])
                i += 2
            } else {
                result += YtDlpArg(token)
                i++
            }
        }
        return result
    }
}
