package com.strik3forc3.ytdownloader.core

import java.net.URI

/**
 * Parses yt-dlp's `--flat-playlist --print` output into a download queue.
 *
 * Contract: `docs/download-rules.md` §8.
 * Ported from `BuildQueueAsync` (reference line 1252) and `IsWebLink` (line 1898).
 */
object QueueParser {

    /** The print template whose output [parse] consumes. */
    const val PRINT_TEMPLATE = "%(title)s\t%(webpage_url)s\t%(live_status|not_live)s"

    private const val LIVE = "is_live"
    private const val UPCOMING = "is_upcoming"

    const val REASON_LIVE = "Active YouTube livestreams cannot be downloaded."
    const val REASON_UPCOMING = "Upcoming YouTube livestreams cannot be downloaded."

    sealed interface Entry {
        val title: String
        val url: String

        data class Item(override val title: String, override val url: String) : Entry

        /** Recorded as a visible failure rather than silently dropped. */
        data class Rejected(
            override val title: String,
            override val url: String,
            val reason: String,
        ) : Entry
    }

    /** Accepts only absolute http/https URIs. */
    fun isWebLink(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        return runCatching {
            val uri = URI(trimmed)
            uri.isAbsolute && (uri.scheme == "http" || uri.scheme == "https")
        }.getOrDefault(false)
    }

    /**
     * Trims, keeps only web links, and deduplicates by canonical identity — so the same
     * video shared twice, carrying different `?si=` tracking parameters, is one entry.
     */
    fun normaliseInput(raw: String): List<String> {
        val seen = HashSet<String>()
        return raw.split('\n')
            .map { it.trim() }
            .filter { isWebLink(it) }
            .filter { seen.add(YouTubeUrl.canonicalKey(it)) }
    }

    /**
     * Splits each line from the **right**: the last tab is live status, the one before it
     * is the URL, and everything remaining is the title — which may itself contain tabs.
     */
    fun parse(output: String): List<Entry> = output
        .split('\r', '\n')
        .filter { it.isNotEmpty() }
        .mapNotNull { parseLine(it) }

    private fun parseLine(line: String): Entry? {
        val statusTab = line.lastIndexOf('\t')
        if (statusTab <= 0) return null
        val liveStatus = line.substring(statusTab + 1).trim()

        val mediaFields = line.substring(0, statusTab)
        val urlTab = mediaFields.lastIndexOf('\t')
        if (urlTab <= 0) return null

        val title = mediaFields.substring(0, urlTab).trim()
        val url = mediaFields.substring(urlTab + 1).trim()
        if (!url.startsWith("http", ignoreCase = true)) return null

        return when {
            liveStatus.equals(LIVE, ignoreCase = true) -> Entry.Rejected(title, url, REASON_LIVE)
            liveStatus.equals(UPCOMING, ignoreCase = true) -> Entry.Rejected(title, url, REASON_UPCOMING)
            else -> Entry.Item(title, url)
        }
    }

    /** Deduplicates the final queue by canonical identity, keeping the first occurrence. */
    fun dedupeByUrl(entries: List<Entry.Item>): List<Entry.Item> {
        val seen = HashSet<String>()
        return entries.filter { seen.add(YouTubeUrl.canonicalKey(it.url)) }
    }
}
