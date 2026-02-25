package com.wearbubbles.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index("chatGuid"), Index("dateCreated")]
)
data class MessageEntity(
    @PrimaryKey
    val guid: String,
    val chatGuid: String,
    val text: String?,
    val isFromMe: Boolean,
    val dateCreated: Long,
    val handleAddress: String?,
    val isTemporary: Boolean = false,
    val attachmentGuid: String? = null,
    val attachmentMimeType: String? = null,
    val associatedMessageGuid: String? = null,
    val associatedMessageType: String? = null
)
