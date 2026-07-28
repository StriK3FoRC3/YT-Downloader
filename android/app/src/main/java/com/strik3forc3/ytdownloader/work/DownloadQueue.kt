package com.strik3forc3.ytdownloader.work

import android.net.Uri
import androidx.core.net.toUri
import com.strik3forc3.ytdownloader.core.AudioSourceInfo
import com.strik3forc3.ytdownloader.core.DownloadPhase
import com.strik3forc3.ytdownloader.core.DownloadRequest
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.core.QueueParser
import com.strik3forc3.ytdownloader.core.YouTubeUrl
import com.strik3forc3.ytdownloader.data.ProfileRepository
import com.strik3forc3.ytdownloader.data.Settings
import com.strik3forc3.ytdownloader.data.SettingsRepository
import com.strik3forc3.ytdownloader.data.db.ItemStatus
import com.strik3forc3.ytdownloader.data.db.QueueDao
import com.strik3forc3.ytdownloader.data.db.QueueItemEntity
import com.strik3forc3.ytdownloader.ytdlp.DownloadLogger
import com.strik3forc3.ytdownloader.ytdlp.OutputFinalizer
import com.strik3forc3.ytdownloader.ytdlp.YtDlpEngine
import com.strik3forc3.ytdownloader.ytdlp.cookie.CookieProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Live per-item progress, kept out of the database so it is not written 10× a second. */
data class ActiveProgress(val itemId: String, val progress: ItemProgress)

data class SessionState(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val active: Map<String, ItemProgress> = emptyMap(),
    val elapsedMillis: Long = 0,
) {
    /** Combined transfer rate across everything currently downloading. */
    val combinedSpeedBytesPerSecond: Double
        get() = active.values
            .filter { it.phase == DownloadPhase.DOWNLOADING }
            .sumOf { it.speedBytesPerSecond }

    val overallFraction: Float
        get() = if (total == 0) 0f else {
            val finished = (completed + failed).toFloat()
            val inFlight = active.values.sumOf { it.fraction.toDouble() }.toFloat()
            ((finished + inFlight) / total).coerceIn(0f, 1f)
        }
}

/**
 * Owns the download session: expands links, then runs items with bounded concurrency.
 *
 * Ported from `StartDownloadsAsync` (reference line 907) and `RunQueuedDownloadAsync`
 * (line 1296), with the reference's `SemaphoreSlim` gate becoming a coroutine
 * [Semaphore].
 */
