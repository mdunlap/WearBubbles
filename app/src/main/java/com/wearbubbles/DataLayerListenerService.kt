package com.wearbubbles

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.wearbubbles.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DataLayerListenerService : WearableListenerService() {

    data class SetupPayload(val url: String, val password: String)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = applicationContext as WearBubblesApp

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
                app.sessionManager.signIn(payload.url.trim(), payload.password)

                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "ok".toByteArray())

                if (!relaunchIfVisible()) {
                    NotificationHelper.showSetupCompleteNotification(applicationContext)
                }
            } catch (e: Exception) {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "error".toByteArray())
            }
        }
    }

    private fun handleRequestStatus(messageEvent: MessageEvent) {
        serviceScope.launch {
            try {
                val chatDao = app.database.chatDao()

                val status = mapOf(
                    "serverConnected" to app.socketManager.isConnected,
                    "serverUrl" to app.settings.getServerUrl(),
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
                app.sessionManager.signOut()

                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/reset-ack", "ok".toByteArray())

                // Show the setup screen if the app is open; otherwise it appears on next launch
                relaunchIfVisible()
            } catch (e: Exception) {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/reset-ack", "error".toByteArray())
            }
        }
    }

    /**
     * Restarts the UI so it picks up the new state. Android blocks activity starts from
     * the background, so this only happens when the app is on screen; returns whether it did.
     */
    private suspend fun relaunchIfVisible(): Boolean = withContext(Dispatchers.Main) {
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (visible) {
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(launchIntent)
        }
        visible
    }
}
