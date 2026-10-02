package com.wearbubbles.ui.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientsTest {

    @Test
    fun `formatted US number becomes E164`() {
        assertEquals("iMessage;-;+15551234567", recipientChatGuid("(555) 123-4567"))
    }

    @Test
    fun `eleven digit number with leading 1 gets a plus`() {
        assertEquals("iMessage;-;+15551234567", recipientChatGuid("1-555-123-4567"))
    }

    @Test
    fun `explicit international number keeps its country code`() {
        assertEquals("iMessage;-;+447911123456", recipientChatGuid(" +44 7911 123456 "))
    }

    @Test
    fun `email is trimmed and lowercased`() {
        assertEquals("iMessage;-;jane@example.com", recipientChatGuid("  Jane@Example.com "))
    }
}
