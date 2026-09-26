package com.strik3forc3.ytdownloader.core

import java.net.URI

/**
 * Canonicalises YouTube links so the same video is recognised however it was shared.
 *
 * The share sheet appends a per-share `?si=` tracking parameter, so sharing one video
 * twice produces two different URL strings. Deduplicating on the raw string therefore
 * lets the same video into the queue repeatedly — which is exactly what happens in
 * practice, because the obvious way to add links is to share them one at a time.
 */
object YouTubeUrl {

    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** Query parameters that identify content, as opposed to tracking the referrer. */
    private val MEANINGFUL_PARAMS = setOf("v", "list")

    /**
     * A stable identity for deduplication.
     *
     * Returns `youtube:<videoId>` or `youtube:list:<playlistId>` for recognised YouTube
     * links, and a lowercased URL stripped of tracking parameters for anything else.
     */
    fun canonicalKey(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return trimmed.lowercase()

        val host = uri.host.orEmpty().removePrefix("www.").removePrefix("m.").lowercase()
        val params = parseQuery(uri.rawQuery)

        videoId(host, uri.path.orEmpty(), params)?.let { return "youtube:$it" }

        // A playlist link with no video id identifies the playlist itself.
        if (host.endsWith("youtube.com")) {
            params["list"]?.let { return "youtube:list:$it" }
        }

        return stripTracking(uri, params, trimmed)
    }

    private fun videoId(host: String, path: String, params: Map<String, String>): String? {
        val candidate = when {
            host == "youtu.be" -> path.trim('/').substringBefore('/')
            !host.endsWith("youtube.com") -> null
            path.startsWith("/watch") -> params["v"]
            path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
            path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
            path.startsWith("/live/") -> path.removePrefix("/live/").substringBefore('/')
            else -> null
        }
        return candidate?.takeIf { VIDEO_ID.matches(it) }
    }

    private fun stripTracking(uri: URI, params: Map<String, String>, original: String): String {
        // URI("") parses without throwing but leaves scheme and host null, which would
        // otherwise render as the literal string "null://null".
        val scheme = uri.scheme ?: return original.lowercase()
        val host = uri.host ?: return original.lowercase()

        val kept = params.filterKeys { it in MEANINGFUL_PARAMS }
        val base = "$scheme://$host${uri.path.orEmpty()}".lowercase().trimEnd('/')
        if (kept.isEmpty()) return base
        return base + "?" + kept.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty()
            .split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                part.substring(0, separator) to part.substring(separator + 1)
            }
            .toMap()
}
