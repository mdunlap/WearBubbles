package com.wearbubbles

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import com.wearbubbles.data.SettingsDataStore
import kotlinx.coroutines.runBlocking

class DataLayerListenerService : WearableListenerService() {

    data class SetupPayload(val url: String, val password: String)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/setup-credentials") {
            try {
                val json = String(messageEvent.data)
                val payload = Gson().fromJson(json, SetupPayload::class.java)
                val settings = SettingsDataStore(applicationContext)
                runBlocking {
                    settings.saveCredentials(payload.url, payload.password)
                }
                // Send ack back to phone
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "ok".toByteArray())
            } catch (e: Exception) {
                Wearable.getMessageClient(applicationContext)
                    .sendMessage(messageEvent.sourceNodeId, "/setup-credentials-ack", "error".toByteArray())
            }
        }
    }
}
