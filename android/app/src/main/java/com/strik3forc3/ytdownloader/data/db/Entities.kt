package com.strik3forc3.ytdownloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.VideoFormat

/** Where a queue item currently sits. */
enum class ItemStatus { QUEUED, EXTRACTING, RUNNING, PROCESSING, DONE, FAILED, CANCELLED }

/**
 * One item of work, persisted so a queue survives process death.
 *
 * The Windows app holds its queue purely in memory, which is fine for a desktop window
 * that stays open. On Android the process can be killed at any time, so an interrupted
 * 40-item playlist has to be recoverable.
 *
 * The download options are stored per item rather than read from current settings — this
 * is the persisted form of the immutable snapshot described in
 * `core/DownloadOptions.kt`.
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val status: ItemStatus,
    val mode: DownloadMode,
    val audioFormat: AudioFormat,
    val videoFormat: VideoFormat,
    val resolution: Resolution,
    val profileName: String,
    val queuedAt: Long,
    val position: Int,
    val failureReason: String? = null,
    val outputName: String? = null,
    /** SAF document URI of the finished file, so a completed row can open it. */
    val outputUri: String? = null,
    val thumbnailUrl: String? = null,
    val sourceBitrateKbps: Double? = null,
    val sourceCodec: String? = null,
) {
    val isTerminal: Boolean
        get() = status == ItemStatus.DONE || status == ItemStatus.FAILED || status == ItemStatus.CANCELLED
}

/**
 * A saved profile. Ported from `FilterProfile` (reference line 2911), which the Windows
 * app flattens into numbered `settings.ini` keys.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val name: String,
    val presetRules: List<String>,
    val customRules: List<String>,
    val embedThumbnail: Boolean,
    val metadataEnabled: Boolean,
    val cleanTitles: Boolean,
    val removeArtistPrefix: Boolean,
    val bitrateSetting: String,
    val metadataFields: List<String>,
)
