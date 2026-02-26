package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.api.dto.MessageDto
import com.wearbubbles.api.dto.MessageQueryRequest
import com.wearbubbles.api.dto.ReactRequest
import com.wearbubbles.api.dto.SendMessageRequest
import com.wearbubbles.api.dto.WhereClause
import com.wearbubbles.db.MessageDao
import com.wearbubbles.db.entities.MessageEntity
import com.wearbubbles.socket.SocketEvent
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class MessageRepository(
    private val api: BlueBubblesApi,
    private val password: String,
    private val messageDao: MessageDao,
    private val socketManager: SocketManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    private var hasMore = true
    fun hasMoreMessages() = hasMore

    init {
        scope.launch(Dispatchers.IO) {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> {
                        val chatGuid = event.message.chats?.firstOrNull()?.guid ?: return@collect
                        messageDao.upsertMessage(event.message.toEntity(chatGuid))
                    }
                    is SocketEvent.UpdatedMessage -> {
                        val chatGuid = event.message.chats?.firstOrNull()?.guid ?: return@collect
                        messageDao.upsertMessage(event.message.toEntity(chatGuid))
                    }
                    else -> {}
                }
            }
        }
    }

    fun getMessages(chatGuid: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForChat(chatGuid)
    }

    suspend fun refreshMessages(chatGuid: String) {
        try {
            val response = api.getMessages(
                password = password,
                body = MessageQueryRequest(
                    limit = 15,
                    where = listOf(chatWhereClause(chatGuid))
                )
            )
            hasMore = response.data.size >= 15
            val entities = response.data.map { it.toEntity(chatGuid) }
            messageDao.upsertMessages(preserveAttachments(chatGuid, entities))
            messageDao.pruneOldMessages(chatGuid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh messages for $chatGuid", e)
        }
    }

    suspend fun loadMoreMessages(chatGuid: String) {
        try {
            val currentCount = messageDao.getMessageCount(chatGuid)
            val response = api.getMessages(
                password = password,
                body = MessageQueryRequest(
                    limit = 15,
                    offset = currentCount,
                    where = listOf(chatWhereClause(chatGuid))
                )
            )
            hasMore = response.data.size >= 15
            val entities = response.data.map { it.toEntity(chatGuid) }
            messageDao.upsertMessages(preserveAttachments(chatGuid, entities))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load more messages for $chatGuid", e)
        }
    }

    private fun chatWhereClause(chatGuid: String) = WhereClause(
        statement = "chat.guid = :val",
        args = mapOf("val" to chatGuid)
    )

    /** If API response lacks attachment info for a message we already have it for, keep existing */
    private suspend fun preserveAttachments(chatGuid: String, entities: List<MessageEntity>): List<MessageEntity> {
        val existing = messageDao.getAttachmentInfo(chatGuid).associateBy { it.guid }
        return entities.map { entity ->
            val cached = existing[entity.guid]
            if (entity.attachmentGuid == null && cached != null) {
                entity.copy(
                    attachmentGuid = cached.attachmentGuid,
                    attachmentMimeType = cached.attachmentMimeType
                )
            } else {
                entity
            }
        }
    }

    suspend fun sendMessage(chatGuid: String, text: String): Boolean {
        val tempGuid = "temp_${UUID.randomUUID()}"

        // Optimistic insert
        val tempMessage = MessageEntity(
            guid = tempGuid,
            chatGuid = chatGuid,
            text = text,
            isFromMe = true,
            dateCreated = System.currentTimeMillis(),
            handleAddress = null,
            isTemporary = true
        )
        messageDao.upsertMessage(tempMessage)

        return try {
            Log.d(TAG, "Sending message to chatGuid=$chatGuid text=$text")
            val response = api.sendMessage(
                password = password,
                body = SendMessageRequest(
                    chatGuid = chatGuid,
                    message = text,
                    tempGuid = tempGuid
                )
            )
            Log.d(TAG, "Send response: status=${response.status} message=${response.message}")
            if (response.data != null) {
                messageDao.deleteMessage(tempGuid)
                messageDao.upsertMessage(response.data.toEntity(chatGuid))
            }
            response.status == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            messageDao.upsertMessage(tempMessage.copy(sendFailed = true))
            false
        }
    }

    suspend fun retrySendMessage(message: MessageEntity): Boolean {
        messageDao.upsertMessage(message.copy(sendFailed = false))

        return try {
            val response = api.sendMessage(
                password = password,
                body = SendMessageRequest(
                    chatGuid = message.chatGuid,
                    message = message.text ?: "",
                    tempGuid = message.guid
                )
            )
            if (response.data != null) {
                messageDao.deleteMessage(message.guid)
                messageDao.upsertMessage(response.data.toEntity(message.chatGuid))
            }
            response.status == 200
        } catch (e: Exception) {
            Log.e(TAG, "Retry failed", e)
            messageDao.upsertMessage(message.copy(sendFailed = true))
            false
        }
    }

    suspend fun reactToMessage(chatGuid: String, messageGuid: String): Boolean {
        return try {
            val response = api.reactToMessage(
                password = password,
                body = ReactRequest(chatGuid = chatGuid, selectedMessageGuid = messageGuid)
            )
            response.status == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to react to message", e)
            false
        }
    }

    private fun MessageDto.toEntity(chatGuid: String): MessageEntity {
        // Find the first visible image/gif attachment
        val imageAttachment = attachments?.firstOrNull { att ->
            att.hideAttachment != true &&
            att.mimeType != null &&
            att.guid != null &&
            att.mimeType.startsWith("image/")
        }
        return MessageEntity(
            guid = guid,
            chatGuid = chatGuid,
            text = text,
            isFromMe = isFromMe,
            dateCreated = dateCreated ?: System.currentTimeMillis(),
            handleAddress = handle?.address,
            attachmentGuid = imageAttachment?.guid,
            attachmentMimeType = imageAttachment?.mimeType,
            associatedMessageGuid = associatedMessageGuid,
            associatedMessageType = associatedMessageType
        )
    }
}
