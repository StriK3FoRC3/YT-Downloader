package com.strik3forc3.ytdownloader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * yt-dlp's Python environment is *not* unpacked here. `YtDlpEngine.ensureInitialised()`
 * extracts tens of megabytes on first run, which would block application startup; it is
 * driven from the first screen instead, behind a visible setup state.
 */
@HiltAndroidApp
class YtdlApplication : Application()
