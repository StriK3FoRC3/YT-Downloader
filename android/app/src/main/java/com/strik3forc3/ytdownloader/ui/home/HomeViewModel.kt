package com.strik3forc3.ytdownloader.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.BitrateLadder
import com.strik3forc3.ytdownloader.core.BitrateSetting
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.core.Profile
import com.strik3forc3.ytdownloader.core.QueueParser
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.SharedText
import com.strik3forc3.ytdownloader.core.VideoFormat
import com.strik3forc3.ytdownloader.core.YtDlpVersion
import com.strik3forc3.ytdownloader.data.ProfileRepository
import com.strik3forc3.ytdownloader.data.Settings
import com.strik3forc3.ytdownloader.data.SettingsRepository
import com.strik3forc3.ytdownloader.data.ShareInbox
import com.strik3forc3.ytdownloader.data.db.ItemStatus
import com.strik3forc3.ytdownloader.data.db.QueueItemEntity
import com.strik3forc3.ytdownloader.work.DownloadQueue
import com.strik3forc3.ytdownloader.work.DownloadService
import com.strik3forc3.ytdownloader.work.SessionState
import com.strik3forc3.ytdownloader.ytdlp.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything one frame of the home screen needs, assembled once. */
data class HomeUiState(
    val settings: Settings = Settings(),
    val items: List<QueueItemEntity> = emptyList(),
    val session: SessionState = SessionState(),
    val profiles: List<Profile> = listOf(Profile.Default),
    val setupComplete: Boolean = false,
    /** True while links are being expanded — enumeration runs yt-dlp and takes seconds. */
    val adding: Boolean = false,
    /**
     * Set when unpacking yt-dlp failed. Nothing can work in this state, so it is shown
     * as a persistent banner rather than a transient message.
     */
    val setupError: String? = null,
    /** Shown during first-run work, e.g. unpacking or refreshing yt-dlp. */
    val setupStatus: String? = null,
) {
    val activeProfile: Profile
        get() = profiles.firstOrNull { it.name == settings.activeProfileName } ?: Profile.Default

    val queued: List<QueueItemEntity> get() = items.filterNot { it.isTerminal }
    val failed: List<QueueItemEntity> get() = items.filter { it.status == ItemStatus.FAILED }
    val finished: List<QueueItemEntity> get() = items.filter { it.status == ItemStatus.DONE }

    val canDownload: Boolean
        get() = setupComplete && setupError == null && settings.destinationTreeUri != null &&
            queued.isNotEmpty() && !session.running

    val canAdd: Boolean get() = setupComplete && setupError == null && !adding

    fun progressFor(itemId: String): ItemProgress? = session.active[itemId]

    /**
     * Whether the current audio selection forces an FFmpeg re-encode.
     *
     * Surfaced *before* the user commits. The Windows app only records this decision in
     * its log file, after the fact — but on a phone it is the difference between a stream
     * copy and a sustained CPU load, so it belongs in front of the user.
     */
    val willTranscode: Boolean
        get() = settings.mode == DownloadMode.AUDIO &&
            BitrateLadder.willTranscode(settings.audioFormat, null, activeProfile.bitrate)

    /**
     * Why the current audio choice re-encodes, and what to pick instead.
     *
     * The earlier wording only mentioned speed and battery. Quality is what people
     * actually notice: YouTube never serves MP3, so choosing it always means decoding
     * YouTube's Opus and re-encoding it — a second generation of lossy compression that
     * no bitrate setting can undo. M4A and OPUS copy the original stream untouched.
     */
    val transcodeWarning: String?
        get() = when {
            !willTranscode -> null
            settings.audioFormat == AudioFormat.MP3 ->
                "MP3 re-encodes: YouTube never serves it, so quality drops. " +
                    "Pick M4A or OPUS to copy the original audio untouched."
            settings.audioFormat.isLossless ->
                "${settings.audioFormat.name} cannot recover quality YouTube already " +
                    "discarded — it just makes a much larger file."
            activeProfile.bitrate !is BitrateSetting.Automatic ->
                "A fixed bitrate forces a re-encode. Automatic copies the original " +
                    "stream when it can."
            else ->
                "${settings.audioFormat.name} re-encodes here: quality drops and it " +
                    "takes longer."
        }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val queue: DownloadQueue,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val engine: YtDlpEngine,
    private val shareInbox: ShareInbox,
) : ViewModel() {

    private data class LocalState(
        val setupComplete: Boolean = false,
        val setupError: String? = null,
        val setupStatus: String? = null,
        val adding: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    /** One-shot user-facing messages. The UI must drain these; nothing else reads them. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        queue.items,
        queue.state,
        profileRepository.profiles,
        local,
    ) { settings, items, session, profiles, localState ->
        HomeUiState(
            settings = settings,
            items = items,
            session = session,
            profiles = profiles,
            setupComplete = localState.setupComplete,
            adding = localState.adding,
            setupError = localState.setupError,
            setupStatus = localState.setupStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        observeSharedLinks()
        viewModelScope.launch {
            // Unpacking Python takes seconds on first run, so it happens behind a visible
            // setup state rather than blocking application startup.
            val failure = runCatching {
                engine.ensureInitialised()
                queue.recover()
                refreshYtDlpIfStale()
            }.exceptionOrNull()

            local.update {
                it.copy(
                    setupComplete = true,
                    setupStatus = null,
                    setupError = failure?.let { error ->
                        error.message ?: error::class.simpleName ?: "Unknown error"
                    },
                )
            }
        }
    }

    /**
     * Updates yt-dlp when the bundled copy has aged out.
     *
     * The version frozen inside `youtubedl-android` is months old by the time anyone
     * installs the app, and an out-of-date yt-dlp cannot solve YouTube's current player
     * challenges — so it is served storyboard images only and *every* download fails.
     * Leaving that to a button the user has to discover means the app is broken on first
     * run for no visible reason.
     *
     * A failed update is not fatal: the existing copy may still work, and the download
     * itself will report the real problem.
     */
    private suspend fun refreshYtDlpIfStale() {
        val version = engine.versionOrNull()
        if (!YtDlpVersion.isStale(version, LocalDate.now())) return

        local.update { it.copy(setupStatus = "Updating yt-dlp…") }
        when (engine.updateYtDlp()) {
            YtDlpEngine.UpdateResult.UPDATED ->
                _message.value = "yt-dlp updated to ${engine.versionOrNull() ?: "the latest version"}."
            YtDlpEngine.UpdateResult.FAILED ->
                _message.value = "Could not update yt-dlp. Downloads may fail until it updates."
            YtDlpEngine.UpdateResult.ALREADY_CURRENT -> Unit
        }
    }

    /** Manual retry, for when the automatic update could not reach GitHub. */
    fun updateYtDlp() {
        viewModelScope.launch {
            local.update { it.copy(setupStatus = "Updating yt-dlp…") }
            val result = engine.updateYtDlp()
            local.update { it.copy(setupStatus = null) }
            _message.value = when (result) {
                YtDlpEngine.UpdateResult.UPDATED -> "yt-dlp updated."
                YtDlpEngine.UpdateResult.ALREADY_CURRENT -> "yt-dlp is already current."
                YtDlpEngine.UpdateResult.FAILED -> "Update failed. Check the connection."
            }
        }
    }

    /**
     * Puts links from the share sheet into the link box rather than queueing them.
     *
     * A shared video is usually the moment you decide whether you want audio or video, so
     * the format, resolution and profile pickers have to come *before* it is committed.
     * Successive shares append, so several videos can be collected and configured once.
     */
    private fun observeSharedLinks() {
        viewModelScope.launch {
            shareInbox.pending.filterNotNull().collect {
                val text = shareInbox.consume() ?: return@collect

                if (SharedText.extractLinks(text).isEmpty()) {
                    _message.value = "That share did not contain a link."
                    return@collect
                }

                val merge = SharedText.appendTo(_urlInput.value, text)
                _urlInput.value = merge.text
                if (merge.addedCount == 0) {
                    _message.value = "That video is already in the box."
                }
            }
        }
    }

    fun onUrlInputChange(value: String) { _urlInput.value = value }

    fun dismissMessage() { _message.value = null }

    fun addLinks() {
        if (local.value.adding) return

        val input = _urlInput.value
        if (QueueParser.normaliseInput(input).isEmpty()) {
            _message.value = "That does not look like a link. Paste a full YouTube URL starting with https://"
            return
        }

        viewModelScope.launch {
            local.update { it.copy(adding = true) }
            try {
                val added = queue.enqueue(input)
                if (added > 0) {
                    _urlInput.value = ""
                    _message.value = "Added $added ${if (added == 1) "item" else "items"}."
                } else {
                    _message.value = "Nothing could be read from those links."
                }
            } catch (error: Throwable) {
                // Enumeration runs yt-dlp, so this covers a failed unpack, no network,
                // and an extractor error alike. Previously these vanished into a flow
                // nothing rendered, which looked exactly like a dead button.
                _message.value = "Could not add: ${error.message ?: error::class.simpleName}"
            } finally {
                local.update { it.copy(adding = false) }
            }
        }
    }

    fun start() {
        viewModelScope.launch {
            when (queue.start()) {
                is DownloadQueue.StartResult.Started -> {
                    // The queue must survive the app being backgrounded, which only a
                    // foreground service guarantees.
                    DownloadService.start(context)
                }
                DownloadQueue.StartResult.NoDestination ->
                    _message.value = "Choose a download folder first."
                DownloadQueue.StartResult.NothingQueued ->
                    _message.value = "Nothing left to download."
                DownloadQueue.StartResult.AlreadyRunning -> Unit
            }
        }
    }

    fun cancel() {
        queue.cancel()
        DownloadService.cancel(context)
    }

    /** Swipe-to-dismiss on a queue row. */
    fun remove(itemId: String) {
        viewModelScope.launch { queue.remove(itemId) }
    }

    /** Swipe right on a failed row. */
    fun retry(itemId: String) {
        viewModelScope.launch {
            queue.retry(itemId)
            _message.value = "Requeued. Tap Download to run it again."
        }
    }

    /** No installed app claimed the finished file's type. */
    fun reportNoHandler() {
        _message.value = "No app on this device can open that file."
    }

    fun clearFinished() {
        viewModelScope.launch { queue.clearFinished() }
    }

    fun setMode(mode: DownloadMode) = update { settingsRepository.setMode(mode) }
    fun setAudioFormat(value: AudioFormat) = update { settingsRepository.setAudioFormat(value) }
    fun setVideoFormat(value: VideoFormat) = update { settingsRepository.setVideoFormat(value) }
    fun setResolution(value: Resolution) = update { settingsRepository.setResolution(value) }
    fun setParallel(value: Int) = update { settingsRepository.setParallelDownloads(value) }
    fun setActiveProfile(name: String) = update { settingsRepository.setActiveProfile(name) }
    fun setDestination(uri: String) = update { settingsRepository.setDestination(uri) }

    suspend fun currentSettings(): Settings = settingsRepository.settings.first()

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
