package com.strik3forc3.ytdownloader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.DownloadPhase
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.data.db.ItemStatus
import com.strik3forc3.ytdownloader.data.db.QueueItemEntity
import com.strik3forc3.ytdownloader.ui.Format
import com.strik3forc3.ytdownloader.ui.components.Chip
import com.strik3forc3.ytdownloader.ui.components.ProgressRing
import com.strik3forc3.ytdownloader.ui.components.ShimmerBlock
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

/**
 * One queue item.
 *
 * The Windows app renders this as a four-column `ListView` row — Title, Type, Status,
 * Source link — where status is a text percentage and the only progress indication is a
 * single shared bar for the whole session. Here each item carries its own ring, so a
 * five-item batch shows five independent states at a glance, and the phase is legible
 * without reading text.
 */
@Composable
fun QueueRow(
    item: QueueItemEntity,
    progress: ItemProgress?,
    modifier: Modifier = Modifier,
    /** Non-null once the file exists and can be opened. */
    onClick: (() -> Unit)? = null,
) {
    val failed = item.status == ItemStatus.FAILED
    val phase = progress?.phase ?: item.status.toPhase()
    val fraction = progress?.fraction ?: if (item.status == ItemStatus.DONE) 1f else 0f

    val background by animateColorAsState(
        targetValue = if (phase == DownloadPhase.DOWNLOADING || phase == DownloadPhase.PROCESSING) {
            YtdlColors.AccentContainer
        } else {
            YtdlColors.Surface
        },
        animationSpec = Motion.medium(),
        label = "rowBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = YtdlColors.AccentText),
                        onClickLabel = "Open ${item.outputName ?: "file"}",
                        onClick = onClick,
                    )
                }
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(fraction = fraction, phase = phase, failed = failed)

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // Titles are unknown until extraction returns, so the row reserves the space
            // instead of resizing when it arrives.
            if (item.title == item.url && phase == DownloadPhase.EXTRACTING) {
                ShimmerBlock(Modifier.fillMaxWidth(0.7f))
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = YtdlColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(item.formatLabel())
                Text(
                    text = item.statusLine(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = item.statusColor(phase, failed),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The failure reason expands in place rather than living in a separate tab
            // the user has to go and find.
            AnimatedVisibility(
                visible = failed && item.failureReason != null,
                enter = fadeIn(Motion.fast()) + expandVertically(Motion.medium()),
                exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.medium()),
            ) {
                Text(
                    text = item.failureReason.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = YtdlColors.Error,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            phase == DownloadPhase.DOWNLOADING && progress != null -> {
                Column(
                    Modifier.width(74.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        Format.percent(progress.fraction),
                        style = MaterialTheme.typography.titleMedium,
                        color = YtdlColors.TextPrimary,
                    )
                    Text(
                        Format.speed(progress.speedBytesPerSecond),
                        style = MaterialTheme.typography.labelSmall,
                        color = YtdlColors.TextMuted,
                    )
                }
            }

            // An explicit control rather than relying on the row tap alone. The row sits
            // inside a swipe container that can swallow a tap, and a "tap to open" hint in
            // the status line is the first thing to be truncated away by a long filename —
            // so the affordance was both unreliable and invisible.
            onClick != null -> {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Open ${item.outputName ?: "file"}",
                        tint = YtdlColors.Success,
                    )
                }
            }
        }
    }
}

private fun ItemStatus.toPhase(): DownloadPhase = when (this) {
    ItemStatus.QUEUED -> DownloadPhase.PENDING
    ItemStatus.EXTRACTING -> DownloadPhase.EXTRACTING
    ItemStatus.RUNNING -> DownloadPhase.DOWNLOADING
    ItemStatus.PROCESSING -> DownloadPhase.PROCESSING
    ItemStatus.DONE -> DownloadPhase.DONE
    ItemStatus.FAILED, ItemStatus.CANCELLED -> DownloadPhase.DONE
}

private fun QueueItemEntity.formatLabel(): String = when (mode) {
    DownloadMode.AUDIO -> "Audio · ${audioFormat.name}"
    DownloadMode.VIDEO -> "Video · ${videoFormat.name}" +
        (resolution.height?.let { " · ${it}p" } ?: "")
}

private fun QueueItemEntity.statusLine(progress: ItemProgress?): String = when {
    status == ItemStatus.FAILED -> "Failed"
    status == ItemStatus.CANCELLED -> "Cancelled"
    status == ItemStatus.DONE -> outputName ?: "Finished"
    progress?.phase == DownloadPhase.EXTRACTING -> "Reading link…"
    // Naming the post-processor tells the user *why* the bar has stopped moving.
    progress?.phase == DownloadPhase.PROCESSING ->
        progress.postProcessor?.let { "Processing · $it" } ?: "Processing…"
    progress?.phase == DownloadPhase.DOWNLOADING ->
        Format.eta(progress.etaSeconds) ?: "Downloading"
    else -> "Queued"
}

private fun QueueItemEntity.statusColor(phase: DownloadPhase, failed: Boolean) = when {
    failed -> YtdlColors.Error
    status == ItemStatus.DONE -> YtdlColors.Success
    phase == DownloadPhase.PENDING -> YtdlColors.TextDisabled
    else -> YtdlColors.TextMuted
}
