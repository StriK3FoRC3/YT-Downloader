package com.strik3forc3.ytdownloader.ui.settings

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strik3forc3.ytdownloader.core.MetadataField
import com.strik3forc3.ytdownloader.core.Profile
import com.strik3forc3.ytdownloader.core.TitlePreset
import com.strik3forc3.ytdownloader.data.CookieMode
import com.strik3forc3.ytdownloader.data.ProfileRepository
import com.strik3forc3.ytdownloader.data.Settings
import com.strik3forc3.ytdownloader.data.SettingsRepository
import com.strik3forc3.ytdownloader.ytdlp.YtDlpEngine
import com.strik3forc3.ytdownloader.ytdlp.cookie.WebViewCookieProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: Settings = Settings(),
    val profile: Profile = Profile.Default,
    val signedIn: Boolean = false,
    val ytDlpVersion: String? = null,
    val updating: Boolean = false,
    val message: String? = null,
) {
    val destinationLabel: String
        get() = settings.destinationTreeUri
            ?.let { it.toUri().lastPathSegment?.substringAfterLast(':') ?: "Selected" }
            ?: "Not set — downloads cannot start"

    val cookieSummary: String get() = when {
        settings.cookieMode == CookieMode.DISABLED -> "Not signed in"
        signedIn -> "Signed in"
        else -> "Sign-in incomplete"
    }

    val titleCleanupSummary: String get() = when {
        !profile.cleanTitles -> "Off"
        else -> "${profile.rules.size} rule${if (profile.rules.size == 1) "" else "s"}" +
            if (profile.removeArtistPrefix) " · artist prefix" else ""
    }

    val metadataSummary: String get() = when {
        !profile.metadataEnabled -> "Off"
        profile.metadataFields.isEmpty() -> "On, no fields selected"
        else -> "${profile.metadataFields.size} of ${MetadataField.entries.size} fields"
    }

    val componentSummary: String get() = ytDlpVersion?.let { "yt-dlp $it" } ?: "yt-dlp"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val cookies: WebViewCookieProvider,
    private val engine: YtDlpEngine,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    private data class LocalState(
        val signedIn: Boolean = false,
        val version: String? = null,
        val updating: Boolean = false,
        val message: String? = null,
    )

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        profileRepository.profiles,
        local,
    ) { settings, profiles, localState ->
        SettingsUiState(
            settings = settings,
            profile = profiles.firstOrNull { it.name == settings.activeProfileName } ?: Profile.Default,
            signedIn = localState.signedIn,
            ytDlpVersion = localState.version,
            updating = localState.updating,
            message = localState.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCookieState()
        viewModelScope.launch {
            local.value = local.value.copy(version = engine.versionOrNull())
        }
    }

    fun refreshCookieState() {
        viewModelScope.launch {
            local.value = local.value.copy(signedIn = cookies.cookieFile() != null)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            cookies.clear()
            settingsRepository.setCookieMode(CookieMode.DISABLED)
            local.value = local.value.copy(signedIn = false)
        }
    }

    fun setDestination(uri: String) = launch { settingsRepository.setDestination(uri) }

    fun setVerboseLogging(value: Boolean) = launch { settingsRepository.setVerboseLogging(value) }

    fun updateYtDlp() {
        viewModelScope.launch {
            local.value = local.value.copy(updating = true)
            val result = engine.updateYtDlp()
            local.value = local.value.copy(
                updating = false,
                version = engine.versionOrNull(),
                message = when (result) {
                    YtDlpEngine.UpdateResult.UPDATED -> "yt-dlp updated."
                    YtDlpEngine.UpdateResult.ALREADY_CURRENT -> "yt-dlp is already current."
                    YtDlpEngine.UpdateResult.FAILED -> "Update failed."
                },
            )
        }
    }

    // Profile edits write through to the active profile.

    fun setCleanTitles(value: Boolean) = editProfile { it.copy(cleanTitles = value) }
    fun setRemoveArtistPrefix(value: Boolean) = editProfile { it.copy(removeArtistPrefix = value) }
    fun setMetadataEnabled(value: Boolean) = editProfile { it.copy(metadataEnabled = value) }
    fun setEmbedThumbnail(value: Boolean) = editProfile { it.copy(embedThumbnail = value) }

    fun togglePreset(preset: TitlePreset, enabled: Boolean) = editProfile { profile ->
        val rules = profile.presetRules.toMutableList()
        if (enabled) {
            preset.terms.forEach { if (it !in rules) rules += it }
        } else {
            rules.removeAll(preset.terms)
        }
        profile.copy(presetRules = rules)
    }

    fun toggleMetadataField(field: MetadataField, enabled: Boolean) = editProfile { profile ->
        val fields = profile.metadataFields.toMutableSet()
        if (enabled) fields += field else fields -= field
        profile.copy(metadataFields = fields)
    }

    private fun editProfile(transform: (Profile) -> Profile) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val current = profileRepository.byName(settings.activeProfileName)
            profileRepository.save(transform(current))
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
