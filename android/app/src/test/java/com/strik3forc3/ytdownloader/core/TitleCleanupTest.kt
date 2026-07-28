package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §6. */
class TitleCleanupTest {

    private fun applyBracketed(title: String, phrase: String): String =
        Regex(TitleCleanup.bracketedPattern(phrase)).replace(title, "")

    @Test
    fun `bracketed pattern matches the reference form`() {
        assertThat(TitleCleanup.bracketedPattern("Lyrics"))
            .isEqualTo("""(?i)\s*[\(\[][^\)\]]*Lyrics[^\)\]]*[\)\]]""")
    }

    @Test
    fun `removes the whole bracketed group in either bracket style`() {
        assertThat(applyBracketed("Song (Official Video)", "Official video")).isEqualTo("Song")
        assertThat(applyBracketed("Song [Official Video]", "Official video")).isEqualTo("Song")
    }

    @Test
    fun `matches the phrase anywhere inside the group`() {
        assertThat(applyBracketed("Song (Best Official Video Ever)", "Official video"))
            .isEqualTo("Song")
    }

    @Test
    fun `leaves the phrase alone outside brackets`() {
        assertThat(applyBracketed("Official Video Of The Year", "Official video"))
            .isEqualTo("Official Video Of The Year")
    }

    @Test
    fun `is case-insensitive`() {
        assertThat(applyBracketed("Song (OFFICIAL VIDEO)", "Official video")).isEqualTo("Song")
        assertThat(applyBracketed("Song (official video)", "OFFICIAL VIDEO")).isEqualTo("Song")
    }

    @Test
    fun `escapes metacharacters instead of interpreting them`() {
        // Without escaping, "C++" would be an invalid quantifier and "." a wildcard.
        assertThat(applyBracketed("Track (C++ Remix)", "C++")).isEqualTo("Track")
        assertThat(applyBracketed("Track (Feat. X)", "Feat.")).isEqualTo("Track")
        assertThat(applyBracketed("Track (Feat_ X)", "Feat.")).isEqualTo("Track (Feat_ X)")
    }

    @Test
    fun `escaping targets python's regex engine not java's`() {
        // yt-dlp compiles these with Python's `re`, which does not understand \Q...\E —
        // so kotlin.text.Regex.escape would silently match literally. Guard against a
        // future refactor reaching for it.
        val escaped = TitleCleanup.escapeForPython("a.b*c")
        assertThat(escaped).isEqualTo("""a\.b\*c""")
        assertThat(escaped).doesNotContain("""\Q""")
        assertThat(escaped).doesNotContain("""\E""")
    }

    @Test
    fun `artist prefix strips across all three dash characters`() {
        val regex = Regex(TitleCleanup.ARTIST_PREFIX_PATTERN)
        assertThat(regex.replace("Bruno Mars - The Lazy Song", "")).isEqualTo("The Lazy Song")
        assertThat(regex.replace("Bruno Mars – The Lazy Song", "")).isEqualTo("The Lazy Song")
        assertThat(regex.replace("Bruno Mars — The Lazy Song", "")).isEqualTo("The Lazy Song")
    }

    @Test
    fun `artist prefix is non-greedy and leaves undashed titles alone`() {
        val regex = Regex(TitleCleanup.ARTIST_PREFIX_PATTERN)
        assertThat(regex.replace("A - B - C", "")).isEqualTo("B - C")
        assertThat(regex.replace("The Lazy Song", "")).isEqualTo("The Lazy Song")
    }

    @Test
    fun `merge puts presets first and deduplicates case-insensitively`() {
        val merged = TitleCleanup.mergeRules(
            presetRules = listOf("Official video", "Lyrics"),
            customRules = listOf("  ", "official VIDEO", "Remastered"),
        )
        assertThat(merged).containsExactly("Official video", "Lyrics", "Remastered").inOrder()
    }

    @Test
    fun `presets cover the nine reference toggles`() {
        assertThat(TitleCleanup.PRESETS).hasSize(9)
        assertThat(TitleCleanup.PRESETS.last().terms).containsExactly("HD", "4K")
    }
}
