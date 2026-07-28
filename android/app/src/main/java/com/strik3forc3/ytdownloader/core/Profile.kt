package com.strik3forc3.ytdownloader.core

/**
 * A reusable set of download preferences.
 *
 * Ported from `FilterProfile` (reference line 2911). The Windows app persists these into
 * `settings.ini` as flat `Profile<N><Key>=` lines; Android stores them in DataStore.
 *
 * Note the cookie source is *not* carried over — `--cookies-from-browser` cannot work in
 * the Android sandbox. See `docs/download-rules.md` "Platform divergences".
 */
data class Profile(
    val name: String,
    val presetRules: List<String> = emptyList(),
    val customRules: List<String> = emptyList(),
    val embedThumbnail: Boolean = false,
    val metadataEnabled: Boolean = false,
    val cleanTitles: Boolean = false,
    val removeArtistPrefix: Boolean = false,
    val bitrate: BitrateSetting = BitrateSetting.Automatic,
    val metadataFields: Set<MetadataField> = emptySet(),
) {
    /** Preset and custom phrases merged, deduplicated and blank-stripped. */
    val rules: List<String> get() = TitleCleanup.mergeRules(presetRules, customRules)

    companion object {
        const val DEFAULT_NAME = "Default"

        /**
         * The stock profile. Everything off, matching `ConfigureDefaultProfile`
         * (reference line 2526) — a fresh install downloads exactly what YouTube served,
         * with no rewriting.
         */
        val Default = Profile(name = DEFAULT_NAME)
    }
}
