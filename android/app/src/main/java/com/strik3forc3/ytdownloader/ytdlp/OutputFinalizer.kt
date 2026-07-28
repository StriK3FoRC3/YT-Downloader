package com.strik3forc3.ytdownloader.ytdlp

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.strik3forc3.ytdownloader.core.OutputNaming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves a finished download out of its scratch directory and into the user's chosen
 * destination.
 *
 * yt-dlp writes to a real filesystem path, but scoped storage means the user's folder is
 * usually a SAF tree reachable only through `ContentResolver`. The download therefore
 * lands in app-private cache first and is copied across once, at the end.
 *
 * That per-item scratch directory is also the fix for the reference's hottest file-system
 * path: `FinalizeUniqueOutputs` (line 1601) and `CleanupPrefixedFiles` (line 1644) both
 * enumerate the *entire* destination folder for every download, filtering on a GUID
 * prefix. That cost grows with the user's library and is paid by every parallel worker —
 * and over `ContentResolver` it would be far worse than on NTFS. Here the listing is of
 * one directory containing one item's files.
 */
@Singleton
class OutputFinalizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: DownloadLogger,
) {

    data class Output(val displayName: String, val uri: Uri, val sizeBytes: Long)

    /** Creates the scratch directory for one queue item. */
    fun workDirFor(itemId: String): File =
        File(context.cacheDir, "downloads/$itemId").apply { mkdirs() }

    /**
     * Moves every media file in [workDir] into [destinationTree], applying the collision
     * naming rule, then deletes the scratch directory.
     *
     * @return the outputs written, empty when yt-dlp produced nothing.
     */
    suspend fun finalize(workDir: File, destinationTree: Uri): List<Output> =
        withContext(Dispatchers.IO) {
            val produced = workDir.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
            if (produced.isEmpty()) {
                cleanup(workDir)
                return@withContext emptyList()
            }

            val tree = DocumentFile.fromTreeUri(context, destinationTree)
            if (tree == null || !tree.canWrite()) {
                throw IllegalStateException("The download folder is no longer writable. Pick it again in Settings.")
            }

            // One listing of the destination, reused for every file this item produced,
            // rather than a fresh existence probe per candidate name.
            val existingNames = tree.listFiles().mapNotNull { it.name }.toMutableSet()

            val outputs = produced.map { source ->
                val name = OutputNaming.uniqueName(source.name) { it in existingNames }
                existingNames += name

                val target = tree.createFile(mimeTypeFor(source.extension), name)
                    ?: throw IllegalStateException("Could not create \"$name\" in the download folder.")

                context.contentResolver.openOutputStream(target.uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open \"$name\" for writing.")

                logger.info("output: $name (${source.length()} bytes)")
                Output(name, target.uri, source.length())
            }

            cleanup(workDir)
            outputs
        }

    /** Discards a failed or cancelled item's partial files. */
    fun cleanup(workDir: File) {
        runCatching { workDir.deleteRecursively() }
            .onFailure { logger.warn("could not clean ${workDir.name}: ${it.message}") }
    }

    /** Removes scratch directories orphaned by a process death mid-download. */
    suspend fun cleanupOrphans(activeItemIds: Set<String>) = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "downloads")
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in activeItemIds) cleanup(dir)
        }
    }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}
