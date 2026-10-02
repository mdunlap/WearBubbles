package com.wearbubbles.data

import android.util.Log
import com.wearbubbles.api.dto.ChatQueryRequest
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

/**
 * App-wide chat list. Credentials are read per call (see [currentSession]) so a
 * sign-out or server change never leaves this talking to the old server.
 * Live socket updates are applied by [IncomingMessageHandler].
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val settings: SettingsDataStore,
    private val contactRepository: ContactRepository
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    val chats: Flow<List<ChatEntity>> = chatDao.getVisibleChats()

    suspend fun refreshChats() {
        try {
            val session = settings.currentSession() ?: return
            val response = session.api.getChats(
                password = session.password,
                body = ChatQueryRequest(limit = 1000, offset = 0)
            )
            chatDao.upsertFromServer(response.data.map { it.toEntity() })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh chats", e)
        }
    }

    /** Removes the chat from the list until a newer message arrives in it. */
    suspend fun hideChat(chatGuid: String) {
        chatDao.hideChat(chatGuid)
    }

    // Non-suspend: uses in-memory contact cache only — no DB round-trips
    fun getDisplayNameSync(chat: ChatEntity): String {
        if (!chat.displayName.isNullOrBlank()) return chat.displayName
        val addresses = chat.participantAddresses?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        return if (addresses.isNotEmpty()) {
            contactRepository.getDisplayNameForAddressesSync(addresses)
        } else {
            chat.chatIdentifier ?: chat.guid
        }
    }
}
