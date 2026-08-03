package com.strik3forc3.ytdownloader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

/**
 * A numeric readout whose digits roll rather than snap.
 *
 * Applied per character, so only the digits that actually changed animate — updating a
 * speed from `2.4 MB/s` to `2.5 MB/s` moves one glyph, not the whole string.
 */
@Composable
fun RollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = YtdlColors.TextPrimary,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, character ->
            AnimatedContent(
                targetState = character,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { it } + fadeIn() togetherWith
                            slideOutVertically { -it } + fadeOut()
                    } else {
                        slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                    }
                },
                label = "digit$index",
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A labelled session statistic: speed, elapsed, counts. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = YtdlColors.TextPrimary,
    rolling: Boolean = false,
) {
    Column(modifier, horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = YtdlColors.TextMuted,
        )
        if (rolling) {
            RollingNumber(value, color = valueColor)
        } else {
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}

/**
 * Placeholder shown while yt-dlp extracts metadata, before a title is known.
 *
 * A sweeping alpha gradient rather than a spinner: it occupies the exact shape the real
 * content will take, so the row does not resize when the title arrives.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.DurationSlow * 2, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Box(
        modifier
            .height(height)
            .alpha(alpha)
            .background(YtdlColors.SurfaceRaised, RoundedCornerShape(4.dp)),
    )
}

/** Small status pill: the item's format, or a warning that a choice forces a re-encode. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = YtdlColors.TextMuted,
    container: Color = YtdlColors.SurfaceRaised,
) {
    Box(
        modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
