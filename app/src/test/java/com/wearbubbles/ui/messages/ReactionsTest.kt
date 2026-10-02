package com.wearbubbles.ui.messages

import com.wearbubbles.db.entities.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionsTest {

    private fun reaction(
        guid: String,
        type: String,
        date: Long,
        target: String = "MSG1",
        fromMe: Boolean = false,
        sender: String? = "+15551234567"
    ) = MessageEntity(
        guid = guid,
        chatGuid = "chat",
        text = null,
        isFromMe = fromMe,
        dateCreated = date,
        handleAddress = if (fromMe) null else sender,
        associatedMessageGuid = "p:0/$target",
        associatedMessageType = type
    )

    @Test
    fun `love marks the target message`() {
        assertEquals(setOf("MSG1"), lovedMessageGuids(listOf(reaction("r1", "2000", 1))))
    }

    @Test
    fun `later un-love clears the heart`() {
        val reactions = listOf(reaction("r1", "2000", 1), reaction("r2", "3000", 2))
        assertEquals(emptySet<String>(), lovedMessageGuids(reactions))
    }

    @Test
    fun `named reaction types from newer servers are understood`() {
        val reactions = listOf(reaction("r1", "love", 1), reaction("r2", "-love", 2))
        assertEquals(emptySet<String>(), lovedMessageGuids(reactions))
    }

    @Test
    fun `another sender's love survives one person removing theirs`() {
        val reactions = listOf(
            reaction("r1", "2000", 1, sender = "+1111"),
            reaction("r2", "2000", 2, sender = "+2222"),
            reaction("r3", "3000", 3, sender = "+2222")
        )
        assertEquals(setOf("MSG1"), lovedMessageGuids(reactions))
    }

    @Test
    fun `other tapbacks are ignored`() {
        assertEquals(emptySet<String>(), lovedMessageGuids(listOf(reaction("r1", "2001", 1))))
    }
}
