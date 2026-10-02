package com.wearbubbles.db

import androidx.room.*
import com.wearbubbles.db.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("""
        SELECT * FROM chats
        WHERE hiddenBefore IS NULL OR COALESCE(lastMessageDate, 0) > hiddenBefore
        ORDER BY lastMessageDate DESC
    """)
    fun getVisibleChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE guid = :guid")
    suspend fun getChatByGuid(guid: String): ChatEntity?

    @Upsert
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET hasUnreadMessage = 0 WHERE guid = :guid")
    suspend fun markRead(guid: String)

    @Query("UPDATE chats SET hiddenBefore = COALESCE(lastMessageDate, 0) WHERE guid = :guid")
    suspend fun hideChat(guid: String)

    @Query("SELECT guid, hiddenBefore FROM chats WHERE hiddenBefore IS NOT NULL")
    suspend fun getHiddenChats(): List<HiddenChat>

    @Query("SELECT COUNT(*) FROM chats")
    suspend fun getChatCount(): Int

    @Query("SELECT COUNT(*) FROM chats WHERE hasUnreadMessage = 1")
    suspend fun getUnreadCount(): Int

    @Query("SELECT MAX(lastMessageDate) FROM chats")
    suspend fun getLastMessageTime(): Long?
}

data class HiddenChat(
    val guid: String,
    val hiddenBefore: Long
)
