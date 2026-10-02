package com.wearbubbles.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.wearbubbles.WearBubblesApp
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.data.UpdateChecker
import com.wearbubbles.data.attachmentLabel
import com.wearbubbles.data.currentSession
import com.wearbubbles.data.toEntity
import com.wearbubbles.data.upsertFromServer
import com.wearbubbles.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Periodic fallback for when the socket isn't delivering (e.g. the process was killed).
 * Only notifies for messages newer than what Room already has, so a chat that stays
 * unread doesn't re-alert every run, and anything the socket already showed is skipped.
 */
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
        Log.d(TAG, "doWork() started")
        UpdateChecker.checkAndNotify(applicationContext)

        val app = applicationContext as WearBubblesApp
        val session = app.settings.currentSession()
        if (session == null) {
            Log.d(TAG, "No credentials, skipping")
            return Result.success()
        }

        return try {
            val chatDao = app.database.chatDao()
            val response = session.api.getChats(
                password = session.password,
                body = ChatQueryRequest(limit = 20)
            )

            // First sync on an empty cache only seeds Room — don't alert for old unread chats
            val isFirstSync = chatDao.getChatCount() == 0

            val newMessages = response.data.filter { chat ->
                val last = chat.lastMessage ?: return@filter false
                if (chat.hasUnreadMessage != true || last.isFromMe || last.associatedMessageType != null) {
                    return@filter false
                }
                val knownDate = chatDao.getChatByGuid(chat.guid)?.lastMessageDate ?: 0L
                (last.dateCreated ?: 0L) > knownDate
            }

            chatDao.upsertFromServer(response.data.map { it.toEntity() })

            Log.d(TAG, "Found ${newMessages.size} chats with new messages (first sync: $isFirstSync)")
            if (!isFirstSync && newMessages.isNotEmpty()) {
                app.contactRepository.loadFromCache()
                for (chat in newMessages) {
                    val last = chat.lastMessage!!
                    val sender = last.handle?.address
                        ?.let { app.contactRepository.getDisplayName(it) }
                        ?: chat.displayName
                        ?: "Unknown"
                    val preview = last.text?.ifBlank { null }
                        ?: attachmentLabel(last.attachments?.firstOrNull { it.hideAttachment != true }?.mimeType)
                        ?: "New message"
                    NotificationHelper.showNewMessageNotification(applicationContext, sender, preview, chat.guid)
                }
                NotificationHelper.vibrateIfEnabled(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }
}
