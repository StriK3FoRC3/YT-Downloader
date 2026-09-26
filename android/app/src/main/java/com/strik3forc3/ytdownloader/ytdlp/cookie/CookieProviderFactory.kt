package com.strik3forc3.ytdownloader.ytdlp.cookie

import com.strik3forc3.ytdownloader.data.CookieMode
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves the user's cookie setting to a provider. */
@Singleton
class CookieProviderFactory @Inject constructor(
    private val webView: WebViewCookieProvider,
    private val microG: MicroGCookieProvider,
) {
    fun forMode(mode: CookieMode): CookieProvider = when (mode) {
        CookieMode.DISABLED -> NoCookieProvider
        CookieMode.WEBVIEW -> webView
        CookieMode.MICROG -> microG
    }

    /** Providers that can currently work on this device, for the settings screen. */
    suspend fun available(): List<Pair<CookieMode, CookieProvider>> = buildList {
        add(CookieMode.DISABLED to NoCookieProvider)
        add(CookieMode.WEBVIEW to webView)
        if (microG.isAvailable()) add(CookieMode.MICROG to microG)
    }
}
