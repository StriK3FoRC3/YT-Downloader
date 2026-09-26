package com.strik3forc3.ytdownloader.core

/**
 * Builds the metadata-related yt-dlp arguments.
 *
 * Contract: `docs/download-rules.md` §7.
 * Ported from `AppendDisabledMetadataOverrides` (reference line 2199) and the branch
 * selection in `DownloadOneAsync` (reference lines 1376–1387).
 *
 * yt-dlp embeds everything by default, so the model is inverted: *disabled* fields are
 * blanked with `--parse-metadata ":(?P<meta_FIELD>)"` rather than enabled ones being
 * requested.
 */
object MetadataArgs {

    /** yt-dlp keys blanked when a UI field is switched off. */
    private val BLANKED_KEYS: Map<MetadataField, List<String>> = mapOf(
        MetadataField.TITLE to listOf("title"),
        MetadataField.ARTIST to listOf("artist", "composer"),
        MetadataField.ALBUM to listOf("album", "album_artist", "show"),
        MetadataField.DATE to listOf("date"),
        MetadataField.DESCRIPTION to listOf("description", "synopsis"),
        MetadataField.SOURCE to listOf("purl", "comment"),
        MetadataField.GENRE to listOf("genre"),
        MetadataField.TRACK to listOf("track", "disc"),
    )

    /**
     * @param enabled the master metadata toggle.
     * @param fields the individual fields the user has selected.
     */
    fun build(enabled: Boolean, fields: Set<MetadataField>): List<String> {
        val args = mutableListOf<String>()
        val hasStandardField = enabled && fields.any { it.isStandard }

        if (hasStandardField) {
            args += listOf("--embed-metadata", "--no-embed-info-json")
            for ((field, keys) in BLANKED_KEYS) {
                if (field !in fields) {
                    for (key in keys) {
                        args += listOf("--parse-metadata", ":(?P<meta_$key>)")
                    }
                }
            }
        } else {
            // Divergence from Windows, recorded in rules §7 and the divergence table.
            //
            // The reference emits nothing when the master toggle is on but *only*
            // Chapters is selected, so yt-dlp's default embedding silently applies —
            // the opposite of what the user asked for. That is a gap in the reference,
            // not a design, so this branch covers it.
            args += listOf("--no-embed-metadata", "--no-embed-info-json")
        }

        args += if (enabled && MetadataField.CHAPTERS in fields) {
            "--embed-chapters"
        } else {
            "--no-embed-chapters"
        }

        return args
    }
}
