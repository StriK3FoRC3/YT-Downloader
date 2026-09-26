package com.strik3forc3.ytdownloader.core

/**
 * Builds the regexes handed to yt-dlp's `--replace-in-metadata title`.
 *
 * Contract: `docs/download-rules.md` §6.
 * Ported from `TitleRemovalPattern` (reference line 1910).
 */
object TitleCleanup {

    /**
     * Strips a leading `Artist - ` using hyphen, en dash or em dash, so
     * `Bruno Mars - The Lazy Song` becomes `The Lazy Song`.
     */
    const val ARTIST_PREFIX_PATTERN = """(?i)^\s*.+?\s+[-–—]\s+"""

    /**
     * Matches [phrase] anywhere inside a `(...)` or `[...]` group and deletes the whole
     * group, so `Song (Official Video) [HD]` loses only the bracketed parts.
     */
    fun bracketedPattern(phrase: String): String =
        """(?i)\s*[\(\[][^\)\]]*${escapeForPython(phrase)}[^\)\]]*[\)\]]"""

    /**
     * Escapes regex metacharacters for **Python's** `re` module, which is what yt-dlp
     * compiles these patterns with.
     *
     * Deliberately not `kotlin.text.Regex.escape`, which wraps its input in `\Q...\E` —
     * a Java-only construct that Python does not understand and would silently match
     * literally. The reference uses .NET's `Regex.Escape`; escaping this set is
     * compatible with both engines.
     */
    internal fun escapeForPython(literal: String): String = buildString(literal.length) {
        for (ch in literal) {
            if (ch in METACHARACTERS) append('\\')
            append(ch)
        }
    }

    private const val METACHARACTERS = """\.^$*+?()[]{}|"""

    /**
     * Preset bracketed tags offered in the profile editor (reference lines 491–499).
     * `HD / 4K` is one toggle covering two terms.
     */
    val PRESETS: List<TitlePreset> = listOf(
        TitlePreset("Official video", listOf("Official video")),
        TitlePreset("Official music video", listOf("Official music video")),
        TitlePreset("Official audio", listOf("Official audio")),
        TitlePreset("Lyrics", listOf("Lyrics")),
        TitlePreset("Lyric video", listOf("Lyric video")),
        TitlePreset("Music video", listOf("Music video")),
        TitlePreset("Visualizer", listOf("Visualizer")),
        TitlePreset("Audio", listOf("Audio")),
        TitlePreset("HD / 4K", listOf("HD", "4K")),
    )

    /**
     * Merges preset and custom phrases the way `FilterProfile.RebuildRules`
     * (reference line 2932) does: presets first, blanks dropped, case-insensitive
     * deduplication, original order preserved.
     */
    fun mergeRules(presetRules: List<String>, customRules: List<String>): List<String> {
        val seen = HashSet<String>()
        return (presetRules + customRules)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { seen.add(it.lowercase()) }
    }
}

data class TitlePreset(val label: String, val terms: List<String>)
