package com.strik3forc3.ytdownloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

/**
 * The one primary action on a screen.
 *
 * The Windows app colours Download green, Clear red, Info yellow and Check For Updates
 * blue, all on the same surface — six accents competing means none of them leads. Here
 * exactly one control per screen gets the purple fill and everything else recedes.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Scale, not colour-flash: a transform is GPU-cheap and cannot trigger a layout pass.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.pressSpring(),
        label = "primaryPress",
    )
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> YtdlColors.SurfaceRaised
            pressed -> YtdlColors.AccentHover
            else -> YtdlColors.Accent
        },
        animationSpec = Motion.fast(),
        label = "primaryFill",
    )

    Row(
        modifier = modifier
            .scale(scale)
            .background(background, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) YtdlColors.TextPrimary else YtdlColors.TextDisabled
        icon?.let { Icon(it, contentDescription = null, tint = contentColor) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** Everything that is not the primary action: outlined, neutral, quiet. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    /** Destructive actions tint their *text*, never their fill. */
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.pressSpring(),
        label = "secondaryPress",
    )

    val contentColor = when {
        !enabled -> YtdlColors.TextDisabled
        destructive -> YtdlColors.Error
        else -> YtdlColors.TextPrimary
    }

    Row(
        modifier = modifier
            .scale(scale)
            .background(
                if (pressed) YtdlColors.SurfaceRaised else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, YtdlColors.Outline, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = contentColor) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/**
 * Audio / Video mode switch, replacing the reference's pair of radio buttons.
 *
 * The selected indicator slides between segments rather than blinking on, so the change
 * of mode is legible as a movement.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(YtdlColors.SurfaceRaised, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) YtdlColors.Accent else Color.Transparent,
                animationSpec = Motion.medium(),
                label = "segmentFill",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) YtdlColors.TextPrimary else YtdlColors.TextMuted,
                animationSpec = Motion.medium(),
                label = "segmentText",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(fill, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(option) }
                    .defaultMinSize(minHeight = 44.dp)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = MaterialTheme.typography.labelLarge, color = textColor)
            }
        }
    }
}

/** A labelled row that opens a picker. 48dp minimum target throughout. */
@Composable
fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = Motion.pressSpring(),
        label = "pickerPress",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .background(YtdlColors.SurfaceRaised, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) YtdlColors.TextMuted else YtdlColors.TextDisabled,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) YtdlColors.TextPrimary else YtdlColors.TextDisabled,
            )
        }
    }
}
