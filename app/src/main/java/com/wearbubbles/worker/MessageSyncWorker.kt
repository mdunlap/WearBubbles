package com.wearbubbles.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.wearbubbles.api.ApiClient
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import com.wearbubbles.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MessageSyncWorker"
        private const val WORK_NAME = "message_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MessageSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val settingsDataStore = SettingsDataStore(applicationContext)
        if (!settingsDataStore.hasCredentials()) return Result.success()

        return try {
            val serverUrl = settingsDataStore.getServerUrl()
            val password = settingsDataStore.getPassword()
            val api = ApiClient.getInstance(serverUrl)
            val db = AppDatabase.getInstance(applicationContext)

            // Fetch latest chats
            val response = api.getChats(
                password = password,
                body = ChatQueryRequest(limit = 20)
            )

            // Check for new unread messages
            var newMessageCount = 0
            var lastSenderName = ""

            val contactRepo = ContactRepository(api, password, db.contactDao())
            contactRepo.loadFromCache() // Use cached contacts only — don't fetch all from API

            for (chat in response.data) {
                if (chat.hasUnreadMessage == true && chat.lastMessage?.isFromMe == false) {
                    newMessageCount++
                    val addresses = chat.participants?.map { it.address } ?: emptyList()
                    lastSenderName = if (addresses.isNotEmpty()) {
                        contactRepo.getDisplayNameForAddresses(addresses)
                    } else {
                        chat.displayName ?: "Unknown"
                    }
                }
            }

            if (newMessageCount > 0) {
                val title = if (newMessageCount == 1) "New message from $lastSenderName" else "$newMessageCount new messages"
                val text = if (newMessageCount == 1) "Tap to view" else "Tap to view conversations"
                NotificationHelper.showNewMessageNotification(
                    applicationContext, title, text, "worker_sync"
                )
                NotificationHelper.vibrateIfEnabled(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }
}
