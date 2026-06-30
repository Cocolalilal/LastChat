package me.rerere.ai.util

import kotlin.test.Test
import kotlin.test.assertEquals
import me.rerere.ai.core.MessageRole

class MessageTemplateContextTest {
    @Test
    fun buildCreatesStablePebbleCompatibleKeys() {
        val context = MessageTemplateContext.build(
            message = "hello",
            role = MessageRole.USER,
            time = "12:34:56 PM",
            date = "Jun 17, 2026"
        )

        assertEquals(
            mapOf(
                "message" to "hello",
                "role" to "user",
                "time" to "12:34:56 PM",
                "date" to "Jun 17, 2026",
            ),
            context.asMap()
        )
    }
}
