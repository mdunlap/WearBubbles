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
            ORDER BY dateCreated DESC LIMIT 500
        )
    """)
    suspend fun pruneOldMessages(chatGuid: String)

    @Query("SELECT * FROM messages WHERE chatGuid = :chatGuid ORDER BY dateCreated DESC LIMIT 1")
    suspend fun getLatestMessage(chatGuid: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatGuid = :chatGuid")
    suspend fun getMessageCount(chatGuid: String): Int

    @Query("SELECT guid, attachmentGuid, attachmentMimeType FROM messages WHERE chatGuid = :chatGuid AND attachmentGuid IS NOT NULL")
    suspend fun getAttachmentInfo(chatGuid: String): List<AttachmentInfo>
}

data class AttachmentInfo(
    val guid: String,
    val attachmentGuid: String,
    val attachmentMimeType: String?
)
