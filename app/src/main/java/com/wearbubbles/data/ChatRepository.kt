package com.wearbubbles.data

import android.content.Context
import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.api.dto.ChatDto
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.entities.ChatEntity
import com.wearbubbles.notifications.NotificationHelper
import com.wearbubbles.socket.SocketEvent
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatRepository(
    private val context: Context,
    private val api: BlueBubblesApi,
    private val password: String,
    private val chatDao: ChatDao,
    private val socketManager: SocketManager,
    private val contactRepository: ContactRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ChatRepository"
        private const val PAGE_SIZE = 15
    }

    val chats: Flow<List<ChatEntity>> = chatDao.getAllChats()

    private var totalLoaded = 0
    private var hasMore = true

    init {
        scope.launch(Dispatchers.IO) {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.NewMessage -> {
                        val chatGuid = event.message.chats?.firstOrNull()?.guid ?: return@collect
                        val existing = chatDao.getChatByGuid(chatGuid)
                        if (existing != null) {
                            val imageAttachment = event.message.attachments?.firstOrNull { att ->
                                att.hideAttachment != true &&
                                att.mimeType != null &&
                                att.guid != null &&
                                att.mimeType.startsWith("image/")
                            }
                            chatDao.upsertChat(
                                existing.copy(
                                    lastMessageText = event.message.text
                                        ?: if (event.message.attachments?.isNotEmpty() == true) "Attachment" else null,
                                    lastMessageDate = event.message.dateCreated,
                                    lastMessageIsFromMe = event.message.isFromMe,
                                    hasUnreadMessage = !event.message.isFromMe,
                                    lastMessageAttachmentGuid = imageAttachment?.guid ?: existing.lastMessageAttachmentGuid,
                                    lastMessageAttachmentMimeType = imageAttachment?.mimeType ?: existing.lastMessageAttachmentMimeType
                                )
                            )
                        } else {
                            refreshChats()
                        }

                        if (!event.message.isFromMe) {
                            val address = event.message.handle?.address
                            val senderName = if (address != null) {
                                contactRepository.getDisplayNameSync(address)
                            } else {
                                "Unknown"
                            }
                            val messagePreview = event.message.text
                                ?: if (event.message.attachments?.isNotEmpty() == true) "Attachment" else "New message"
                            NotificationHelper.showNewMessageNotification(
                                context, senderName, messagePreview, chatGuid
                            )
                            NotificationHelper.vibrateIfEnabled(context)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun refreshChats() {
        try {
            val response = api.getChats(
                password = password,
                body = ChatQueryRequest(limit = PAGE_SIZE, offset = 0)
            )
            val entities = response.data.map { it.toEntity() }
            chatDao.upsertChats(entities)
            totalLoaded = entities.size
            hasMore = entities.size >= PAGE_SIZE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh chats", e)
        }
    }

    suspend fun loadMore(): Boolean {
        if (!hasMore) return false
        try {
            val response = api.getChats(
                password = password,
                body = ChatQueryRequest(limit = PAGE_SIZE, offset = totalLoaded)
            )
            val entities = response.data.map { it.toEntity() }
            if (entities.isNotEmpty()) {
                chatDao.upsertChats(entities)
                totalLoaded += entities.size
            }
            hasMore = entities.size >= PAGE_SIZE
            return entities.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load more chats", e)
            return false
        }
    }

    fun hasMoreChats(): Boolean = hasMore

    suspend fun markChatRead(chatGuid: String) {
        try {
            chatDao.markRead(chatGuid)
            api.markChatRead(chatGuid, password)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark chat as read", e)
        }
    }

    // Non-suspend: uses in-memory contact cache only — no DB round-trips
    fun getDisplayNameSync(chat: ChatEntity): String {
        if (!chat.displayName.isNullOrBlank()) return chat.displayName
        val addresses = chat.participantAddresses?.split(",")?.map { it.trim() } ?: emptyList()
        return if (addresses.isNotEmpty()) {
            contactRepository.getDisplayNameForAddressesSync(addresses)
        } else {
            chat.chatIdentifier ?: chat.guid
        }
    }

    private fun ChatDto.toEntity(): ChatEntity {
        val addresses = participants?.joinToString(",") { it.address } ?: ""
        val imageAttachment = lastMessage?.attachments?.firstOrNull { att ->
            att.hideAttachment != true &&
            att.mimeType != null &&
            att.guid != null &&
            att.mimeType.startsWith("image/")
        }
        return ChatEntity(
            guid = guid,
            chatIdentifier = chatIdentifier,
            displayName = displayName,
            participantAddresses = addresses,
            lastMessageText = lastMessage?.text
                ?: if (lastMessage?.attachments?.isNotEmpty() == true) "Attachment" else null,
            lastMessageDate = lastMessage?.dateCreated,
            lastMessageIsFromMe = lastMessage?.isFromMe,
            hasUnreadMessage = hasUnreadMessage ?: false,
            lastMessageAttachmentGuid = imageAttachment?.guid,
            lastMessageAttachmentMimeType = imageAttachment?.mimeType
        )
    }
}
