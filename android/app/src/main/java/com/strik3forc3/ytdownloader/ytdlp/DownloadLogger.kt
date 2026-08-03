package com.strik3forc3.ytdownloader.ytdlp

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Append-only diagnostic log with a size cap.
 *
 * Replaces the reference's `WriteLog` (line 2051), which opens, appends and closes the
 * file under a process-wide lock on *every* line, and additionally dumps each process's
 * entire accumulated stdout and stderr on exit (line 1683). With several downloads in
 * flight that is sustained lock contention and constant flash writes on a device where
 * both matter.
 *
 * Here writes are buffered through a channel and drained by one writer coroutine, so
 * callers never block, and full process output is recorded only when [verbose] is on.
 */
@Singleton
class DownloadLogger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "YtDlp"
        private const val FILE_NAME = "ytd.log"
        private const val ROTATED_NAME = "ytd.log.1"
        private const val MAX_BYTES = 512 * 1024L
        private const val CHANNEL_CAPACITY = 256
    }

    /** Full stdout/stderr capture. Off by default — it is large and rarely needed. */
    @Volatile
    var verbose: Boolean = false

    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logFile: File get() = File(context.filesDir, FILE_NAME)

    private val lines = Channel<String>(
        capacity = CHANNEL_CAPACITY,
        // Diagnostics must never stall a download, so drop under sustained pressure.
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            for (line in lines) {
                runCatching { append(line) }
                    .onFailure { Log.w(TAG, "log write failed", it) }
            }
        }
    }

    fun info(message: String) = enqueue("INFO", message)

    fun warn(message: String) = enqueue("WARN", message)

    fun error(message: String, throwable: Throwable? = null) {
        enqueue("ERROR", if (throwable == null) message else "$message\n${throwable.stackTraceToString()}")
    }

    /** The exact argv handed to yt-dlp. Values are logged as discrete tokens, never joined. */
    fun command(argv: List<String>) {
        enqueue("CMD", argv.joinToString(separator = "␟"))
    }

    /** Process output, recorded only in [verbose] mode. */
    fun processOutput(exitCode: Int, stdout: String, stderr: String) {
        enqueue("EXIT", "code=$exitCode")
        if (!verbose) return
        if (stdout.isNotBlank()) enqueue("OUT", stdout)
        if (stderr.isNotBlank()) enqueue("ERR", stderr)
    }

    private fun enqueue(level: String, message: String) {
        Log.println(levelToPriority(level), TAG, message)
        lines.trySend("[${timestamp.format(Date())}] $level $message")
    }

    private fun levelToPriority(level: String) = when (level) {
        "ERROR" -> Log.ERROR
        "WARN" -> Log.WARN
        else -> Log.DEBUG
    }

    private fun append(line: String) {
        val file = logFile
        if (file.length() > MAX_BYTES) {
            val rotated = File(context.filesDir, ROTATED_NAME)
            rotated.delete()
            file.renameTo(rotated)
        }
        file.appendText(line + "\n")
    }

    /** Everything currently on disk, newest file last. For the in-app log viewer. */
    fun read(): String = buildString {
        File(context.filesDir, ROTATED_NAME).takeIf { it.exists() }?.let { append(it.readText()) }
        logFile.takeIf { it.exists() }?.let { append(it.readText()) }
    }

    fun clear() {
        File(context.filesDir, ROTATED_NAME).delete()
        logFile.delete()
    }
}
