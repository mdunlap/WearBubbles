package com.wearbubbles.data

import com.wearbubbles.api.dto.AttachmentDto
import com.wearbubbles.api.dto.MessageDto
import com.wearbubbles.db.ChatDao
import com.wearbubbles.db.HiddenChat
import com.wearbubbles.db.entities.ChatEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    private class FakeChatDao : ChatDao {
        val rows = mutableMapOf<String, ChatEntity>()
        override fun getVisibleChats(): Flow<List<ChatEntity>> = emptyFlow()
        override suspend fun getChatByGuid(guid: String) = rows[guid]
        override suspend fun upsertChats(chats: List<ChatEntity>) = chats.forEach { rows[it.guid] = it }
        override suspend fun upsertChat(chat: ChatEntity) { rows[chat.guid] = chat }
        override suspend fun markRead(guid: String) {}
        override suspend fun hideChat(guid: String) {}
        override suspend fun getHiddenChats() =
            rows.values.mapNotNull { c -> c.hiddenBefore?.let { HiddenChat(c.guid, it) } }
        override suspend fun getChatCount() = rows.size
        override suspend fun getUnreadCount() = 0
        override suspend fun getLastMessageTime(): Long? = null
    }

    private fun chat(guid: String, date: Long, hiddenBefore: Long? = null) = ChatEntity(
        guid = guid, chatIdentifier = null, displayName = null, participantAddresses = "",
        lastMessageText = "hi", lastMessageDate = date, lastMessageIsFromMe = false,
        hiddenBefore = hiddenBefore
    )

    private fun message(text: String?, vararg mimeTypes: String) = MessageDto(
        guid = "m", text = text, isFromMe = false, dateCreated = 1L, dateDelivered = null,
        dateRead = null, handle = null, chats = null,
        attachments = mimeTypes.mapIndexed { i, mime ->
            AttachmentDto("a$i", mime, null, null, null, false, false)
        },
        associatedMessageGuid = null, associatedMessageType = null
    )

    @Test
    fun `server refresh keeps a removed chat hidden`() = runBlocking {
        val dao = FakeChatDao()
        dao.upsertChat(chat("c1", date = 100, hiddenBefore = 100))

        dao.upsertFromServer(listOf(chat("c1", date = 100), chat("c2", date = 50)))

        assertEquals(100L, dao.rows["c1"]!!.hiddenBefore)
        assertNull(dao.rows["c2"]!!.hiddenBefore)
    }

    @Test
    fun `video-only message keeps its type for labeling but no inline image`() {
        val entity = message(null, "video/mp4").toEntity("chat")
        assertNull(entity.attachmentGuid)
        assertEquals("video/mp4", entity.attachmentMimeType)
        assertEquals("Video", attachmentLabel(entity.attachmentMimeType))
    }

    @Test
    fun `image attachment is preferred for the inline preview`() {
        val entity = message("look", "video/mp4", "image/jpeg").toEntity("chat")
        assertEquals("a1", entity.attachmentGuid)
        assertEquals("image/jpeg", entity.attachmentMimeType)
    }
}
