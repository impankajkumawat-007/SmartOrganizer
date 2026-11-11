package com.smartorganizer.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.smartorganizer.agent.MainActivity
import com.smartorganizer.agent.R
import com.smartorganizer.agent.SmartOrganizerApp
import com.smartorganizer.agent.runtime.OrganizerRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OrganizerService : LifecycleService() {
    private val runtime: OrganizerRuntime
        get() = (application as SmartOrganizerApp).runtime

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Watching the inbox. New files are sorted offline."),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        runtime.setWatchingFromService(true)
        runtime.ensureEngineLoaded()
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { runtime.processInboxOnce() }
                    .onFailure { error ->
                        if (error !is kotlinx.coroutines.CancellationException) {
                            runtime.recordFailure("Inbox watch stopped on this pass", error)
                        }
                    }
                val processing = runtime.uiState.value.processingName
                updateNotification(
                    if (processing != null) {
                        "Sorting $processing"
                    } else {
                        "Watching the inbox with ${runtime.uiState.value.selectedModel.displayName}"
                    }
                )
                delay(POLL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runtime.setWatchingFromService(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watch_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun notification(text: String): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.watch_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(launch)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        private const val CHANNEL_ID = "organizer-watch"
        private const val NOTIFICATION_ID = 31
        private const val POLL_MS = 6_000L
        private const val ACTION_STOP = "com.smartorganizer.agent.STOP_WATCH"

        fun start(context: Context) {
            val intent = Intent(context, OrganizerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrganizerService::class.java))
        }
    }
}
