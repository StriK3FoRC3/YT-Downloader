package com.strik3forc3.ytdownloader.data

import com.strik3forc3.ytdownloader.core.BitrateLadder
import com.strik3forc3.ytdownloader.core.BitrateSetting
import com.strik3forc3.ytdownloader.core.MetadataField
import com.strik3forc3.ytdownloader.core.Profile
import com.strik3forc3.ytdownloader.data.db.ProfileDao
import com.strik3forc3.ytdownloader.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(private val dao: ProfileDao) {

    val profiles: Flow<List<Profile>> = dao.observeAll().map { rows ->
        rows.map { it.toProfile() }.ifEmpty { listOf(Profile.Default) }
    }

    /** Seeds the stock profile on first run (reference `AddDefaultProfile`, line 2519). */
    suspend fun ensureDefaultExists() {
        if (dao.count() == 0) dao.upsert(Profile.Default.toEntity())
    }

    suspend fun byName(name: String): Profile =
        dao.byName(name)?.toProfile() ?: Profile.Default

    suspend fun save(profile: Profile) = dao.upsert(profile.toEntity())

    /** The stock profile is the fallback for every item, so it cannot be removed. */
    suspend fun delete(name: String): Boolean {
        if (name == Profile.DEFAULT_NAME) return false
        dao.delete(name)
        return true
    }
}

private fun ProfileEntity.toProfile() = Profile(
    name = name,
    presetRules = presetRules,
    customRules = customRules,
    embedThumbnail = embedThumbnail,
    metadataEnabled = metadataEnabled,
    cleanTitles = cleanTitles,
    removeArtistPrefix = removeArtistPrefix,
    bitrate = BitrateLadder.parse(bitrateSetting),
    metadataFields = metadataFields.mapNotNull { stored ->
        MetadataField.entries.firstOrNull { it.id.equals(stored, ignoreCase = true) }
    }.toSet(),
)

private fun Profile.toEntity() = ProfileEntity(
    name = name,
    presetRules = presetRules,
    customRules = customRules,
    embedThumbnail = embedThumbnail,
    metadataEnabled = metadataEnabled,
    cleanTitles = cleanTitles,
    removeArtistPrefix = removeArtistPrefix,
    bitrateSetting = when (val setting = bitrate) {
        is BitrateSetting.Fixed -> "${setting.kbps} kbps"
        BitrateSetting.Automatic -> "Automatic"
    },
    metadataFields = metadataFields.map { it.id },
)
