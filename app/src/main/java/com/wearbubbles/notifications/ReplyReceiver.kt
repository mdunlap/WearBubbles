package com.wearbubbles.notifications

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wearbubbles.api.ApiClient
import com.wearbubbles.api.dto.SendMessageRequest
import com.wearbubbles.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val reply = results.getCharSequence("reply")?.toString() ?: return
        val chatGuid = intent.getStringExtra("chatGuid") ?: return

        // Keep the process alive until the network call finishes
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val sent = try {
                val settings = SettingsDataStore(context)
                val serverUrl = settings.getServerUrl()
                val password = settings.getPassword()
                val api = ApiClient.getInstance(serverUrl)

                val response = api.sendMessage(
                    password = password,
                    body = SendMessageRequest(
                        chatGuid = chatGuid,
                        message = reply,
                        tempGuid = "temp_${UUID.randomUUID()}"
                    )
                )
                response.status == 200
            } catch (e: Exception) {
                Log.e("ReplyReceiver", "Failed to send reply", e)
                false
            }

            if (sent) {
                NotificationHelper.updateNotificationAfterReply(context, chatGuid)
            } else {
                NotificationHelper.showReplyFailedNotification(context, chatGuid)
            }
            pendingResult.finish()
        }
    }
}
