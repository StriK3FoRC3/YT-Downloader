package com.strik3forc3.ytdownloader.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.VideoFormat
import com.strik3forc3.ytdownloader.ui.Format
import com.strik3forc3.ytdownloader.ui.components.Chip
import com.strik3forc3.ytdownloader.ui.components.PickerField
import com.strik3forc3.ytdownloader.ui.components.PrimaryButton
import com.strik3forc3.ytdownloader.ui.components.SecondaryButton
import com.strik3forc3.ytdownloader.ui.components.SegmentedControl
import com.strik3forc3.ytdownloader.ui.components.SessionProgressBar
import com.strik3forc3.ytdownloader.ui.components.StatTile
import com.strik3forc3.ytdownloader.ui.theme.Motion
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.data.db.QueueItemEntity

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.urlInput.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Every error path writes here. Without this the app fails silently, which is
    // indistinguishable from a dead button.
    LaunchedEffect(message) {
        message?.let {
            snackbars.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // Scoped storage: the destination is a SAF tree the user grants, not a path. The
    // grant must be made persistable, or write access is lost when the process restarts
    // and every download fails at the final move.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDestination(it.toString())
        }
    }

    Scaffold(
        containerColor = YtdlColors.Background,
        topBar = { HomeHeader(onOpenSettings) },
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            ActionBar(
                state = state,
                onDownload = viewModel::start,
                onCancel = viewModel::cancel,
                onChooseFolder = { folderPicker.launch(null) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.setupError?.let { error ->
                item("setupError") { SetupErrorBanner(error) }
            }

            item("input") {
                LinkInput(
                    value = input,
                    onValueChange = viewModel::onUrlInputChange,
                    onAdd = viewModel::addLinks,
                    adding = state.adding,
                    enabled = state.canAdd,
                )
            }

            item("options") {
                OptionsCard(
                    state = state,
                    onModeChange = viewModel::setMode,
                    onPick = { picker = it },
                )
            }

            item("session") {
                AnimatedVisibility(
                    visible = state.session.running,
                    enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
                    exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.medium()),
                ) {
                    SessionCard(state)
                }
            }

            if (state.items.isNotEmpty()) {
                item("queueHeading") {
                    QueueHeading(state)
                }
            }

            items(state.items, key = { it.id }) { item ->
                SwipeableQueueRow(
                    item = item,
                    progress = state.progressFor(item.id),
                    onRemove = { viewModel.remove(item.id) },
                    // Rows reflow rather than jumping when one is removed or completes.
                    modifier = Modifier.animateItem(
                        placementSpec = Motion.placementSpring(),
                    ),
                )
            }

            if (state.items.isEmpty()) {
                item("empty") { EmptyState(state.setupComplete) }
            }
        }
    }

    picker?.let { target ->
        OptionPickerSheet(
            target = target,
            state = state,
            onDismiss = { picker = null },
            onAudioFormat = viewModel::setAudioFormat,
            onVideoFormat = viewModel::setVideoFormat,
            onResolution = viewModel::setResolution,
            onParallel = viewModel::setParallel,
            onProfile = viewModel::setActiveProfile,
        )
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "YT Downloader",
                style = MaterialTheme.typography.headlineMedium,
                color = YtdlColors.TextPrimary,
            )
            Text(
                "Videos, songs and playlists",
                style = MaterialTheme.typography.bodyMedium,
                color = YtdlColors.TextMuted,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "Settings", tint = YtdlColors.TextMuted)
        }
    }
}

