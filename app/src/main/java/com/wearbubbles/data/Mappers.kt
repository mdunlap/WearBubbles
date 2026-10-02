package com.wearbubbles.data

import com.wearbubbles.api.dto.AttachmentDto
import com.wearbubbles.api.dto.ChatDto
import com.wearbubbles.api.dto.MessageDto
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.entities.ChatEntity
import com.wearbubbles.db.entities.MessageEntity

private fun AttachmentDto.isVisible() = hideAttachment != true && mimeType != null && guid != null

private fun List<AttachmentDto>?.firstVisible() = this?.firstOrNull { it.isVisible() }

private fun List<AttachmentDto>?.firstImage() =
    this?.firstOrNull { it.isVisible() && it.mimeType!!.startsWith("image/") }

/** Short label for a message that has an attachment but no text ("Photo", "Video", ...). */
fun attachmentLabel(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.startsWith("image/") -> "Photo"
    mimeType.startsWith("video/") -> "Video"
    mimeType.startsWith("audio/") -> "Audio message"
    else -> "Attachment"
}

fun ChatDto.toEntity(): ChatEntity = ChatEntity(
    guid = guid,
    chatIdentifier = chatIdentifier,
    displayName = displayName,
    participantAddresses = participants?.joinToString(",") { it.address } ?: "",
    // Null for attachment-only messages so the list can label them "Photo"/"Video"
    lastMessageText = lastMessage?.text?.ifBlank { null },
    lastMessageDate = lastMessage?.dateCreated,
    lastMessageIsFromMe = lastMessage?.isFromMe,
    hasUnreadMessage = hasUnreadMessage ?: false,
    lastMessageAttachmentGuid = lastMessage?.attachments.firstImage()?.guid,
    lastMessageAttachmentMimeType = lastMessage?.attachments.firstVisible()?.mimeType
)

/** Applies a new (non-reaction) message to an existing chat's preview. */
fun ChatEntity.withLastMessage(message: MessageDto): ChatEntity = copy(
    lastMessageText = message.text?.ifBlank { null },
    lastMessageDate = message.dateCreated,
    lastMessageIsFromMe = message.isFromMe,
    hasUnreadMessage = !message.isFromMe,
    lastMessageAttachmentGuid = message.attachments.firstImage()?.guid ?: lastMessageAttachmentGuid,
    lastMessageAttachmentMimeType = message.attachments.firstVisible()?.mimeType ?: lastMessageAttachmentMimeType
)

fun MessageDto.toEntity(chatGuid: String): MessageEntity {
    // Images get a guid so they render inline; other attachments only keep their
    // mime type so the bubble can show a "Video"/"Attachment" label instead of vanishing
    val image = attachments.firstImage()
    return MessageEntity(
        guid = guid,
        chatGuid = chatGuid,
        text = text,
        isFromMe = isFromMe,
        dateCreated = dateCreated ?: System.currentTimeMillis(),
        handleAddress = handle?.address,
        attachmentGuid = image?.guid,
        attachmentMimeType = image?.mimeType ?: attachments.firstVisible()?.mimeType,
        associatedMessageGuid = associatedMessageGuid,
        associatedMessageType = associatedMessageType
    )
}

/** Upserts chats fetched from the server without un-hiding chats the user removed. */
suspend fun ChatDao.upsertFromServer(chats: List<ChatEntity>) {
    val hidden = getHiddenChats().associate { it.guid to it.hiddenBefore }
    upsertChats(chats.map { chat -> chat.copy(hiddenBefore = hidden[chat.guid]) })
}
