package com.wearbubbles.data

import android.content.Context
import android.util.Log
import com.wearbubbles.api.dto.MessageDto
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.MessageDao
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.socket.SocketEvent
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single consumer of socket events: keeps Room in sync and posts notifications.
 * Started once from WearBubblesApp, so it lives as long as the process (and the
 * socket) does — notifications keep working after the UI is closed.
 */
class IncomingMessageHandler(
    private val context: Context,
    private val socketManager: SocketManager,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
    private val contactRepository: ContactRepository
) {
    companion object {
        private const val TAG = "IncomingMessageHandler"
        private const val MAX_RECENT_GUIDS = 200
    }

    // Track recently seen message guids to prevent duplicate notifications
    private val recentMessageGuids = LinkedHashSet<String>()

    fun start(scope: CoroutineScope) {
        scope.launch {
            contactRepository.loadFromCache()
            socketManager.events.collect { event ->
                // One bad event must not kill the collector (and with it all notifications)
                try {
                    when (event) {
                        is SocketEvent.NewMessage -> onNewMessage(event.message)
                        is SocketEvent.UpdatedMessage -> onUpdatedMessage(event.message)
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle socket event", e)
                }
            }
        }
    }

    private suspend fun onUpdatedMessage(message: MessageDto) {
        val chatGuid = message.chats?.firstOrNull()?.guid ?: return
        messageDao.upsertMessage(message.toEntity(chatGuid))
    }

    private suspend fun onNewMessage(message: MessageDto) {
        val chatGuid = message.chats?.firstOrNull()?.guid ?: return
        messageDao.upsertMessage(message.toEntity(chatGuid))

        val isDuplicate = !recentMessageGuids.add(message.guid)
        if (recentMessageGuids.size > MAX_RECENT_GUIDS) {
            recentMessageGuids.remove(recentMessageGuids.first())
        }
        if (isDuplicate) return

        // Reactions (tapbacks) don't change the chat preview or notify
        if (message.associatedMessageType != null) return

        val existing = chatDao.getChatByGuid(chatGuid)
        if (existing != null) {
            chatDao.upsertChat(existing.withLastMessage(message))
        } else {
            chatRepository.refreshChats()
        }

        if (!message.isFromMe) {
            val senderName = message.handle?.address
                ?.let { contactRepository.getDisplayName(it) }
                ?: "Unknown"
            val preview = message.text?.ifBlank { null }
                ?: attachmentLabel(message.attachments?.firstOrNull { it.hideAttachment != true }?.mimeType)
                ?: "New message"
            NotificationHelper.showNewMessageNotification(context, senderName, preview, chatGuid)
            NotificationHelper.vibrateIfEnabled(context)
        }
    }
}
