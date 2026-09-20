package me.rerere.rikkahub.data.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableSharePayloadTest {
    @Test
    fun emptyPayloadHasNoContent() {
        assertFalse(PortableSharePayload().hasContent())
    }

    @Test
    fun promptTextJoinsSubjectAndBody() {
        val payload = PortableSharePayload(text = "Look at this", subject = "Shared page")
        assertTrue(payload.hasContent())
        assertEquals("Shared page\n\nLook at this", payload.promptText())
    }
}
