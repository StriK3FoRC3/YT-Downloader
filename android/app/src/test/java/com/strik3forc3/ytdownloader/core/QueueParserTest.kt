package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Asserts `docs/download-rules.md` §8. */
class QueueParserTest {

    private fun line(title: String, url: String, status: String) = "$title\t$url\t$status"

    @Test
    fun `parses a normal entry`() {
        val entries = QueueParser.parse(line("Song", "https://youtu.be/abc", "not_live"))
        assertThat(entries).containsExactly(QueueParser.Entry.Item("Song", "https://youtu.be/abc"))
    }

    @Test
    fun `splits from the right so tabs in titles survive`() {
        val entries = QueueParser.parse(line("Weird\tTitle", "https://youtu.be/abc", "not_live"))
        val item = entries.single() as QueueParser.Entry.Item
        assertThat(item.title).isEqualTo("Weird\tTitle")
        assertThat(item.url).isEqualTo("https://youtu.be/abc")
    }

    @Test
    fun `rejects live and upcoming streams with their own reasons`() {
        val live = QueueParser.parse(line("Stream", "https://youtu.be/l", "is_live")).single()
        assertThat(live).isInstanceOf(QueueParser.Entry.Rejected::class.java)
        assertThat((live as QueueParser.Entry.Rejected).reason).isEqualTo(QueueParser.REASON_LIVE)

        val soon = QueueParser.parse(line("Premiere", "https://youtu.be/u", "is_upcoming")).single()
        assertThat((soon as QueueParser.Entry.Rejected).reason).isEqualTo(QueueParser.REASON_UPCOMING)
    }

    @Test
    fun `drops malformed lines and non-http urls`() {
        val output = listOf(
            "no tabs at all",
            "only\tone-tab",
            line("Bad", "ftp://example.com/x", "not_live"),
            line("Good", "https://youtu.be/ok", "not_live"),
        ).joinToString("\n")

        assertThat(QueueParser.parse(output))
            .containsExactly(QueueParser.Entry.Item("Good", "https://youtu.be/ok"))
    }

    @Test
    fun `parses a multi-entry playlist`() {
        val output = listOf(
            line("One", "https://youtu.be/1", "not_live"),
            line("Two", "https://youtu.be/2", "is_live"),
            line("Three", "https://youtu.be/3", "not_live"),
        ).joinToString("\n")

        val entries = QueueParser.parse(output)
        assertThat(entries).hasSize(3)
        assertThat(entries.filterIsInstance<QueueParser.Entry.Item>()).hasSize(2)
        assertThat(entries.filterIsInstance<QueueParser.Entry.Rejected>()).hasSize(1)
    }

    @Test
    fun `web link check accepts only absolute http and https`() {
        assertThat(QueueParser.isWebLink("https://youtu.be/abc")).isTrue()
        assertThat(QueueParser.isWebLink("http://youtu.be/abc")).isTrue()
        assertThat(QueueParser.isWebLink("ftp://example.com")).isFalse()
        assertThat(QueueParser.isWebLink("youtu.be/abc")).isFalse()
        assertThat(QueueParser.isWebLink("")).isFalse()
        assertThat(QueueParser.isWebLink(null)).isFalse()
        assertThat(QueueParser.isWebLink("not a url at all")).isFalse()
    }

    @Test
    fun `input normalisation trims filters and deduplicates`() {
        val raw = """
            https://youtu.be/a
              https://youtu.be/b
            not-a-link
            HTTPS://YOUTU.BE/A
            https://youtu.be/a
        """.trimIndent()

        assertThat(QueueParser.normaliseInput(raw))
            .containsExactly("https://youtu.be/a", "https://youtu.be/b").inOrder()
    }

    @Test
    fun `queue deduplication is case-insensitive and keeps the first occurrence`() {
        val items = listOf(
            QueueParser.Entry.Item("First", "https://youtu.be/a"),
            QueueParser.Entry.Item("Duplicate", "HTTPS://YOUTU.BE/A"),
            QueueParser.Entry.Item("Second", "https://youtu.be/b"),
        )
        assertThat(QueueParser.dedupeByUrl(items).map { it.title })
            .containsExactly("First", "Second").inOrder()
    }
}
