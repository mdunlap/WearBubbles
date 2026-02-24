package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.BlueBubblesApi
import com.wearbubbles.api.dto.ChatDto
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.entities.ChatEntity
import com.wearbubbles.socket.SocketEvent
import com.wearbubbles.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ChatRepository(
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
                            chatDao.upsertChat(
                                existing.copy(
                                    lastMessageText = event.message.text,
                                    lastMessageDate = event.message.dateCreated,
                                    lastMessageIsFromMe = event.message.isFromMe,
                                    hasUnreadMessage = !event.message.isFromMe
                                )
                            )
                        } else {
                            // New chat we haven't seen — refresh from API
                            refreshChats()
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

    suspend fun getDisplayName(chat: ChatEntity): String {
        if (!chat.displayName.isNullOrBlank()) return chat.displayName
        val addresses = chat.participantAddresses?.split(",")?.map { it.trim() } ?: emptyList()
        return if (addresses.isNotEmpty()) {
            contactRepository.getDisplayNameForAddresses(addresses)
        } else {
            chat.chatIdentifier ?: chat.guid
        }
    }

    private fun ChatDto.toEntity(): ChatEntity {
        val addresses = participants?.joinToString(",") { it.address } ?: ""
        return ChatEntity(
            guid = guid,
            chatIdentifier = chatIdentifier,
            displayName = displayName,
            participantAddresses = addresses,
            lastMessageText = lastMessage?.text,
            lastMessageDate = lastMessage?.dateCreated,
            lastMessageIsFromMe = lastMessage?.isFromMe,
            hasUnreadMessage = hasUnreadMessage ?: false
        )
    }
}