/**
 * Swipe either way to drop an item.
 *
 * The Windows app has no way to remove a queued item at all — the only options are run
 * the whole batch or cancel it. On a phone, pruning a 40-item playlist down to the three
 * tracks you actually wanted is the common case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableQueueRow(
    item: QueueItemEntity,
    progress: ItemProgress?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        // A quarter of the width. The default threshold is half, which is a long drag
        // on a tall list.
        positionalThreshold = { distance -> distance * 0.25f },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onRemove()
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            // Red is the app's error colour and appears only behind a destructive
            // gesture, never as a button fill.
            val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val tint by animateColorAsState(
                targetValue = if (active) YtdlColors.Error else YtdlColors.SurfaceRaised,
                animationSpec = Motion.fast(),
                label = "dismissTint",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove from queue",
                    tint = if (active) YtdlColors.Error else YtdlColors.TextMuted,
                )
            }
        },
    ) {
        QueueRow(item = item, progress = progress)
    }
}

@Composable
private fun SetupErrorBanner(error: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(YtdlColors.Surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "yt-dlp could not start",
            style = MaterialTheme.typography.titleMedium,
            color = YtdlColors.Error,
        )
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = YtdlColors.TextMuted,
        )
    }
}

@Composable
private fun LinkInput(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    adding: Boolean,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            placeholder = {
                Text(
                    "Paste YouTube links, one per line",
                    color = YtdlColors.TextDisabled,
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = YtdlColors.Surface,
                unfocusedContainerColor = YtdlColors.Surface,
                focusedBorderColor = YtdlColors.Accent,
                unfocusedBorderColor = YtdlColors.Outline,
                cursorColor = YtdlColors.AccentText,
                focusedTextColor = YtdlColors.TextPrimary,
                unfocusedTextColor = YtdlColors.TextPrimary,
            ),
        )
        // Expanding a playlist invokes yt-dlp and can take many seconds, so the button
        // has to say so — otherwise the tap looks like it did nothing.
        SecondaryButton(
            text = if (adding) "Reading links…" else "Add to queue",
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = adding,
            enter = fadeIn(Motion.fast()),
            exit = fadeOut(Motion.fast()),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = YtdlColors.Accent,
                trackColor = YtdlColors.Track,
            )
        }
    }
}

@Composable
private fun OptionsCard(
    state: HomeUiState,
    onModeChange: (DownloadMode) -> Unit,
    onPick: (PickerTarget) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(YtdlColors.Surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedControl(
            options = listOf(DownloadMode.AUDIO, DownloadMode.VIDEO),
            selected = state.settings.mode,
            onSelect = onModeChange,
            label = { if (it == DownloadMode.AUDIO) "Audio" else "Video" },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.settings.mode == DownloadMode.AUDIO) {
            PickerField("Format", state.settings.audioFormat.name) { onPick(PickerTarget.AudioFormat) }
        } else {
            PickerField("Format", state.settings.videoFormat.name) { onPick(PickerTarget.VideoFormat) }
            PickerField("Resolution", state.settings.resolution.label) { onPick(PickerTarget.Resolution) }
        }

        PickerField("Profile", state.settings.activeProfileName) { onPick(PickerTarget.Profile) }
        PickerField("At once", "${state.settings.parallelDownloads}") { onPick(PickerTarget.Parallel) }

        // A warning the Windows app never shows: on a phone this is the difference
        // between a stream copy and a sustained FFmpeg load.
        AnimatedVisibility(
            visible = state.willTranscode,
            enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
            exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.medium()),
        ) {
            Chip(
                text = "This format will re-encode — slower and heavier on battery",
                color = YtdlColors.Warning,
                container = YtdlColors.SurfaceRaised,
            )
        }
    }
}

@Composable
private fun SessionCard(state: HomeUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(YtdlColors.Surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(
                label = "Speed",
                value = Format.speed(state.session.combinedSpeedBytesPerSecond),
                valueColor = YtdlColors.AccentText,
                rolling = true,
            )
            StatTile(
                label = "Elapsed",
                value = Format.elapsed(state.session.elapsedMillis),
                rolling = true,
            )
            StatTile(
                label = "Done",
                value = "${state.session.completed}/${state.session.total}",
            )
            if (state.session.failed > 0) {
                StatTile(
                    label = "Failed",
                    value = "${state.session.failed}",
                    valueColor = YtdlColors.Error,
                )
            }
        }
        SessionProgressBar(
            fraction = state.session.overallFraction,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QueueHeading(state: HomeUiState) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Queue",
            style = MaterialTheme.typography.titleLarge,
            color = YtdlColors.TextPrimary,
        )
        Chip("${state.queued.size} waiting")
        if (state.finished.isNotEmpty()) {
            Chip("${state.finished.size} done", color = YtdlColors.Success)
        }
        if (state.failed.isNotEmpty()) {
            Chip("${state.failed.size} failed", color = YtdlColors.Error)
        }
    }
}

@Composable
private fun EmptyState(setupComplete: Boolean) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (setupComplete) {
                "Nothing queued yet.\nPaste a link to get started."
            } else {
                "Setting up yt-dlp…"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = YtdlColors.TextDisabled,
        )
    }
}

@Composable
private fun ActionBar(
    state: HomeUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(YtdlColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.settings.destinationTreeUri == null) {
            SecondaryButton(
                text = "Choose download folder",
                onClick = onChooseFolder,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.session.running) {
            SecondaryButton(
                text = "Cancel",
                onClick = onCancel,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PrimaryButton(
                text = "Download ${state.queued.size}",
                onClick = onDownload,
                enabled = state.canDownload,
                icon = Icons.Default.Download,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
