package com.strik3forc3.ytdownloader.ytdlp.cookie

import android.content.Context
import android.webkit.CookieManager
import com.strik3forc3.ytdownloader.core.NetscapeCookies
import com.strik3forc3.ytdownloader.ytdlp.DownloadLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures YouTube cookies from an in-app WebView login.
 *
 * The Windows app hands `--cookies-from-browser` to yt-dlp, which reads Firefox's or
 * Chrome's cookie store directly. Android's sandbox makes that impossible, so the user
 * signs in inside our own WebView and the resulting cookies are written to app-private
 * storage.
 *
 * The cookie file is readable only by this app's uid, and its contents are never logged.
 */
@Singleton
class WebViewCookieProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: DownloadLogger,
) : CookieProvider {

    companion object {
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube"

        private const val FILE_NAME = "cookies.txt"
        private val DOMAINS = mapOf(
            ".youtube.com" to "https://www.youtube.com",
            ".google.com" to "https://accounts.google.com",
        )
    }

    override val displayName: String = "In-app sign-in"

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun isAvailable(): Boolean = true

    override suspend fun cookieFile(): File? = withContext(Dispatchers.IO) {
        file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Reads the WebView cookie jar and writes the file. Returns false when no
     * session cookie is present, so the caller can say the sign-in did not complete
     * rather than letting the download fail later with a bot-check error.
     */
    suspend fun capture(): Boolean = withContext(Dispatchers.IO) {
        val manager = CookieManager.getInstance()
        val cookies = DOMAINS.flatMap { (domain, url) ->
            NetscapeCookies.parseHeader(domain, manager.getCookie(url))
        }

        if (!NetscapeCookies.looksAuthenticated(cookies)) {
            logger.warn("cookie capture found no session cookie")
            return@withContext false
        }

        file.writeText(NetscapeCookies.render(cookies, System.currentTimeMillis() / 1000))
        // Owner-only. The file holds live session credentials.
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)

        logger.info("captured ${cookies.size} cookies")
        true
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Unit
    }
}
