package com.strik3forc3.ytdownloader.core

/**
 * Pulls links out of text handed over by the system share sheet.
 *
 * Shared text is rarely just a URL. YouTube sends the video title, a blank line, then the
 * link; other apps prepend "Check this out"; some include hashtags or a trailing full
 * stop. Treating the whole payload as a URL therefore fails for the most common case of
 * all, which is sharing straight from the YouTube app.
 */
object SharedText {

    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** Characters that commonly trail a pasted link but are not part of it. */
    private const val TRAILING = ".,;:!?)]}'\"»”’"

    /**
     * Every distinct link in [text], in order of appearance.
     *
     * Deduplicated by canonical identity, so a share carrying both the short and long
     * form of one video yields a single entry.
     */
    fun extractLinks(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return URL.findAll(text)
            .map { it.value.trimEnd { char -> char in TRAILING } }
            .filter { QueueParser.isWebLink(it) }
            .filter { seen.add(YouTubeUrl.canonicalKey(it)) }
            .toList()
    }

    /**
     * Formats extracted links for the queue's text input, which is one link per line.
     */
    fun toQueueInput(text: String?): String = extractLinks(text).joinToString("\n")
}
