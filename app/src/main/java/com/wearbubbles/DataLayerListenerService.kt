package com.wearbubbles

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DataLayerListenerService : WearableListenerService() {

    data class SetupPayload(val url: String, val password: String)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/setup-credentials" -> handleSetupCredentials(messageEvent)
            "/reset-watch" -> handleReset(messageEvent)
            "/request-status" -> handleRequestStatus(messageEvent)
        }
    }

    private fun handleSetupCredentials(messageEvent: MessageEvent) {
        serviceScope.launch {
            try {
                val json = String(messageEvent.data)
                val payload = Gson().fromJson(json, SetupPayload::class.java)
                val settings = SettingsDataStore(applicationContext)
                settings.saveCredentials(payload.url, payload.password)

                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "ok".toByteArray())

                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                applicationContext.startActivity(launchIntent)
            } catch (e: Exception) {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "error".toByteArray())
            }
        }
    }

    private fun handleRequestStatus(messageEvent: MessageEvent) {
        serviceScope.launch {
            try {
                val app = applicationContext as WearBubblesApp
                val settings = SettingsDataStore(applicationContext)
                val db = AppDatabase.getInstance(applicationContext)
                val chatDao = db.chatDao()

                val status = mapOf(
                    "serverConnected" to app.socketManager.isConnected,
                    "serverUrl" to settings.getServerUrl(),
                    "totalConversations" to chatDao.getChatCount(),
                    "unreadConversations" to chatDao.getUnreadCount(),
                    "lastMessageTime" to chatDao.getLastMessageTime(),
                )

                val json = Gson().toJson(status)
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/watch-status", json.toByteArray())
            } catch (_: Exception) {
                // Best-effort status reporting
            }
        }
    }

    private fun handleReset(messageEvent: MessageEvent) {
        serviceScope.launch {
            try {
                val settings = SettingsDataStore(applicationContext)
                settings.clear()

                val db = AppDatabase.getInstance(applicationContext)
                db.clearAllTables()

                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/reset-ack", "ok".toByteArray())

                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                applicationContext.startActivity(launchIntent)
            } catch (e: Exception) {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/reset-ack", "error".toByteArray())
            }
        }
    }
}
