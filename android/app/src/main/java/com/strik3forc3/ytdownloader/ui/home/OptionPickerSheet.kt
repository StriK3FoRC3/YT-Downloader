package com.strik3forc3.ytdownloader.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.BitrateSetting
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.VideoFormat
import com.strik3forc3.ytdownloader.data.Settings
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

enum class PickerTarget { AudioFormat, Quality, VideoFormat, Resolution, Parallel, Profile }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

/**
 * One sheet for every option list.
 *
 * The Windows app puts six dropdowns on one dense row; a phone needs each choice to own
 * the screen while it is being made, with a subtitle explaining the trade-off. The format
 * descriptions are taken from the README's own guidance tables.
 */
@Composable
fun OptionPickerSheet(
    target: PickerTarget,
    state: HomeUiState,
    onDismiss: () -> Unit,
    onAudioFormat: (AudioFormat) -> Unit,
    onBitrate: (BitrateSetting) -> Unit,
    onVideoFormat: (VideoFormat) -> Unit,
    onResolution: (Resolution) -> Unit,
    onParallel: (Int) -> Unit,
    onProfile: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = YtdlColors.Surface,
        contentColor = YtdlColors.TextPrimary,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = target.title(),
                style = MaterialTheme.typography.titleLarge,
                color = YtdlColors.TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when (target) {
                PickerTarget.AudioFormat -> AudioFormat.entries.forEach { option ->
                    Option(
                        title = option.name,
                        subtitle = option.description(),
                        selected = option == state.settings.audioFormat,
                    ) { onAudioFormat(option); onDismiss() }
                }

                PickerTarget.Quality -> BitrateSetting.ALL.forEach { option ->
                    Option(
                        title = option.label,
                        subtitle = option.description(state.settings.audioFormat),
                        selected = option == state.activeProfile.bitrate,
                    ) { onBitrate(option); onDismiss() }
                }

                PickerTarget.VideoFormat -> VideoFormat.entries.forEach { option ->
                    Option(
                        title = option.name,
                        subtitle = option.description(),
                        selected = option == state.settings.videoFormat,
                    ) { onVideoFormat(option); onDismiss() }
                }

                PickerTarget.Resolution -> Resolution.entries.forEach { option ->
                    Option(
                        title = option.label,
                        subtitle = if (option == Resolution.HIGHEST) {
                            "Best available — never upscales"
                        } else {
                            "Caps height at ${option.height}px"
                        },
                        selected = option == state.settings.resolution,
                    ) { onResolution(option); onDismiss() }
                }

                PickerTarget.Parallel -> (1..Settings.MAX_PARALLEL).forEach { count ->
                    Option(
                        title = "$count at once",
                        subtitle = when (count) {
                            1 -> "Gentlest on the connection and on YouTube's rate limits"
                            2 -> "Recommended"
                            else -> "Fastest, but a phone may thermally throttle"
                        },
                        selected = count == state.settings.parallelDownloads,
                    ) { onParallel(count); onDismiss() }
                }

                PickerTarget.Profile -> state.profiles.forEach { profile ->
                    Option(
                        title = profile.name,
                        subtitle = profile.summary(),
                        selected = profile.name == state.settings.activeProfileName,
                    ) { onProfile(profile.name); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun Option(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) YtdlColors.AccentContainer else Color.Transparent,
        animationSpec = Motion.fast(),
        label = "optionContainer",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) YtdlColors.AccentText else YtdlColors.TextPrimary,
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = YtdlColors.TextMuted)
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = YtdlColors.AccentText)
        }
    }
}

private fun PickerTarget.title(): String = when (this) {
    PickerTarget.AudioFormat -> "Audio format"
    PickerTarget.Quality -> "Audio quality"
    PickerTarget.VideoFormat -> "Video format"
    PickerTarget.Resolution -> "Resolution"
    PickerTarget.Parallel -> "Downloads at once"
    PickerTarget.Profile -> "Profile"
}

// Guidance lifted from the project README's format tables.
private fun AudioFormat.description(): String = when (this) {
    AudioFormat.MP3 -> "Best support for older devices — normally re-encodes"
    AudioFormat.M4A -> "Copies YouTube's AAC stream when it can"
    AudioFormat.AAC -> "Same stream as M4A, raw container"
    AudioFormat.FLAC -> "Lossless container — cannot restore lost quality"
    AudioFormat.WAV -> "Uncompressed, very large files"
    AudioFormat.OGG -> "Vorbis-based workflows"
    AudioFormat.OPUS -> "Best quality per byte — copies YouTube's stream"
}

private fun VideoFormat.description(): String = when (this) {
    VideoFormat.MP4 -> "Widest device and editor compatibility"
    VideoFormat.MKV -> "Any codec combination, most flexible"
    VideoFormat.WEBM -> "Modern web codecs, less legacy support"
}

private fun com.strik3forc3.ytdownloader.core.Profile.summary(): String {
    val parts = buildList {
        if (cleanTitles) add("clean titles")
        if (metadataEnabled) add("metadata")
        if (embedThumbnail) add("thumbnail")
    }
    return if (parts.isEmpty()) "No rewriting" else parts.joinToString(" · ")
}

/**
 * Bitrate guidance, which depends on the target format.
 *
 * A fixed bitrate always forces a re-encode, so for a format that could otherwise be
 * copied byte-for-byte it is a downgrade. For MP3 — which YouTube never serves and which
 * therefore always re-encodes — a higher bitrate genuinely does reduce the damage.
 */
private fun BitrateSetting.description(format: AudioFormat): String = when (this) {
    is BitrateSetting.Automatic -> when {
        format.isLossless -> "Decodes without a bitrate limit"
        format == AudioFormat.MP3 -> "Best possible — re-encodes at 320 kbps"
        else -> "Best possible — copies the original stream, or 256 kbps if it cannot"
    }
    is BitrateSetting.Fixed -> when {
        format.isLossless -> "Ignored for ${format.name}"
        kbps >= 320 -> "Best quality this format can hold" +
            if (format == AudioFormat.MP3) " — closest MP3 gets to the original" else ""
        kbps >= 256 -> "Near-transparent, noticeably smaller than 320"
        kbps >= 192 -> "Reasonable for music, some loss on dense tracks"
        kbps >= 128 -> "Noticeable loss — fine for speech"
        else -> "Small files, clearly degraded"
    }
}
