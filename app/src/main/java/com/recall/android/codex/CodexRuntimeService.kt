package com.recall.android.codex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.recall.android.MainActivity
import com.recall.android.R
import com.recall.android.RecallApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CodexRuntimeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtime by lazy { (application as RecallApplication).container.codexRuntime }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtime.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(getString(R.string.codex_notification_starting)))
        scope.launch {
            runCatching { runtime.start() }
                .onSuccess { updateNotification(getString(R.string.codex_notification_running)) }
                .onFailure { updateNotification(it.message ?: getString(R.string.codex_notification_failed)) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runtime.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.codex_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recall)
            .setContentTitle(getString(R.string.codex_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    companion object {
        private const val CHANNEL_ID = "recall_codex_runtime"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "com.recall.android.codex.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CodexRuntimeService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CodexRuntimeService::class.java).setAction(ACTION_STOP))
        }
    }
}
