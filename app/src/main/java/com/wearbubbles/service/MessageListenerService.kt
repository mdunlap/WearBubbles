package com.wearbubbles.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.notifications.NotificationHelper
import kotlinx.coroutines.*

class MessageListenerService : Service() {

    companion object {
        private const val TAG = "MessageListenerService"
        private const val SERVICE_NOTIFICATION_ID = 9999

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MessageListenerService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses foreground-service starts from the background;
                // the service starts the next time the app is opened
                Log.w(TAG, "Could not start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MessageListenerService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(
            SERVICE_NOTIFICATION_ID,
            buildServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    // Runs on every start (not just onCreate) so new credentials reach an already-running service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            try {
                // Connect the shared socket — IncomingMessageHandler posts the notifications.
                // The foreground service itself keeps the process alive; no loop needed.
                if (!(application as WearBubblesApp).sessionManager.connectSocket()) {
                    Log.d(TAG, "No credentials, stopping")
                    // startId-scoped: a sign-in that started us again meanwhile wins
                    stopSelf(startId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listener", e)
            }
        }
        return START_STICKY
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("WearBubbles")
            .setContentText("Listening for messages")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "Service destroyed")
    }
}
