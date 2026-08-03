package com.strik3forc3.ytdownloader.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.strik3forc3.ytdownloader.core.AudioFormat
import com.strik3forc3.ytdownloader.core.DownloadMode
import com.strik3forc3.ytdownloader.core.Resolution
import com.strik3forc3.ytdownloader.core.VideoFormat
import kotlinx.coroutines.flow.Flow

/**
 * Stores enums by name rather than ordinal, so reordering an enum cannot silently
 * reinterpret existing rows.
 */
class Converters {
    @TypeConverter fun listToString(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun stringToList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n")

    @TypeConverter fun statusToString(value: ItemStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): ItemStatus =
        runCatching { ItemStatus.valueOf(value) }.getOrDefault(ItemStatus.QUEUED)

    @TypeConverter fun modeToString(value: DownloadMode): String = value.name
    @TypeConverter fun stringToMode(value: String): DownloadMode =
        runCatching { DownloadMode.valueOf(value) }.getOrDefault(DownloadMode.AUDIO)

    @TypeConverter fun audioToString(value: AudioFormat): String = value.name
    @TypeConverter fun stringToAudio(value: String): AudioFormat =
        runCatching { AudioFormat.valueOf(value) }.getOrDefault(AudioFormat.MP3)

    @TypeConverter fun videoToString(value: VideoFormat): String = value.name
    @TypeConverter fun stringToVideo(value: String): VideoFormat =
        runCatching { VideoFormat.valueOf(value) }.getOrDefault(VideoFormat.MP4)

    @TypeConverter fun resolutionToString(value: Resolution): String = value.name
    @TypeConverter fun stringToResolution(value: String): Resolution =
        runCatching { Resolution.valueOf(value) }.getOrDefault(Resolution.HIGHEST)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun observeAll(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE status IN ('QUEUED','EXTRACTING','RUNNING','PROCESSING') ORDER BY position ASC")
    suspend fun pending(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items WHERE id = :id")
    suspend fun byId(id: String): QueueItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    @Upsert
    suspend fun upsert(item: QueueItemEntity)

    @Query("UPDATE queue_items SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: ItemStatus)

    @Query("UPDATE queue_items SET status = :status, failureReason = :reason WHERE id = :id")
    suspend fun setFailure(id: String, reason: String, status: ItemStatus = ItemStatus.FAILED)

    @Query("UPDATE queue_items SET status = :status, outputName = :outputName, outputUri = :outputUri WHERE id = :id")
    suspend fun setComplete(
        id: String,
        outputName: String?,
        outputUri: String?,
        status: ItemStatus = ItemStatus.DONE,
    )

    /** Requeues a failed item, clearing the previous reason so a stale error is not shown. */
    @Query("UPDATE queue_items SET status = :status, failureReason = NULL WHERE id = :id")
    suspend fun retry(id: String, status: ItemStatus = ItemStatus.QUEUED)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM queue_items")
    suspend fun nextPosition(): Int

    /** Used to deduplicate an incoming paste against what is already queued. */
    @Query("SELECT url FROM queue_items")
    suspend fun allUrls(): List<String>

    @Query("DELETE FROM queue_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM queue_items WHERE status IN ('DONE','FAILED','CANCELLED')")
    suspend fun clearFinished()

    @Query("DELETE FROM queue_items")
    suspend fun clearAll()

    /**
     * A process killed mid-download leaves rows claiming to be running. Reconciled at
     * startup rather than left to display a download that is not happening.
     */
    @Query("UPDATE queue_items SET status = 'QUEUED' WHERE status IN ('RUNNING','EXTRACTING','PROCESSING')")
    suspend fun requeueInterrupted()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE name = :name")
    suspend fun byName(name: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE name = :name")
    suspend fun delete(name: String)
}

@Database(
    entities = [QueueItemEntity::class, ProfileEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
    abstract fun profileDao(): ProfileDao

    companion object {
        /**
         * Adds the finished file's document URI so a completed row can be tapped to open
         * it. Written out rather than falling back to a destructive migration — losing a
         * user's saved profiles on an upgrade is the exact failure the Windows app has
         * when one line of `settings.ini` fails to parse.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE queue_items ADD COLUMN outputUri TEXT")
            }
        }
    }
}
