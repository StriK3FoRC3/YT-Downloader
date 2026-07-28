package com.strik3forc3.ytdownloader.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.strik3forc3.ytdownloader.BuildConfig
import com.strik3forc3.ytdownloader.core.MetadataField
import com.strik3forc3.ytdownloader.core.TitleCleanup
import com.strik3forc3.ytdownloader.data.CookieMode
import com.strik3forc3.ytdownloader.ui.components.PrimaryButton
import com.strik3forc3.ytdownloader.ui.components.SecondaryButton
import com.strik3forc3.ytdownloader.ui.components.SettingsSection
import com.strik3forc3.ytdownloader.ui.components.ToggleRow
import com.strik3forc3.ytdownloader.ui.cookie.CookieLoginActivity
import com.strik3forc3.ytdownloader.ui.theme.YtdlColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }

    // Same defect the home screen had: results were written to a flow nothing rendered,
    // so an update that finished — or failed — looked identical to one still running.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbars.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

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

    val signIn = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshCookieState() }

    Scaffold(
        containerColor = YtdlColors.Background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = YtdlColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = YtdlColors.Background,
                    titleContentColor = YtdlColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSection(
                    title = "Download folder",
                    summary = state.destinationLabel,
                    initiallyExpanded = state.settings.destinationTreeUri == null,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Android only grants access to folders you pick explicitly. " +
                                "Files are written here when a download finishes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YtdlColors.TextMuted,
                        )
                        SecondaryButton(
                            text = if (state.settings.destinationTreeUri == null) "Choose folder" else "Change folder",
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "YouTube sign-in",
                    summary = state.cookieSummary,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "YouTube blocks a growing share of anonymous downloads. Signing in " +
                                "inside the app stores cookies privately on this device — they are " +
                                "never sent anywhere except to YouTube.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YtdlColors.TextMuted,
                        )
                        Text(
                            "yt-dlp warns that authenticated downloads are linked to your account " +
                                "and can get it restricted. Consider a throwaway account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YtdlColors.Warning,
                        )
                        if (state.settings.cookieMode == CookieMode.WEBVIEW && state.signedIn) {
                            SecondaryButton(
                                text = "Sign out",
                                onClick = viewModel::signOut,
                                destructive = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            SecondaryButton(
                                text = "Sign in to YouTube",
                                onClick = { signIn.launch(CookieLoginActivity.intent(context)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Title cleanup",
                    summary = state.titleCleanupSummary,
                ) {
                    ToggleRow(
                        title = "Clean up titles",
                        subtitle = "Remove bracketed tags from the saved filename",
                        checked = state.profile.cleanTitles,
                        onCheckedChange = viewModel::setCleanTitles,
                    )
                    ToggleRow(
                        title = "Remove artist prefix",
                        subtitle = "\"Bruno Mars - The Lazy Song\" becomes \"The Lazy Song\"",
                        checked = state.profile.removeArtistPrefix,
                        onCheckedChange = viewModel::setRemoveArtistPrefix,
                    )
                    TitleCleanup.PRESETS.forEach { preset ->
                        ToggleRow(
                            title = preset.label,
                            subtitle = "Removes “(${preset.label})” from the filename",
                            checked = preset.terms.all { it in state.profile.presetRules },
                            onCheckedChange = { viewModel.togglePreset(preset, it) },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Metadata",
                    summary = state.metadataSummary,
                ) {
                    ToggleRow(
                        title = "Embed metadata",
                        subtitle = "Write tags into the downloaded file",
                        checked = state.profile.metadataEnabled,
                        onCheckedChange = viewModel::setMetadataEnabled,
                    )
                    MetadataField.entries.forEach { field ->
                        ToggleRow(
                            title = field.id,
                            checked = field in state.profile.metadataFields,
                            onCheckedChange = { viewModel.toggleMetadataField(field, it) },
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Thumbnail",
                    summary = if (state.profile.embedThumbnail) "Embedded" else "Not embedded",
                ) {
                    ToggleRow(
                        title = "Embed thumbnail",
                        subtitle = "Skipped for WAV and AAC, which cannot store one",
                        checked = state.profile.embedThumbnail,
                        onCheckedChange = viewModel::setEmbedThumbnail,
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Components",
                    summary = state.componentSummary,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "FFmpeg and the JavaScript runtime ship inside the app and change " +
                                "only with an app update — Android does not allow replacing them " +
                                "at runtime. yt-dlp itself can be updated.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YtdlColors.TextMuted,
                        )
                        PrimaryButton(
                            text = if (state.updating) "Updating…" else "Update yt-dlp",
                            onClick = viewModel::updateYtDlp,
                            enabled = !state.updating,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Diagnostics",
                    summary = if (state.settings.verboseLogging) "Verbose logging on" else "Standard logging",
                ) {
                    ToggleRow(
                        title = "Verbose logging",
                        subtitle = "Record full yt-dlp output. Useful for bug reports, larger on disk.",
                        checked = state.settings.verboseLogging,
                        onCheckedChange = viewModel::setVerboseLogging,
                    )
                }
            }

            item {
                SettingsSection(
                    title = "About",
                    summary = "Version ${BuildConfig.VERSION_NAME}",
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Credit(
                            role = "Created by",
                            name = "StriK3FoRC3",
                            detail = "Founder of the project and author of the original Windows app.",
                        )
                        Credit(
                            role = "Android port by",
                            name = "Dload",
                        )

                        HorizontalDivider(color = YtdlColors.Outline)

                        Text(
                            "Built on",
                            style = MaterialTheme.typography.labelSmall,
                            color = YtdlColors.AccentText,
                        )
                        // These do the actual work and carry their own licences; see
                        // THIRD_PARTY_NOTICES.md in the repository.
                        Text(
                            "yt-dlp — extraction and downloading\n" +
                                "FFmpeg — merging, conversion, metadata\n" +
                                "QuickJS — solving YouTube's player challenges\n" +
                                "Python — the runtime yt-dlp needs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = YtdlColors.TextMuted,
                        )
                        Text(
                            "Each is a separate program under its own licence. " +
                                "Not affiliated with YouTube or Google.",
                            style = MaterialTheme.typography.labelMedium,
                            color = YtdlColors.TextDisabled,
                        )
                    }
                }
            }
        }
    }
}

/** One attribution line: who, and what they did. */
@Composable
private fun Credit(role: String, name: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            role,
            style = MaterialTheme.typography.labelSmall,
            color = YtdlColors.AccentText,
        )
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = YtdlColors.TextPrimary,
        )
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = YtdlColors.TextMuted)
        }
    }
}
