package com.strik3forc3.ytdownloader.ytdlp

import android.content.Context
import com.strik3forc3.ytdownloader.core.ArgsBuilder
import com.strik3forc3.ytdownloader.core.AudioSourceInfo
import com.strik3forc3.ytdownloader.core.BitrateLadder
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.DownloadRequest
import com.strik3forc3.ytdownloader.core.ErrorMapper
import com.strik3forc3.ytdownloader.core.FormatSelector
import com.strik3forc3.ytdownloader.core.ItemProgress
import com.strik3forc3.ytdownloader.core.ProgressParser
import com.strik3forc3.ytdownloader.core.ProgressTracker
import com.strik3forc3.ytdownloader.core.QueueParser
import com.strik3forc3.ytdownloader.core.YtDlpArg
import com.strik3forc3.ytdownloader.core.toArgv
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single point of contact with yt-dlp.
 *
 * The Windows app spawns `yt-dlp.exe` from a `Dependencies` folder. Android forbids
 * executing anything outside `nativeLibraryDir`, so instead `youtubedl-android` ships a
 * Python interpreter, FFmpeg and QuickJS as APK-bundled native libraries and runs yt-dlp
 * as a Python payload. That library also injects `--js-runtimes quickjs:<path>` itself,
 * which is why nothing here emits a JS-runtime flag.
 */
