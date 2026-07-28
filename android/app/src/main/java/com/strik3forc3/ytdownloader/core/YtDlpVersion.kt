package com.strik3forc3.ytdownloader.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Reads yt-dlp's date-based version string, e.g. `2026.07.04`.
 *
 * This matters more on Android than it looks. The yt-dlp bundled inside
 * `youtubedl-android` is frozen at whatever shipped with that library release, so a
 * freshly installed app can be many months behind. YouTube rotates its player JavaScript
 * continuously, and once an installed yt-dlp can no longer solve the signature and
 * n-parameter challenges, YouTube offers it storyboard images only — every download then
 * fails with the misleading "Requested format is not available".
 *
 * A stale yt-dlp is therefore the *default* state of a new install, not an edge case.
 */
object YtDlpVersion {

    /**
     * Beyond this, assume YouTube has moved on.
     *
     * yt-dlp ships roughly monthly, so a current stable release is routinely a few weeks
     * old; a tighter threshold would flag every healthy install and spend a network round
     * trip on every launch to be told it is already current.
     */
    const val STALE_AFTER_DAYS = 30L

    private val FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val VERSION = Regex("""(\d{4}\.\d{2}\.\d{2})""")

    /** Parses the leading date, ignoring any nightly suffix. Null when unrecognisable. */
    fun releaseDate(version: String?): LocalDate? {
        val match = VERSION.find(version.orEmpty())?.groupValues?.get(1) ?: return null
        return runCatching { LocalDate.parse(match, FORMAT) }.getOrNull()
    }

    fun ageInDays(version: String?, today: LocalDate): Long? =
        releaseDate(version)?.let { ChronoUnit.DAYS.between(it, today) }

    /**
     * An unparseable or absent version counts as stale — better to attempt an update that
     * turns out to be unnecessary than to leave a broken install alone.
     */
    fun isStale(version: String?, today: LocalDate): Boolean =
        (ageInDays(version, today) ?: Long.MAX_VALUE) > STALE_AFTER_DAYS
}
