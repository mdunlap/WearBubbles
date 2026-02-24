package com.wearbubbles.db

import androidx.room.*
import com.wearbubbles.db.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY lastMessageDate DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE guid = :guid")
    suspend fun getChatByGuid(guid: String): ChatEntity?

    @Upsert
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Upsert
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET hasUnreadMessage = 0 WHERE guid = :guid")
    suspend fun markRead(guid: String)

    @Query("DELETE FROM chats WHERE guid NOT IN (SELECT guid FROM chats ORDER BY lastMessageDate DESC LIMIT 50)")
    suspend fun pruneOldChats()
}
