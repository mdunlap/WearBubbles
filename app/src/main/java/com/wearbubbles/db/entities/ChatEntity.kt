package com.wearbubbles.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val guid: String,
    val chatIdentifier: String?,
    val displayName: String?,
    val participantAddresses: String?, // comma-separated
    val lastMessageText: String?,
    val lastMessageDate: Long?,
    val lastMessageIsFromMe: Boolean?,
    val hasUnreadMessage: Boolean = false
)
