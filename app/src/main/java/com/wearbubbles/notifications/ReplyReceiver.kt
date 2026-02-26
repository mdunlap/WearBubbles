package com.wearbubbles.notifications

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wearbubbles.WearBubblesApp
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsDataStore(context)
                val serverUrl = settings.getServerUrl()
                val password = settings.getPassword()
                val api = ApiClient.getInstance(serverUrl)

                api.sendMessage(
                    password = password,
                    body = SendMessageRequest(
                        chatGuid = chatGuid,
                        message = reply,
                        tempGuid = "temp_${UUID.randomUUID()}"
                    )
                )
            } catch (_: Exception) {}

            // Update notification to show the reply was sent
            NotificationHelper.updateNotificationAfterReply(context, chatGuid)
        }
    }
}