@Singleton
class DownloadQueue @Inject constructor(
    private val engine: YtDlpEngine,
    private val queueDao: QueueDao,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val finalizer: OutputFinalizer,
    private val cookies: CookieProviderFactory,
    private val logger: DownloadLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Serialises position allocation and dedup checks across concurrent enumerations. */
    private val commitMutex = Mutex()

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val items = queueDao.observeAll()

    private var sessionJob: Job? = null

    /** Reconciles rows left mid-flight by a process death. */
    suspend fun recover() {
        queueDao.requeueInterrupted()
        profiles.ensureDefaultExists()
        finalizer.cleanupOrphans(activeItemIds = emptySet())
    }

    /**
     * Expands the pasted links into queue rows.
     *
     * Each link's rows are written **as soon as that link resolves**, not batched until
     * every link is done. Enumeration invokes yt-dlp, so a playlist can take many
     * seconds; committing per link means the list fills in progressively instead of
     * staying empty and then dumping everything at once.
     *
     * Enumeration itself runs concurrently — the reference awaits each link in turn
     * (line 1254) — but bounded, so a 20-link paste does not open 20 extractions.
     */
    suspend fun enqueue(rawInput: String): Int {
        val links = QueueParser.normaliseInput(rawInput)
        if (links.isEmpty()) return 0

        val current = settings.settings.first()
        val cookieFile = cookies.forMode(current.cookieMode).cookieFile()
        val gate = Semaphore(ENUMERATION_CONCURRENCY)

        val counts = coroutineScope {
            links.map { link ->
                async {
                    gate.withPermit {
                        val entries = engine.enumerate(link, cookieFile)
                        commit(entries, current)
                    }
                }
            }.awaitAll()
        }

        val added = counts.sum()
        logger.info("queued $added items from ${links.size} links")
        return added
    }

    /**
     * Writes one link's entries, serialised so concurrent enumerations cannot collide
     * over positions or insert the same URL twice.
     *
     * Deduplication is against the whole table rather than just this paste, so re-adding
     * a link already in the queue is a no-op instead of a duplicate row.
     */
    private suspend fun commit(entries: List<QueueParser.Entry>, current: Settings): Int =
        commitMutex.withLock {
            val existing = queueDao.allUrls().mapTo(HashSet()) { YouTubeUrl.canonicalKey(it) }
            var position = queueDao.nextPosition()

            val rows = mutableListOf<QueueItemEntity>()
            var accepted = 0

            for (entry in entries) {
                if (!existing.add(YouTubeUrl.canonicalKey(entry.url))) continue
                when (entry) {
                    is QueueParser.Entry.Item -> {
                        rows += entry.toRow(current, position++, ItemStatus.QUEUED, null)
                        accepted++
                    }
                    // Live and upcoming streams are surfaced as failures, not dropped.
                    is QueueParser.Entry.Rejected ->
                        rows += entry.toRow(current, position++, ItemStatus.FAILED, entry.reason)
                }
            }

            if (rows.isNotEmpty()) queueDao.insertAll(rows)
            accepted
        }

    /** Removes one item. Cancels it first if it happens to be running. */
    suspend fun remove(itemId: String) {
        queueDao.delete(itemId)
        _state.update { it.copy(active = it.active - itemId) }
    }

    suspend fun clearFinished() = queueDao.clearFinished()

    private fun QueueParser.Entry.toRow(
        current: Settings,
        position: Int,
        status: ItemStatus,
        failureReason: String?,
    ) = QueueItemEntity(
        id = UUID.randomUUID().toString(),
        url = url,
        title = title.ifBlank { url },
        status = status,
        mode = current.mode,
        audioFormat = current.audioFormat,
        videoFormat = current.videoFormat,
        resolution = current.resolution,
        profileName = current.activeProfileName,
        queuedAt = System.currentTimeMillis(),
        position = position,
        failureReason = failureReason,
    )

    /** Why a session did or did not begin. Silent refusal reads as a broken button. */
    sealed interface StartResult {
        data class Started(val count: Int) : StartResult
        data object AlreadyRunning : StartResult
        data object NothingQueued : StartResult
        data object NoDestination : StartResult
    }

    /**
     * Validates up front and reports the outcome, then runs the session in the
     * background. Preconditions are checked synchronously so the caller can explain a
     * refusal rather than leaving the user staring at an unchanged screen.
     */
    suspend fun start(): StartResult {
        if (sessionJob?.isActive == true) return StartResult.AlreadyRunning

        val current = settings.settings.first()
        val destination = current.destinationTreeUri?.toUri()
            ?: return StartResult.NoDestination

        val pending = queueDao.pending()
        if (pending.isEmpty()) return StartResult.NothingQueued

        sessionJob = scope.launch { runSession(current, destination, pending) }
        return StartResult.Started(pending.size)
    }

    fun cancel() {
        sessionJob?.cancel()
        sessionJob = null
    }

    private suspend fun runSession(
        current: Settings,
        destination: Uri,
        pending: List<QueueItemEntity>,
    ) {
        val startedAt = System.currentTimeMillis()
        _state.value = SessionState(running = true, total = pending.size)

        val cookieFile = cookies.forMode(current.cookieMode).cookieFile()
        val gate = Semaphore(current.parallelDownloads)

        try {
            coroutineScope {
                pending.map { item ->
                    async { gate.withPermit { runItem(item, destination, cookieFile, startedAt) } }
                }.awaitAll()
            }
        } catch (cancellation: CancellationException) {
            logger.info("session cancelled")
            throw cancellation
        } finally {
            _state.update { it.copy(running = false, active = emptyMap()) }
        }
    }

    private suspend fun runItem(
        item: QueueItemEntity,
        destination: Uri,
        cookieFile: java.io.File?,
        startedAt: Long,
    ) {
        val workDir = finalizer.workDirFor(item.id)
        try {
            queueDao.setStatus(item.id, ItemStatus.EXTRACTING)
            publish(item.id, ItemProgress(phase = DownloadPhase.EXTRACTING), startedAt)

            val profile = profiles.byName(item.profileName)
            val request = DownloadRequest(
                url = item.url,
                title = item.title,
                mode = item.mode,
                audioFormat = item.audioFormat,
                videoFormat = item.videoFormat,
                resolution = item.resolution,
                profile = profile,
            )

            val source: AudioSourceInfo? = engine.probeAudioSource(request, cookieFile)
            queueDao.setStatus(item.id, ItemStatus.RUNNING)

            val result = engine.download(request, workDir, source, cookieFile) { progress ->
                publish(item.id, progress, startedAt)
            }

            when (result) {
                is YtDlpEngine.Result.Success -> {
                    val outputs = finalizer.finalize(workDir, destination)
                    if (outputs.isEmpty()) {
                        fail(item.id, "Downloaded media was not found after processing.")
                    } else {
                        queueDao.setComplete(item.id, outputs.first().displayName)
                        _state.update { it.copy(completed = it.completed + 1) }
                    }
                }
                is YtDlpEngine.Result.Failure -> {
                    finalizer.cleanup(workDir)
                    fail(item.id, result.message)
                }
            }
        } catch (cancellation: CancellationException) {
            finalizer.cleanup(workDir)
            queueDao.setStatus(item.id, ItemStatus.CANCELLED)
            throw cancellation
        } catch (error: Throwable) {
            finalizer.cleanup(workDir)
            logger.error("item failed: ${item.title}", error)
            fail(item.id, error.message ?: "Unknown download error")
        } finally {
            _state.update { it.copy(active = it.active - item.id) }
        }
    }

    private suspend fun fail(itemId: String, reason: String) {
        queueDao.setFailure(itemId, reason)
        _state.update { it.copy(failed = it.failed + 1) }
    }

    /**
     * Progress lands in a single conflated [StateFlow] rather than being marshalled
     * per item. The reference throttles each item to 10 Hz independently (line 1455), so
     * five parallel downloads produce ~50 UI updates a second; Compose reads this once
     * per frame instead.
     */
    private fun publish(itemId: String, progress: ItemProgress, startedAt: Long) {
        _state.update {
            it.copy(
                active = it.active + (itemId to progress),
                elapsedMillis = System.currentTimeMillis() - startedAt,
            )
        }
    }

    private companion object {
        const val ENUMERATION_CONCURRENCY = 3
    }
}