@Singleton
class YtDlpEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: DownloadLogger,
) {

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    private val initMutex = Mutex()

    @Volatile
    private var initialised = false

    /**
     * Unpacks the Python environment, FFmpeg and aria2c. First run is slow — it extracts
     * tens of megabytes — so callers must keep this off the main thread and show a
     * one-time setup state.
     */
    suspend fun ensureInitialised() {
        if (initialised) return
        initMutex.withLock {
            if (initialised) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                Aria2c.getInstance().init(context)
            }
            initialised = true
            logger.info("yt-dlp ${versionOrNull() ?: "unknown"} initialised")
        }
    }

    suspend fun versionOrNull(): String? = withContext(Dispatchers.IO) {
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
    }

    /**
     * Expands one input link into queue entries, rejecting live and upcoming streams.
     *
     * Contract: `docs/download-rules.md` §8.
     */
    suspend fun enumerate(url: String, cookies: File?): List<QueueParser.Entry> {
        ensureInitialised()
        val args = ArgsBuilder.buildQueueProbe(url) + cookieArgs(cookies)
        val response = execute(args, processId = null, onLine = null)

        return if (response.exitCode == 0 || response.stdout.isNotBlank()) {
            QueueParser.parse(response.stdout).ifEmpty { fallbackEntry(url) }
        } else {
            logger.warn("enumerate failed for $url: ${ErrorMapper.friendly(response.stderr)}")
            fallbackEntry(url)
        }
    }

    /**
     * A link yt-dlp could not enumerate is still queued, so it fails later with a real
     * error rather than vanishing silently (reference line 1262).
     */
    private fun fallbackEntry(url: String): List<QueueParser.Entry> =
        if (QueueParser.isWebLink(url)) listOf(QueueParser.Entry.Item(url, url)) else emptyList()

    /**
     * Reads the codec and bitrate of the audio stream that *would* be selected.
     *
     * Skipped entirely when [BitrateLadder.needsSourceProbe] says the answer cannot
     * change the outcome — the reference probes unconditionally, spending a full
     * extraction to learn something it will not use.
     */
    suspend fun probeAudioSource(request: DownloadRequest, cookies: File?): AudioSourceInfo? {
        if (request.mode != DownloadMode.AUDIO) return null
        if (!BitrateLadder.needsSourceProbe(request.audioFormat, request.bitrate)) {
            logger.info("probe skipped for ${request.audioFormat.id}: outcome already determined")
            return null
        }

        ensureInitialised()
        val selector = FormatSelector.audio(request.audioFormat, request.bitrate)

        return withContext(Dispatchers.IO) {
            runCatching {
                val probe = YoutubeDLRequest(request.url)
                    .addCommands(
                        (listOf(
                            YtDlpArg("--ignore-config"),
                            YtDlpArg("--no-playlist"),
                            YtDlpArg("--format", selector),
                        ) + cookieArgs(cookies)).toArgv()
                    )
                val info = YoutubeDL.getInstance().getInfo(probe)
                val format = info.requestedFormats?.firstOrNull()
                    ?: info.formats?.firstOrNull { it.formatId == info.formatId }
                format?.toAudioSourceInfo()
            }.onFailure {
                // A failed probe is the "unknown source" case, not a download failure.
                logger.warn("audio probe failed: ${it.message}")
            }.getOrNull()
        }
    }

    private fun VideoFormat.toAudioSourceInfo(): AudioSourceInfo {
        // abr is the audio-specific rate; tbr is the fallback when yt-dlp omits it.
        val kbps = when {
            abr > 0 -> abr.toDouble()
            tbr > 0 -> tbr.toDouble()
            else -> 0.0
        }
        return AudioSourceInfo(formatId = formatId, codec = acodec, averageBitrateKbps = kbps)
    }

    /**
     * Runs one download into [workDir].
     *
     * [workDir] is a per-item scratch directory, which is what lets the caller finalise
     * outputs without scanning the whole library — the reference enumerates the entire
     * destination folder per item to find its GUID-prefixed files (lines 1601 and 1644).
     */
    suspend fun download(
        request: DownloadRequest,
        workDir: File,
        audioSource: AudioSourceInfo?,
        cookies: File?,
        onProgress: (ItemProgress) -> Unit,
    ): Result {
        ensureInitialised()
        workDir.mkdirs()

        val tracker = ProgressTracker(ProgressTracker.expectedStreamsFor(request.mode))
        val outputTemplate = File(workDir, "%(title)s.%(ext)s").absolutePath
        val args = ArgsBuilder.build(request, audioSource, outputTemplate) +
            ProgressParser.PROGRESS_TEMPLATES +
            cookieArgs(cookies)

        val response = execute(args, processId = UUID.randomUUID().toString()) { line ->
            ProgressParser.parse(line)?.let { onProgress(tracker.onEvent(it)) }
        }

        return if (response.exitCode == 0) {
            onProgress(tracker.finish())
            Result.Success
        } else {
            // describe() rather than friendly(): yt-dlp's wording for a bot-check refusal
            // sends people to change format settings that cannot possibly help.
            Result.Failure(ErrorMapper.describe(response.stderr.ifBlank { response.stdout }))
        }
    }

    /**
     * Outcome of a component update.
     *
     * Only yt-dlp appears here. The Windows app's Check For Updates also replaces
     * FFmpeg, ffprobe and Deno (reference lines 1050–1152); on Android those are
     * APK-bundled native libraries and can only change with an app update.
     */
    enum class UpdateResult { UPDATED, ALREADY_CURRENT, FAILED }

    /** yt-dlp is updatable because it is a Python payload rather than an exec'd binary. */
    suspend fun updateYtDlp(): UpdateResult = withContext(Dispatchers.IO) {
        ensureInitialised()
        runCatching {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        }.fold(
            onSuccess = { status ->
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> UpdateResult.UPDATED
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> UpdateResult.ALREADY_CURRENT
                    else -> UpdateResult.FAILED
                }
            },
            onFailure = {
                logger.error("yt-dlp update failed", it)
                UpdateResult.FAILED
            },
        )
    }

    private fun cookieArgs(cookies: File?): List<YtDlpArg> =
        if (cookies == null) emptyList() else listOf(YtDlpArg("--cookies", cookies.absolutePath))

    private data class RawResponse(val exitCode: Int, val stdout: String, val stderr: String)

    /**
     * Bridges the library's blocking call onto a coroutine.
     *
     * Cancellation kills the process rather than merely abandoning the thread. The
     * reference's `KillActiveProcesses` (line 1868) tracks every live process in a shared
     * set and kills them all; scoping the kill to the cancelled item's own process id is
     * both simpler and correct when only one item is cancelled.
     */
    private suspend fun execute(
        args: List<YtDlpArg>,
        processId: String?,
        onLine: ((String) -> Unit)?,
    ): RawResponse = withContext(Dispatchers.IO) {
        val argv = args.toArgv()
        logger.command(argv)

        val request = YoutubeDLRequest(emptyList<String>()).addCommands(argv)

        val cancelHandle = processId?.let { id ->
            currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
                }
            }
        }

        try {
            val response = if (processId == null) {
                YoutubeDL.getInstance().execute(request)
            } else {
                YoutubeDL.getInstance().execute(request, processId) { _, _, line ->
                    onLine?.invoke(line)
                }
            }
            logger.processOutput(response.exitCode, response.out, response.err)
            RawResponse(response.exitCode, response.out, response.err)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logger.error("yt-dlp execution failed", error)
            RawResponse(exitCode = -1, stdout = "", stderr = error.message.orEmpty())
        } finally {
            cancelHandle?.dispose()
        }
    }
}
