package com.strik3forc3.ytdownloader.ytdlp.cookie

import java.io.File

/**
 * Supplies a Netscape-format `cookies.txt` for yt-dlp's `--cookies` flag.
 *
 * The Windows app asks yt-dlp to read cookies straight out of an installed browser
 * (`--cookies-from-browser`, reference line 1915). That is impossible on Android: the
 * app sandbox forbids reading another app's cookie store, so the browser picker has no
 * meaning here and the cookies must come from somewhere inside our own sandbox.
 *
 * Implementations are interchangeable so the source can change without touching the
 * download path.
 */
interface CookieProvider {

    /** Human-readable name for the settings screen. */
    val displayName: String

    /** Whether this provider can currently produce cookies on this device. */
    suspend fun isAvailable(): Boolean

    /**
     * Returns a cookie file ready to hand to yt-dlp, or null when the user is not signed
     * in through this provider. Implementations must write to app-private storage.
     */
    suspend fun cookieFile(): File?

    /** Discards any stored cookies. */
    suspend fun clear()
}

/** Used when the user has turned cookies off, matching the reference's "Disabled" option. */
object NoCookieProvider : CookieProvider {
    override val displayName: String = "Disabled"
    override suspend fun isAvailable(): Boolean = true
    override suspend fun cookieFile(): File? = null
    override suspend fun clear() = Unit
}
