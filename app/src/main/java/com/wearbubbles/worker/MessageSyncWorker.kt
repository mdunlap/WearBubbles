package com.wearbubbles.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.wearbubbles.MainActivity
import com.wearbubbles.R
import com.wearbubbles.api.ApiClient
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.data.ContactRepository
import com.wearbubbles.data.SettingsDataStore
import com.wearbubbles.db.AppDatabase
import java.util.concurrent.TimeUnit

class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MessageSyncWorker"
        private const val WORK_NAME = "message_sync"
        private const val CHANNEL_ID = "new_messages"
        private const val NOTIFICATION_ID = 1001

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

            val contactRepo = ContactRepository(api, password)
            contactRepo.loadContacts()

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
                showNotification(newMessageCount, lastSenderName)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }

    private fun showNotification(count: Int, senderName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new iMessages"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (count == 1) "New message from $senderName" else "$count new messages"
        val text = if (count == 1) "Tap to view" else "Tap to view conversations"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
