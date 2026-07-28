package com.strik3forc3.ytdownloader.ui.cookie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.strik3forc3.ytdownloader.data.CookieMode
import com.strik3forc3.ytdownloader.data.SettingsRepository
import com.strik3forc3.ytdownloader.ytdlp.cookie.WebViewCookieProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Signs the user in to YouTube inside the app so cookies can be captured.
 *
 * `--cookies-from-browser`, which the Windows app relies on, cannot work here: the
 * sandbox forbids reading another app's cookie store. Signing in through our own WebView
 * puts the cookies inside our sandbox, where they can be written to a file for yt-dlp.
 *
 * Deliberately a plain `WebView` rather than a Compose screen — Google's sign-in flow is
 * fussy about the browser environment, and wrapping it adds risk for no benefit.
 */
@AndroidEntryPoint
class CookieLoginActivity : ComponentActivity() {

    @Inject lateinit var provider: WebViewCookieProvider
    @Inject lateinit var settingsRepository: SettingsRepository

    companion object {
        fun intent(context: Context) = Intent(context, CookieLoginActivity::class.java)
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        webView = WebView(this).apply {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            // Google's sign-in flow does not work without JavaScript or DOM storage.
            // The WebView only ever loads Google's own sign-in origin and renders no
            // app-supplied content, so there is no injection surface here.
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Reaching youtube.com signed in is the signal the flow completed.
                    if (url != null && url.contains("youtube.com")) {
                        captureAndFinish()
                    }
                }
            }
            loadUrl(WebViewCookieProvider.LOGIN_URL)
        }

        setContentView(webView)
    }

    private fun captureAndFinish() {
        lifecycleScope.launch {
            CookieManager.getInstance().flush()
            if (provider.capture()) {
                settingsRepository.setCookieMode(CookieMode.WEBVIEW)
                Toast.makeText(this@CookieLoginActivity, "Signed in", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            // Otherwise the sign-in has not completed yet; leave the WebView open.
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
