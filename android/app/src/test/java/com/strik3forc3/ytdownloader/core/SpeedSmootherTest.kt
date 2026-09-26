package com.strik3forc3.ytdownloader.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeedSmootherTest {

    @Test
    fun `first sample is taken as-is`() {
        assertThat(SpeedSmoother().update(2_000_000.0)).isWithin(0.1).of(2_000_000.0)
    }

    @Test
    fun `a single spike barely moves the average`() {
        val smoother = SpeedSmoother()
        repeat(10) { smoother.update(1_000_000.0) }

        // yt-dlp reports per chunk, so one 5x burst is noise, not a real rate change.
        val afterSpike = smoother.update(5_000_000.0)
        assertThat(afterSpike).isLessThan(2_100_000.0)
    }

    @Test
    fun `a sustained change is still tracked`() {
        val smoother = SpeedSmoother()
        repeat(5) { smoother.update(1_000_000.0) }
        repeat(30) { smoother.update(4_000_000.0) }
        assertThat(smoother.update(4_000_000.0)).isWithin(200_000.0).of(4_000_000.0)
    }

    @Test
    fun `zero and negative samples hold the last average`() {
        // A stalled tick must not yank the readout to zero and back.
        val smoother = SpeedSmoother()
        smoother.update(3_000_000.0)
        assertThat(smoother.update(0.0)).isWithin(0.1).of(3_000_000.0)
        assertThat(smoother.update(-1.0)).isWithin(0.1).of(3_000_000.0)
    }

    @Test
    fun `reset clears state between sessions`() {
        val smoother = SpeedSmoother()
        smoother.update(9_000_000.0)
        smoother.reset()
        assertThat(smoother.update(1_000_000.0)).isWithin(0.1).of(1_000_000.0)
    }

    @Test
    fun `quantising collapses changes too small to render`() {
        // Both display as "2.4 MB/s", so they must be the same value — otherwise every
        // digit re-animates for a change nobody can see.
        assertThat(SpeedSmoother.quantise(2_412_000.0))
            .isEqualTo(SpeedSmoother.quantise(2_437_000.0))
    }

    @Test
    fun `quantising preserves changes that do render`() {
        assertThat(SpeedSmoother.quantise(2_400_000.0))
            .isNotEqualTo(SpeedSmoother.quantise(2_600_000.0))
    }

    @Test
    fun `quantising below a megabyte works to the kilobyte`() {
        assertThat(SpeedSmoother.quantise(812_400.0)).isEqualTo(SpeedSmoother.quantise(812_100.0))
        assertThat(SpeedSmoother.quantise(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `a noisy stream settles to a stable rendered value`() {
        // The actual complaint: digits never stop moving. Feed realistic jitter and
        // assert the rendered string stops changing.
        val smoother = SpeedSmoother()
        val samples = listOf(
            3_900_000.0, 1_100_000.0, 4_400_000.0, 900_000.0, 3_100_000.0,
            2_800_000.0, 3_300_000.0, 2_500_000.0, 3_000_000.0, 2_900_000.0,
            3_100_000.0, 2_950_000.0, 3_050_000.0, 3_000_000.0, 2_980_000.0,
        )
        val rendered = samples.map { SpeedSmoother.quantise(smoother.update(it)) }

        // Over the settled tail the readout should show at most two distinct values.
        assertThat(rendered.takeLast(5).toSet().size).isAtMost(2)
    }
}
