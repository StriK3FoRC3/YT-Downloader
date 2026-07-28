package com.strik3forc3.ytdownloader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.Profile
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which source supplies YouTube cookies. Replaces the reference's browser picker. */
enum class CookieMode { DISABLED, WEBVIEW, MICROG }

data class Settings(
    val mode: DownloadMode = DownloadMode.AUDIO,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val videoFormat: VideoFormat = VideoFormat.MP4,
    val resolution: Resolution = Resolution.HIGHEST,
    val parallelDownloads: Int = DEFAULT_PARALLEL,
    val activeProfileName: String = Profile.DEFAULT_NAME,
    val destinationTreeUri: String? = null,
    val cookieMode: CookieMode = CookieMode.DISABLED,
    val verboseLogging: Boolean = false,
) {
    companion object {
        const val DEFAULT_PARALLEL = 2

        /**
         * The Windows app allows 5. A phone shares one radio and thermally throttles, so
         * more concurrent downloads past this point tend to make the whole batch slower
         * rather than faster. See `docs/download-rules.md` "Platform divergences".
         */
        const val MAX_PARALLEL = 3
    }
}

/**
 * Replaces `settings.ini` and the reference's `LoadPreferences` (line 2363), which builds
 * a dictionary with `ToDictionary()` — throwing on a duplicate key — inside a blanket
 * `catch` that silently discards *every* setting and profile when anything goes wrong.
 * DataStore reads a typed, per-key store, so one bad value cannot take the rest with it.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val VIDEO_FORMAT = stringPreferencesKey("video_format")
        val RESOLUTION = stringPreferencesKey("resolution")
        val PARALLEL = intPreferencesKey("parallel_downloads")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val DESTINATION = stringPreferencesKey("destination_tree_uri")
        val COOKIE_MODE = stringPreferencesKey("cookie_mode")
        val VERBOSE = booleanPreferencesKey("verbose_logging")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            mode = prefs[Keys.MODE].toEnum(DownloadMode.AUDIO),
            audioFormat = prefs[Keys.AUDIO_FORMAT].toEnum(AudioFormat.MP3),
            videoFormat = prefs[Keys.VIDEO_FORMAT].toEnum(VideoFormat.MP4),
            resolution = prefs[Keys.RESOLUTION].toEnum(Resolution.HIGHEST),
            parallelDownloads = (prefs[Keys.PARALLEL] ?: Settings.DEFAULT_PARALLEL)
                .coerceIn(1, Settings.MAX_PARALLEL),
            activeProfileName = prefs[Keys.ACTIVE_PROFILE] ?: Profile.DEFAULT_NAME,
            destinationTreeUri = prefs[Keys.DESTINATION],
            cookieMode = prefs[Keys.COOKIE_MODE].toEnum(CookieMode.DISABLED),
            verboseLogging = prefs[Keys.VERBOSE] ?: false,
        )
    }

    suspend fun setMode(value: DownloadMode) = put(Keys.MODE, value.name)
    suspend fun setAudioFormat(value: AudioFormat) = put(Keys.AUDIO_FORMAT, value.name)
    suspend fun setVideoFormat(value: VideoFormat) = put(Keys.VIDEO_FORMAT, value.name)
    suspend fun setResolution(value: Resolution) = put(Keys.RESOLUTION, value.name)
    suspend fun setActiveProfile(value: String) = put(Keys.ACTIVE_PROFILE, value)
    suspend fun setDestination(value: String) = put(Keys.DESTINATION, value)
    suspend fun setCookieMode(value: CookieMode) = put(Keys.COOKIE_MODE, value.name)

    suspend fun setParallelDownloads(value: Int) {
        context.dataStore.edit { it[Keys.PARALLEL] = value.coerceIn(1, Settings.MAX_PARALLEL) }
    }

    suspend fun setVerboseLogging(value: Boolean) {
        context.dataStore.edit { it[Keys.VERBOSE] = value }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    /** An unrecognised stored value falls back to the default instead of throwing. */
    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
