package com.wearbubbles.api.dto

import com.google.gson.annotations.SerializedName

// -- Ping --

data class PingResponse(
    val status: Int,
    val message: String
)

// -- Chat Query --

data class ChatQueryRequest(
    val limit: Int = 50,
    val offset: Int = 0,
    val sort: String = "lastmessage",
    val with: List<String> = listOf("lastMessage", "sms")
)

data class ChatQueryResponse(
    val status: Int,
    val data: List<ChatDto>
)

data class ChatDto(
    val guid: String,
    @SerializedName("chatIdentifier")
    val chatIdentifier: String?,
    @SerializedName("displayName")
    val displayName: String?,
    val participants: List<HandleDto>?,
    @SerializedName("lastMessage")
    val lastMessage: MessageDto?,
    @SerializedName("hasUnreadMessage")
    val hasUnreadMessage: Boolean?
)

// -- Messages --

data class MessageQueryResponse(
    val status: Int,
    val data: List<MessageDto>
)

data class MessageDto(
    val guid: String,
    val text: String?,
    @SerializedName("isFromMe")
    val isFromMe: Boolean,
    @SerializedName("dateCreated")
    val dateCreated: Long?,
    @SerializedName("dateDelivered")
    val dateDelivered: Long?,
    @SerializedName("dateRead")
    val dateRead: Long?,
    val handle: HandleDto?,
    val chats: List<ChatDto>?,
    val attachments: List<AttachmentDto>?,
    @SerializedName("associatedMessageGuid")
    val associatedMessageGuid: String?,
    @SerializedName("associatedMessageType")
    val associatedMessageType: String?
)

data class AttachmentDto(
    val guid: String?,
    @SerializedName("mimeType")
    val mimeType: String?,
    @SerializedName("transferName")
    val transferName: String?,
    @SerializedName("totalBytes")
    val totalBytes: Long?,
    @SerializedName("transferState")
    val transferState: Int?,
    @SerializedName("hideAttachment")
    val hideAttachment: Boolean?,
    @SerializedName("isSticker")
    val isSticker: Boolean?
)

data class HandleDto(
    val address: String,
    @SerializedName("originalROWID")
    val originalRowId: Int?
)

// -- Send Message --

data class SendMessageRequest(
    val chatGuid: String,
    val message: String,
    val method: String = "apple-script",
    val tempGuid: String
)

data class SendMessageResponse(
    val status: Int,
    val message: String?,
    val data: MessageDto?
)

// -- Contacts --

data class ContactResponse(
    val status: Int,
    val data: List<ContactDto>
)

data class ContactDto(
    val id: String?,
    @SerializedName("firstName")
    val firstName: String?,
    @SerializedName("lastName")
    val lastName: String?,
    @SerializedName("displayName")
    val displayName: String?,
    val phoneNumbers: List<PhoneNumberDto>?,
    val emails: List<EmailDto>?
)

data class PhoneNumberDto(
    val address: String?
)

data class EmailDto(
    val address: String?
)

// -- Basic --

data class BasicResponse(
    val status: Int,
    val message: String?
)
