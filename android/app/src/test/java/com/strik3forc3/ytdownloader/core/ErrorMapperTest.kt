package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §9. */
class ErrorMapperTest {

    @Test
    fun `strips everything up to and including the ERROR prefix`() {
        assertThat(ErrorMapper.friendly("ERROR: Video unavailable"))
            .isEqualTo("Video unavailable")
        assertThat(ErrorMapper.friendly("[youtube] abc: ERROR: Video unavailable"))
            .isEqualTo("Video unavailable")
    }

    @Test
    fun `prefers the last ERROR line over later warnings`() {
        val stderr = """
            WARNING: unable to extract something
            ERROR: Sign in to confirm you're not a bot
            WARNING: falling back
        """.trimIndent()
        assertThat(ErrorMapper.friendly(stderr))
            .isEqualTo("Sign in to confirm you're not a bot")
    }

    @Test
    fun `prefers the last ERROR when several are present`() {
        val stderr = "ERROR: first problem\nERROR: second problem"
        assertThat(ErrorMapper.friendly(stderr)).isEqualTo("second problem")
    }

    @Test
    fun `falls back to the last line when nothing is marked ERROR`() {
        assertThat(ErrorMapper.friendly("something odd\nlast line here"))
            .isEqualTo("last line here")
    }

    @Test
    fun `reports unknown for empty and unusable input`() {
        assertThat(ErrorMapper.friendly(null)).isEqualTo("Unknown download error")
        assertThat(ErrorMapper.friendly("")).isEqualTo("Unknown download error")
        assertThat(ErrorMapper.friendly("\n\n")).isEqualTo("Unknown download error")
        // Divergence from the reference, which would return an empty string here.
        assertThat(ErrorMapper.friendly("ERROR:")).isEqualTo("Unknown download error")
    }

    @Test
    fun `format-unavailable is explained as a sign-in problem`() {
        // yt-dlp's wording points at the format setting, but on YouTube this means the
        // extractor was served no playable formats at all. Changing format cannot help.
        val described = ErrorMapper.describe(
            "ERROR: [youtube] abc: Requested format is not available. Use --list-formats"
        )
        assertThat(described).contains("Requested format is not available")
        assertThat(described).contains("Sign in under Settings")
    }

    @Test
    fun `bot check is explained`() {
        val described = ErrorMapper.describe("ERROR: Sign in to confirm you're not a bot")
        assertThat(described).contains("Sign in under Settings")
    }

    @Test
    fun `unavailable and private videos are not blamed on sign-in`() {
        val unavailable = ErrorMapper.describe("ERROR: Video unavailable")
        assertThat(unavailable).contains("no longer available")
        assertThat(unavailable).doesNotContain("Sign in under Settings")

        val private = ErrorMapper.describe("ERROR: Private video. Sign in if you've been granted access")
        assertThat(private).contains("private")
    }

    @Test
    fun `an unrecognised error gets no invented advice`() {
        val described = ErrorMapper.describe("ERROR: something entirely novel happened")
        assertThat(described).isEqualTo("something entirely novel happened")
    }

    @Test
    fun `truncates to 220 characters including the ellipsis`() {
        val long = "ERROR: " + "x".repeat(500)
        val result = ErrorMapper.friendly(long)
        assertThat(result).hasLength(220)
        assertThat(result).endsWith("...")
    }

    @Test
    fun `leaves a message at exactly the limit intact`() {
        val exact = "x".repeat(220)
        val result = ErrorMapper.friendly("ERROR: $exact")
        assertThat(result).isEqualTo(exact)
        assertThat(result).doesNotContain("...")
    }
}
