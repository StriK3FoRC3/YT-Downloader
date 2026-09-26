package com.strik3forc3.ytdownloader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds text handed over by the share sheet until the home screen can take it.
 *
 * A singleton rather than an activity result or navigation argument, because the two ends
 * do not reliably coexist: a share can arrive on a cold start before any screen is
 * composed. Parking the payload here means it is picked up whenever the home screen does
 * come up, instead of being dropped in the gap.
 *
 * Note the share deliberately does *not* queue anything. It fills the link box, so the
 * format, resolution and profile can be chosen before committing — a shared video is
 * usually the point at which you decide whether you want audio or video.
 */
@Singleton
class ShareInbox @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(text: String?) {
        if (!text.isNullOrBlank()) _pending.value = text
    }

    /** Takes the pending payload exactly once. */
    fun consume(): String? {
        val value = _pending.value
        _pending.value = null
        return value
    }
}
