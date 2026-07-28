package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §7. */
class MetadataArgsTest {

    private val allFields = MetadataField.entries.toSet()

    @Test
    fun `master toggle off disables everything`() {
        val args = MetadataArgs.build(enabled = false, fields = allFields)
        assertThat(args).containsExactly(
            "--no-embed-metadata", "--no-embed-info-json", "--no-embed-chapters",
        ).inOrder()
    }

    @Test
    fun `all fields selected blanks nothing`() {
        val args = MetadataArgs.build(enabled = true, fields = allFields)
        assertThat(args).containsExactly(
            "--embed-metadata", "--no-embed-info-json", "--embed-chapters",
        ).inOrder()
        assertThat(args).doesNotContain("--parse-metadata")
    }

    @Test
    fun `disabled fields are blanked with their full key sets`() {
        val args = MetadataArgs.build(enabled = true, fields = setOf(MetadataField.TITLE))

        assertThat(args).containsAtLeast("--embed-metadata", "--no-embed-info-json")
        // Title stays, so it is never blanked.
        assertThat(args).doesNotContain(":(?P<meta_title>)")
        // Multi-key fields blank every alias.
        assertThat(args).containsAtLeast(":(?P<meta_artist>)", ":(?P<meta_composer>)")
        assertThat(args).containsAtLeast(
            ":(?P<meta_album>)", ":(?P<meta_album_artist>)", ":(?P<meta_show>)",
        )
        assertThat(args).containsAtLeast(":(?P<meta_description>)", ":(?P<meta_synopsis>)")
        assertThat(args).containsAtLeast(":(?P<meta_purl>)", ":(?P<meta_comment>)")
        assertThat(args).containsAtLeast(":(?P<meta_track>)", ":(?P<meta_disc>)")
        assertThat(args).containsAtLeast(":(?P<meta_date>)", ":(?P<meta_genre>)")
    }

    @Test
    fun `every blanking key is preceded by parse-metadata`() {
        val args = MetadataArgs.build(enabled = true, fields = setOf(MetadataField.TITLE))
        args.forEachIndexed { index, token ->
            if (token.startsWith(":(?P<meta_")) {
                assertThat(args[index - 1]).isEqualTo("--parse-metadata")
            }
        }
    }

    @Test
    fun `chapters is independent of the standard fields`() {
        val chaptersOff = MetadataArgs.build(enabled = true, fields = setOf(MetadataField.TITLE))
        assertThat(chaptersOff).contains("--no-embed-chapters")

        val chaptersOn = MetadataArgs.build(
            enabled = true,
            fields = setOf(MetadataField.TITLE, MetadataField.CHAPTERS),
        )
        assertThat(chaptersOn).contains("--embed-chapters")
    }

    @Test
    fun `chapters-only selection still disables standard metadata`() {
        // Documented divergence: the Windows reference emits no metadata flag at all
        // here, so yt-dlp's default embedding silently applies. See rules §7.
        val args = MetadataArgs.build(enabled = true, fields = setOf(MetadataField.CHAPTERS))
        assertThat(args).containsExactly(
            "--no-embed-metadata", "--no-embed-info-json", "--embed-chapters",
        ).inOrder()
    }

    @Test
    fun `enabled with no fields selected disables metadata`() {
        val args = MetadataArgs.build(enabled = true, fields = emptySet())
        assertThat(args).containsExactly(
            "--no-embed-metadata", "--no-embed-info-json", "--no-embed-chapters",
        ).inOrder()
    }
}
