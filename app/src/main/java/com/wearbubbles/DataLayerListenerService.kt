package com.wearbubbles

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import kotlinx.coroutines.runBlocking

class DataLayerListenerService : WearableListenerService() {

    data class SetupPayload(val url: String, val password: String)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/setup-credentials" -> handleSetupCredentials(messageEvent)
            "/reset-watch" -> handleReset(messageEvent)
        }
    }

    private fun handleSetupCredentials(messageEvent: MessageEvent) {
        try {
            val json = String(messageEvent.data)
            val payload = Gson().fromJson(json, SetupPayload::class.java)
            val settings = SettingsDataStore(applicationContext)
            runBlocking {
                settings.saveCredentials(payload.url, payload.password)
            }
            Wearable.getMessageClient(applicationContext)
                .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "ok".toByteArray())

            // Launch the app so it picks up the new credentials
            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(launchIntent)
        } catch (e: Exception) {
            Wearable.getMessageClient(applicationContext)
                .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "error".toByteArray())
        }
    }

    private fun handleReset(messageEvent: MessageEvent) {
        try {
            val settings = SettingsDataStore(applicationContext)
            runBlocking { settings.clear() }

            // Clear the database
            val db = AppDatabase.getInstance(applicationContext)
            runBlocking {
                db.clearAllTables()
            }

            Wearable.getMessageClient(applicationContext)
                .sendMessage(messageEvent.sourceNodeId, "/reset-ack", "ok".toByteArray())

            // Relaunch to setup screen
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
