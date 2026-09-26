package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeUrlTest {

    @Test
    fun `share tracking parameters do not create separate identities`() {
        // The share sheet stamps a fresh ?si= on every share, so the same video shared
        // twice used to enter the queue twice.
        val a = YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo?si=abc123")
        val b = YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo?si=zzz999")
        val c = YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo")
        assertThat(a).isEqualTo(b)
        assertThat(b).isEqualTo(c)
    }

    @Test
    fun `every url shape for one video agrees`() {
        val expected = "youtube:Aq5WXmQQooo"
        assertThat(YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo")).isEqualTo(expected)
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/watch?v=Aq5WXmQQooo")).isEqualTo(expected)
        assertThat(YouTubeUrl.canonicalKey("https://m.youtube.com/watch?v=Aq5WXmQQooo")).isEqualTo(expected)
        assertThat(YouTubeUrl.canonicalKey("https://youtube.com/shorts/Aq5WXmQQooo")).isEqualTo(expected)
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/embed/Aq5WXmQQooo")).isEqualTo(expected)
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/live/Aq5WXmQQooo")).isEqualTo(expected)
    }

    @Test
    fun `timestamps and extra parameters are ignored`() {
        assertThat(YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo?t=42"))
            .isEqualTo("youtube:Aq5WXmQQooo")
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/watch?v=Aq5WXmQQooo&feature=share&t=90"))
            .isEqualTo("youtube:Aq5WXmQQooo")
    }

    @Test
    fun `different videos stay distinct`() {
        assertThat(YouTubeUrl.canonicalKey("https://youtu.be/Aq5WXmQQooo"))
            .isNotEqualTo(YouTubeUrl.canonicalKey("https://youtu.be/Bq5WXmQQooo"))
    }

    @Test
    fun `a video inside a playlist is identified by the video`() {
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/watch?v=Aq5WXmQQooo&list=PLxyz"))
            .isEqualTo("youtube:Aq5WXmQQooo")
    }

    @Test
    fun `a playlist with no video is identified by the playlist`() {
        assertThat(YouTubeUrl.canonicalKey("https://www.youtube.com/playlist?list=PLxyz&si=abc"))
            .isEqualTo("youtube:list:PLxyz")
    }

    @Test
    fun `malformed ids are not mistaken for videos`() {
        // Too short to be a video id, so it must not collapse to one.
        assertThat(YouTubeUrl.canonicalKey("https://youtu.be/short"))
            .doesNotContain("youtube:short")
    }

    @Test
    fun `non-youtube links fall back to a stripped url`() {
        val key = YouTubeUrl.canonicalKey("https://Vimeo.com/22439234/?si=tracking")
        assertThat(key).isEqualTo("https://vimeo.com/22439234")
    }

    @Test
    fun `garbage input does not throw`() {
        assertThat(YouTubeUrl.canonicalKey("not a url")).isEqualTo("not a url")
        assertThat(YouTubeUrl.canonicalKey("")).isEqualTo("")
    }

    @Test
    fun `input normalisation collapses re-shared duplicates`() {
        val pasted = """
            https://youtu.be/Aq5WXmQQooo?si=one
            https://youtu.be/Aq5WXmQQooo?si=two
            https://www.youtube.com/watch?v=Aq5WXmQQooo
            https://youtu.be/Bq5WXmQQooo
        """.trimIndent()

        assertThat(QueueParser.normaliseInput(pasted)).hasSize(2)
    }
}
