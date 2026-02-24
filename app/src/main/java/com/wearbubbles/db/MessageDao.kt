package com.wearbubbles.db

import androidx.room.*
import com.wearbubbles.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatGuid = :chatGuid ORDER BY dateCreated ASC")
    fun getMessagesForChat(chatGuid: String): Flow<List<MessageEntity>>

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE guid = :guid")
    suspend fun deleteMessage(guid: String)

    @Query("""
        DELETE FROM messages WHERE chatGuid = :chatGuid
        AND guid NOT IN (
            SELECT guid FROM messages WHERE chatGuid = :chatGuid
            ORDER BY dateCreated DESC LIMIT 100
        )
    """)
    suspend fun pruneOldMessages(chatGuid: String)

    @Query("SELECT * FROM messages WHERE chatGuid = :chatGuid ORDER BY dateCreated DESC LIMIT 1")
    suspend fun getLatestMessage(chatGuid: String): MessageEntity?
}
