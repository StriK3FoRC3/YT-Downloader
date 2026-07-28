package com.strik3forc3.ytdownloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.core.DownloadPhase
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

/**
 * The queue row's status indicator.
 *
 * Carries three signals at once, which the Windows app spreads across a text column, a
 * colour and a shared progress bar: how far along the item is, what it is doing, and
 * whether it succeeded. The sweep is spring-driven so it glides between the discrete
 * values yt-dlp reports, and the stroke crossfades purple → green on completion.
 *
 * Post-processing has no percentage — FFmpeg does not report one — so that phase spins an
 * indeterminate arc over the held sweep rather than freezing at a number.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    phase: DownloadPhase,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    strokeWidth: Dp = 3.dp,
    failed: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val targetColor = when {
        failed -> YtdlColors.Error
        phase == DownloadPhase.DONE -> YtdlColors.Success
        phase == DownloadPhase.PROCESSING -> YtdlColors.AccentHover
        else -> YtdlColors.Accent
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = Motion.medium(),
        label = "ringColor",
    )

    val sweep by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.progressSpring(),
        label = "ringSweep",
    )

    // Only started for the phases that need it, so an idle list runs no animation clock.
    val indeterminate = phase == DownloadPhase.EXTRACTING || phase == DownloadPhase.PROCESSING
    val transition = rememberInfiniteTransition(label = "ringSpin")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringSpinAngle",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = YtdlColors.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )

            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }

            if (indeterminate) {
                drawArc(
                    color = color.copy(alpha = 0.9f),
                    startAngle = spin,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}

/**
 * The session-level bar under the queue. Animates its own fill so it never jumps when
 * several items report at once.
 */
@Composable
fun SessionProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = YtdlColors.Accent,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.progressSpring(),
        label = "sessionProgress",
    )

    Canvas(modifier.size(width = Dp.Unspecified, height = height)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = YtdlColors.Track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        if (animated > 0f) {
            drawRoundRect(
                color = color,
                size = Size(size.width * animated, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
        }
    }
}
