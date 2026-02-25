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
import com.wearbubbles.api.ApiClient
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.socket.SocketEvent
import kotlinx.coroutines.*

class MessageListenerService : Service() {

    companion object {
        private const val TAG = "MessageListenerService"
        private const val SERVICE_NOTIFICATION_ID = 9999

        fun start(context: Context) {
            val intent = Intent(context, MessageListenerService::class.java)
            context.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contactRepository: ContactRepository? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        startForeground(
            SERVICE_NOTIFICATION_ID,
            buildServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        connectSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun connectSocket() {
        scope.launch {
            try {
                val settings = SettingsDataStore(this@MessageListenerService)
                if (!settings.hasCredentials()) {
                    Log.d(TAG, "No credentials, stopping")
                    stopSelf()
                    return@launch
                }

                val serverUrl = settings.getServerUrl()
                val password = settings.getPassword()
                val api = ApiClient.getInstance(serverUrl)
                val db = AppDatabase.getInstance(this@MessageListenerService)

                contactRepository = ContactRepository(api, password, db.contactDao())
                contactRepository?.loadFromCache()

                val socketManager = (application as WearBubblesApp).socketManager
                if (!socketManager.isConnected) {
                    socketManager.connect(serverUrl, password, ApiClient.getHttpClient())
                }

                // Listen for new messages and show notifications
                socketManager.events.collect { event ->
                    when (event) {
                        is SocketEvent.NewMessage -> {
                            if (!event.message.isFromMe) {
                                showMessageNotification(event)
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listener", e)
            }
        }
    }

    private suspend fun showMessageNotification(event: SocketEvent.NewMessage) {
        val chatGuid = event.message.chats?.firstOrNull()?.guid ?: return
        val senderAddress = event.message.handle?.address
        val senderName = if (senderAddress != null) {
            contactRepository?.getDisplayName(senderAddress) ?: senderAddress
        } else {
            "Unknown"
        }
        val text = event.message.text ?: "New message"

        NotificationHelper.showNewMessageNotification(
            this, senderName, text, chatGuid
        )
        NotificationHelper.vibrateIfEnabled(this)
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
