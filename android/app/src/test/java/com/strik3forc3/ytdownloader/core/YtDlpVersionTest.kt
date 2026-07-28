package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class YtDlpVersionTest {

    private val today = LocalDate.of(2026, 7, 28)

    @Test
    fun `parses the date-based version`() {
        assertThat(YtDlpVersion.releaseDate("2026.07.04")).isEqualTo(LocalDate.of(2026, 7, 4))
    }

    @Test
    fun `ignores a nightly suffix`() {
        assertThat(YtDlpVersion.releaseDate("2026.07.23.234303"))
            .isEqualTo(LocalDate.of(2026, 7, 23))
    }

    @Test
    fun `the version bundled with youtubedl-android is stale`() {
        // This is the real observed case: a fresh install shipped 2025.11.12, which could
        // no longer solve YouTube's player challenges.
        assertThat(YtDlpVersion.ageInDays("2025.11.12", today)).isEqualTo(258)
        assertThat(YtDlpVersion.isStale("2025.11.12", today)).isTrue()
    }

    @Test
    fun `a recent release is not stale`() {
        assertThat(YtDlpVersion.isStale("2026.07.23", today)).isFalse()
        assertThat(YtDlpVersion.isStale("2026.07.04", today)).isFalse()
    }

    @Test
    fun `the staleness boundary is inclusive of the threshold day`() {
        val exactly = today.minusDays(YtDlpVersion.STALE_AFTER_DAYS)
        assertThat(YtDlpVersion.isStale(exactly.toString().replace('-', '.'), today)).isFalse()

        val oneMore = today.minusDays(YtDlpVersion.STALE_AFTER_DAYS + 1)
        assertThat(YtDlpVersion.isStale(oneMore.toString().replace('-', '.'), today)).isTrue()
    }

    @Test
    fun `an unreadable version counts as stale`() {
        // Better to attempt an unnecessary update than to leave a broken install alone.
        assertThat(YtDlpVersion.isStale(null, today)).isTrue()
        assertThat(YtDlpVersion.isStale("", today)).isTrue()
        assertThat(YtDlpVersion.isStale("unknown", today)).isTrue()
    }

    @Test
    fun `a future-dated version is not stale`() {
        assertThat(YtDlpVersion.isStale("2026.12.01", today)).isFalse()
    }
}
