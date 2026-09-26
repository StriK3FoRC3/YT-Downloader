package com.strik3forc3.ytdownloader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion vocabulary.
 *
 * **Budget:** animate transform and alpha only. Nothing here may drive a layout pass or
 * a recomposition storm. A phone running an FFmpeg transcode has no CPU to spare, and a
 * janky progress ring during a slow download is worse than no animation at all — which is
 * why progress uses a single conflated state read once per frame rather than per-event
 * recomposition.
 */
object Motion {

    /** Emphasised easing — quick departure, gentle settle. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0.2f, 1f)

    const val DurationFast = 150
    const val DurationMedium = 260
    const val DurationSlow = 420

    /** Press feedback. Slight overshoot so a tap feels physical rather than instant. */
    fun <T> pressSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Progress values. Critically damped — a bouncing progress bar reads as a bug. */
    fun <T> progressSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Items entering or leaving a list. */
    fun <T> enterSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Queue reordering as rows complete. */
    fun placementSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> fast(): FiniteAnimationSpec<T> = tween(DurationFast, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(DurationMedium, easing = Emphasized)
    fun <T> slow(): FiniteAnimationSpec<T> = tween(DurationSlow, easing = Emphasized)

    /**
     * Stagger between consecutive list items, capped so expanding a 200-item playlist
     * does not take nine seconds to finish animating.
     */
    fun staggerDelay(index: Int): Int = (index * 35).coerceAtMost(280)
}
