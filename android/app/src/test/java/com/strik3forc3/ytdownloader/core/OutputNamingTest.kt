package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §10. */
class OutputNamingTest {

    private fun taken(vararg names: String): (String) -> Boolean =
        { it in names.toSet() }

    @Test
    fun `returns the desired name when free`() {
        assertThat(OutputNaming.uniqueName("Song.mp3", taken())).isEqualTo("Song.mp3")
    }

    @Test
    fun `numbering starts at 2 and inserts before the extension`() {
        assertThat(OutputNaming.uniqueName("Song.mp3", taken("Song.mp3")))
            .isEqualTo("Song (2).mp3")
    }

    @Test
    fun `skips over consecutive collisions`() {
        val existing = taken("Song.mp3", "Song (2).mp3", "Song (3).mp3")
        assertThat(OutputNaming.uniqueName("Song.mp3", existing)).isEqualTo("Song (4).mp3")
    }

    @Test
    fun `handles names with dots in the base`() {
        assertThat(OutputNaming.uniqueName("A.B.C.mp3", taken("A.B.C.mp3")))
            .isEqualTo("A.B.C (2).mp3")
    }

    @Test
    fun `handles names with no extension`() {
        assertThat(OutputNaming.uniqueName("Song", taken("Song"))).isEqualTo("Song (2)")
    }

    @Test
    fun `preserves multi-part extensions as the final segment only`() {
        // Matches the reference, which uses Path.GetExtension semantics.
        assertThat(OutputNaming.uniqueName("Song.f140.m4a", taken("Song.f140.m4a")))
            .isEqualTo("Song.f140 (2).m4a")
    }

    @Test
    fun `gives up rather than looping forever`() {
        val runCatching = runCatching { OutputNaming.uniqueName("Song.mp3") { true } }
        assertThat(runCatching.isFailure).isTrue()
    }
}
