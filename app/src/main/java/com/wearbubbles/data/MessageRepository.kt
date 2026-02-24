package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.api.dto.MessageDto
import com.wearbubbles.api.dto.SendMessageRequest
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
                chatGuid = chatGuid,
                password = password,
                limit = 100
            )
            val entities = response.data.map { it.toEntity(chatGuid) }
            messageDao.upsertMessages(entities)
            messageDao.pruneOldMessages(chatGuid)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh messages for $chatGuid", e)
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
            // Replace temp message with real one if server returned it
            if (response.data != null) {
                messageDao.deleteMessage(tempGuid)
                messageDao.upsertMessage(response.data.toEntity(chatGuid))
            }
            // For apple-script method, response.status 200 means success even without data
            response.status == 200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            // Remove optimistic message on failure
            messageDao.deleteMessage(tempGuid)
            false
        }
    }

    suspend fun getLatestMessageDate(chatGuid: String): Long? {
        return messageDao.getLatestMessage(chatGuid)?.dateCreated
    }

    private fun MessageDto.toEntity(chatGuid: String): MessageEntity {
        return MessageEntity(
            guid = guid,
            chatGuid = chatGuid,
            text = text,
            isFromMe = isFromMe,
            dateCreated = dateCreated ?: System.currentTimeMillis(),
            handleAddress = handle?.address
        )
    }
}
