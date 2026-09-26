package com.strik3forc3.ytdownloader.ytdlp.cookie

import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import com.strik3forc3.ytdownloader.ytdlp.DownloadLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **Unverified spike.** Obtains YouTube cookies from a Google account already present on
 * the device, via microG or Play Services, instead of asking the user to sign in again.
 *
 * The mechanism is `AccountManager.getAuthToken` with a `weblogin:` token type, which
 * historically returns a URL that, when fetched, sets browser cookies. microG implements
 * the `AccountManager` side of Play Services, so a device running microG can serve this
 * without Google's own framework.
 *
 * Three things could sink it, none of which can be settled without a device:
 *
 *  1. Google has progressively restricted `weblogin:` token types, possibly to
 *     signature-allowlisted callers. microG may be more permissive; real Play Services
 *     probably is not.
 *  2. It needs `GET_ACCOUNTS` plus a Google account actually registered on the device.
 *  3. It only helps users who run microG or GApps at all.
 *
 * Consequently this is an *accelerator*, never a dependency: [WebViewCookieProvider] must
 * remain fully functional on its own, and [isAvailable] returns false unless every
 * precondition is genuinely met. Until it is validated on hardware, this provider is not
 * offered in the UI.
 */
@Singleton
class MicroGCookieProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: DownloadLogger,
) : CookieProvider {

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val MICROG_PACKAGE = "com.google.android.gms"
        private const val FILE_NAME = "cookies-microg.txt"

        /** Returns a one-time URL that sets cookies for the continue target. */
        const val WEBLOGIN_TOKEN_TYPE =
            "weblogin:service=youtube&continue=https://www.youtube.com"
    }

    override val displayName: String = "Device Google account (experimental)"

    private val file: File get() = File(context.filesDir, FILE_NAME)

    /**
     * Requires the permission, a provider of Google accounts, and at least one account.
     * Any of these missing means the provider silently does not apply.
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (!hasGetAccountsPermission()) return@withContext false
        if (!hasAccountProvider()) return@withContext false
        runCatching {
            AccountManager.get(context).getAccountsByType(GOOGLE_ACCOUNT_TYPE).isNotEmpty()
        }.getOrDefault(false)
    }

    override suspend fun cookieFile(): File? = withContext(Dispatchers.IO) {
        file.takeIf { it.exists() && it.length() > 0 }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        Unit
    }

    private fun hasGetAccountsPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasAccountProvider(): Boolean = runCatching {
        context.packageManager.getPackageInfo(MICROG_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Placeholder for the token exchange. Deliberately not implemented against an
     * unverified API: shipping a plausible-looking `getAuthToken` call that silently
     * fails on every device would be worse than an explicit gap, because it would look
     * like a working feature in the settings screen.
     *
     * To validate on a microG device: request `GET_ACCOUNTS`, call
     * `AccountManager.getAuthToken(account, WEBLOGIN_TOKEN_TYPE, null, activity, null, null)`,
     * fetch the returned URL, and inspect whether the response sets `SAPISID`. If it
     * does, feed the resulting cookies through `NetscapeCookies.render` exactly as
     * [WebViewCookieProvider] does.
     */
    suspend fun capture(): Boolean {
        logger.warn("microG cookie capture is not implemented; use the in-app sign-in")
        return false
    }
}
