package com.wearbubbles.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerSessionTest {

    @Test
    fun `attachment url encodes special characters in the password`() {
        assertEquals(
            "https://host/api/v1/attachment/ABC/download?password=p%26ss%2Bw%3Drd+%23",
            attachmentUrl("https://host/", "ABC", "p&ss+w=rd #")
        )
    }

    @Test
    fun `attachment url keeps a server path prefix`() {
        assertEquals(
            "https://host/bb/api/v1/attachment/ABC/download?password=pw",
            attachmentUrl("https://host/bb", "ABC", "pw")
        )
    }
}
