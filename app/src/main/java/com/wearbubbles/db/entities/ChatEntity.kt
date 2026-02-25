package com.wearbubbles.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [Index("lastMessageDate")]
)
data class ChatEntity(
    @PrimaryKey
    val guid: String,
    val chatIdentifier: String?,
    val displayName: String?,
    val participantAddresses: String?, // comma-separated
    val lastMessageText: String?,
    val lastMessageDate: Long?,
    val lastMessageIsFromMe: Boolean?,
    val hasUnreadMessage: Boolean = false,
    val lastMessageAttachmentGuid: String? = null,
    val lastMessageAttachmentMimeType: String? = null
)
