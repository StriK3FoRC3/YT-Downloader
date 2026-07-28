package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedTextTest {

    @Test
    fun `extracts the link from a YouTube app share`() {
        // What the YouTube app actually sends: title, blank line, then the link.
        val shared = "KIBI - Look At Me\n\nhttps://youtu.be/9tQeuQb9b28?si=Xa1bC2dE3f"
        assertThat(SharedText.extractLinks(shared))
            .containsExactly("https://youtu.be/9tQeuQb9b28?si=Xa1bC2dE3f")
    }

    @Test
    fun `extracts a bare link`() {
        assertThat(SharedText.extractLinks("https://youtu.be/9tQeuQb9b28"))
            .containsExactly("https://youtu.be/9tQeuQb9b28")
    }

    @Test
    fun `extracts a link surrounded by prose`() {
        val shared = "Check this out https://youtu.be/9tQeuQb9b28 it's great"
        assertThat(SharedText.extractLinks(shared))
            .containsExactly("https://youtu.be/9tQeuQb9b28")
    }

    @Test
    fun `strips punctuation that trails a sentence`() {
        assertThat(SharedText.extractLinks("Watch https://youtu.be/9tQeuQb9b28."))
            .containsExactly("https://youtu.be/9tQeuQb9b28")
        assertThat(SharedText.extractLinks("(https://youtu.be/9tQeuQb9b28)"))
            .containsExactly("https://youtu.be/9tQeuQb9b28")
    }

    @Test
    fun `collapses the same video shared in two forms`() {
        val shared = "https://youtu.be/9tQeuQb9b28?si=one and https://www.youtube.com/watch?v=9tQeuQb9b28"
        assertThat(SharedText.extractLinks(shared)).hasSize(1)
    }

    @Test
    fun `keeps several distinct links in order`() {
        val shared = "https://youtu.be/aaaaaaaaaaa\nhttps://youtu.be/bbbbbbbbbbb"
        assertThat(SharedText.extractLinks(shared))
            .containsExactly("https://youtu.be/aaaaaaaaaaa", "https://youtu.be/bbbbbbbbbbb")
            .inOrder()
    }

    @Test
    fun `text with no link yields nothing`() {
        assertThat(SharedText.extractLinks("just some words")).isEmpty()
        assertThat(SharedText.extractLinks("")).isEmpty()
        assertThat(SharedText.extractLinks(null)).isEmpty()
    }

    @Test
    fun `queue input is one link per line and parses back`() {
        val shared = "Title\n\nhttps://youtu.be/aaaaaaaaaaa\nhttps://youtu.be/bbbbbbbbbbb"
        val input = SharedText.toQueueInput(shared)
        assertThat(input.lines()).hasSize(2)
        // Round-trips through the same normaliser the paste field uses.
        assertThat(QueueParser.normaliseInput(input)).hasSize(2)
    }
}
