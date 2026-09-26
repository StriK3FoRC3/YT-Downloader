package com.strik3forc3.ytdownloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

/**
 * A collapsible group of related settings.
 *
 * This is the direct answer to the Windows settings window, which stacks more than twenty
 * checkboxes in a flat three-column grid with no grouping — everything visible at once,
 * nothing prioritised, and hit targets far too small for touch. Grouping the same options
 * behind headings with a summary means the common case needs no interaction at all.
 */
@Composable
fun SettingsSection(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.medium(),
        label = "sectionChevron",
    )

    Column(
        modifier
            .fillMaxWidth()
            .background(YtdlColors.Surface, RoundedCornerShape(18.dp))
            .padding(4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = YtdlColors.TextPrimary)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = YtdlColors.TextMuted)
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = YtdlColors.TextMuted,
                modifier = Modifier.rotate(chevron),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.fast()) + expandVertically(Motion.medium()),
            exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.medium()),
        ) {
            Column(
                Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                content()
            }
        }
    }
}

/**
 * A checkbox row with a full-width 56dp target.
 *
 * The reference's custom `AccentCheckBox` is a 16px box — fine for a mouse, unusable on
 * a phone. The whole row is the target here, and the box itself scales on press.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.9f,
        animationSpec = Motion.pressSpring(),
        label = "toggleScale",
    )
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            !enabled -> YtdlColors.SurfaceRaised
            checked -> YtdlColors.Accent
            else -> Color.Transparent
        },
        animationSpec = Motion.fast(),
        label = "toggleFill",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onCheckedChange(!checked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .size(24.dp)
                .scale(scale)
                .background(fill, RoundedCornerShape(7.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = YtdlColors.TextPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) YtdlColors.TextPrimary else YtdlColors.TextDisabled,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = YtdlColors.TextMuted)
            }
        }
    }
}
