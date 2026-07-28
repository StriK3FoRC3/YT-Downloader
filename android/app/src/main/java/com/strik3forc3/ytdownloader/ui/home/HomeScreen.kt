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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.core.QueueParser
import com.strik3forc3.ytdownloader.data.db.ItemStatus
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
                    onRetry = { viewModel.retry(item.id) },
                    onOpen = {
                        if (!openOutput(context, item)) {
                            viewModel.reportNoHandler()
                        }
                    },
                    // Rows reflow rather than jumping when one is removed or completes.
                    modifier = Modifier.animateItem(
                        placementSpec = Motion.placementSpring(),
                    ),
                )
            }

            if (state.items.isEmpty()) {
                item("empty") { EmptyState(state.setupComplete, state.setupStatus) }
            }
        }
    }

    picker?.let { target ->
        OptionPickerSheet(
            target = target,
            state = state,
            onDismiss = { picker = null },
            onAudioFormat = viewModel::setAudioFormat,
            onBitrate = viewModel::setBitrate,
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
            .background(YtdlColors.Background)
            // enableEdgeToEdge() draws behind the status bar, so a custom top bar has to
            // inset itself — Scaffold only offsets the *content*, not the bars.
            .statusBarsPadding()
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
 * Directional swipe actions, plus tap-to-open once a download has finished.
 *
 * Swipe **left** removes; swipe **right** retries a failed item. Retry is only offered
 * on failures — allowing it on a queued or finished row would mean the same gesture does
 * different things depending on state, which is worse than it not being there.
 *
 * The Windows app has none of this: no way to remove one item, no way to retry one item,
 * and no way to reach the file it just wrote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableQueueRow(
    item: QueueItemEntity,
    progress: ItemProgress?,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRetry = item.status == ItemStatus.FAILED
    val canOpen = item.status == ItemStatus.DONE && item.outputUri != null

    val dismissState = rememberSwipeToDismissBoxState(
        // A quarter of the width. The default threshold is half, which is a long drag on
        // a tall list.
        positionalThreshold = { distance -> distance * 0.25f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onRemove(); true }
                // Returning false snaps the row back after firing, so the item stays
                // visible and can be watched as it re-runs.
                SwipeToDismissBoxValue.StartToEnd -> { if (canRetry) onRetry(); false }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = canRetry,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val retrying = direction == SwipeToDismissBoxValue.StartToEnd
            val active = dismissState.targetValue != SwipeToDismissBoxValue.Settled

            // Semantic colour only past the commit point — red for destructive, the
            // brand accent for the recoverable action.
            val accent = if (retrying) YtdlColors.Accent else YtdlColors.Error
            val tint by animateColorAsState(
                targetValue = if (active) accent else YtdlColors.SurfaceRaised,
                animationSpec = Motion.fast(),
                label = "swipeTint",
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp),
                contentAlignment = if (retrying) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (retrying) Icons.Default.Refresh else Icons.Default.Delete,
                    contentDescription = if (retrying) "Retry" else "Remove from queue",
                    tint = if (active) accent else YtdlColors.TextMuted,
                )
            }
        },
    ) {
        QueueRow(
            item = item,
            progress = progress,
            onClick = if (canOpen) onOpen else null,
        )
    }
}

/**
 * Opens a finished download in whatever app handles its type.
 *
 * The URI is a SAF document we wrote, so read access has to be granted explicitly to the
 * receiving app.
 */
private fun openOutput(context: android.content.Context, item: QueueItemEntity): Boolean {
    val uri = item.outputUri?.toUri() ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(intent) }.isSuccess
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
    // Recomputed only when the text changes, not on every recomposition.
    val linkCount = remember(value) { QueueParser.normaliseInput(value).size }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // Grows with the content, then scrolls. A fixed height silently clipped
                // anything past the fourth line: links were in the box and doing nothing
                // visible, with no scrollbar to suggest otherwise. The cap keeps the
                // format pickers on screen rather than letting a long paste push them off.
                .heightIn(min = 120.dp, max = 260.dp)
                .verticalScroll(rememberScrollState()),
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
        // has to say so — otherwise the tap looks like it did nothing. The count also
        // confirms how many links were captured when the box has scrolled.
        SecondaryButton(
            text = when {
                adding -> "Reading links…"
                linkCount > 0 -> "Add $linkCount to queue"
                else -> "Add to queue"
            },
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
            PickerField("Quality", state.activeProfile.bitrate.label) { onPick(PickerTarget.Quality) }
        } else {
            PickerField("Format", state.settings.videoFormat.name) { onPick(PickerTarget.VideoFormat) }
            PickerField("Resolution", state.settings.resolution.label) { onPick(PickerTarget.Resolution) }
        }

        PickerField("Profile", state.settings.activeProfileName) { onPick(PickerTarget.Profile) }
        PickerField("At once", "${state.settings.parallelDownloads}") { onPick(PickerTarget.Parallel) }

        // A warning the Windows app never shows: on a phone this is the difference
        // between a stream copy and a sustained FFmpeg load.
        AnimatedVisibility(
            visible = state.transcodeWarning != null,
            enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
            exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.medium()),
        ) {
            Text(
                text = state.transcodeWarning.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = YtdlColors.Warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(YtdlColors.SurfaceRaised, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
private fun EmptyState(setupComplete: Boolean, setupStatus: String?) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                setupStatus != null -> setupStatus
                setupComplete -> "Nothing queued yet.\nPaste a link to get started."
                else -> "Setting up yt-dlp…"
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
            // Clears the gesture bar or the three-button navigation, and lifts above the
            // keyboard when the link field has focus.
            .navigationBarsPadding()
            .imePadding()
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
