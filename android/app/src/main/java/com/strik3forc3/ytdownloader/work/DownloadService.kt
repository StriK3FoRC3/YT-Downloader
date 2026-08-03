package com.strik3forc3.ytdownloader.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.strik3forc3.ytdownloader.R
import com.strik3forc3.ytdownloader.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the download session alive while the app is backgrounded.
 *
 * The Windows app needs nothing like this — a desktop window runs until closed. Android
 * kills background work aggressively, so a foreground service with a visible
 * notification is the only way a 40-item playlist survives the user switching apps.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var queue: DownloadQueue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val ACTION_START = "start"
        const val ACTION_CANCEL = "cancel"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        /** Notification updates are cheap but not free; once a second is plenty. */
        private const val NOTIFICATION_INTERVAL_MS = 1000L

        fun start(context: Context) = send(context, ACTION_START)
        fun cancel(context: Context) = send(context, ACTION_CANCEL)

        private fun send(context: Context, action: String) {
            val intent = Intent(context, DownloadService::class.java).setAction(action)
            context.startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        observeProgress()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                queue.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWith(buildNotification("Preparing downloads", null))
                // Normally the queue is already running — the caller starts it, then
                // starts this service to keep the process alive. Calling start() again
                // is a no-op in that case, and resumes the queue when the system has
                // restarted the service on its own via START_STICKY.
                scope.launch { queue.start() }
            }
        }
        // The queue is persisted in Room, so a restart resumes from the database rather
        // than from a redelivered intent.
        return START_STICKY
    }

    private fun observeProgress() {
        scope.launch {
            queue.state
                .sample(NOTIFICATION_INTERVAL_MS)
                .collectLatest { state ->
                    if (!state.running && state.total > 0) {
                        stopSelf()
                        return@collectLatest
                    }

                    val done = state.completed + state.failed
                    val text = buildString {
                        append("$done of ${state.total}")
                        val speed = state.combinedSpeedBytesPerSecond
                        if (speed > 0) append(" · ${formatSpeed(speed)}")
                        if (state.failed > 0) append(" · ${state.failed} failed")
                    }
                    notify(buildNotification(text, state.overallFraction))
                }
        }
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, fraction: Float?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.cancel), cancel)
            .apply {
                if (fraction == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(1000, (fraction * 1000).toInt(), false)
                }
            }
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        val megabytes = bytesPerSecond / 1_000_000.0
        return if (megabytes >= 1) {
            String.format(java.util.Locale.US, "%.1f MB/s", megabytes)
        } else {
            String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1000.0)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
