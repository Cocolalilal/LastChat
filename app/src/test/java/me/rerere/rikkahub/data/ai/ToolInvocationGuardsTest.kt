package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolInvocationGuardsTest {
    @Test
    fun roleplayNarrationDoesNotAuthorizeSearchTools() {
        val messages = listOf(
            UIMessage.user("I bury my face deeper against her and keep crying."),
            UIMessage.assistant("*My hands, which were hovering awkwardly, suddenly find their own will and wrap around you.*"),
        )

        assertFalse(hasExplicitWebSearchIntent(messages))
        assertFalse(hasExplicitMemorySearchIntent(messages))
    }

    @Test
    fun latestUserMessageMustAuthorizeWebSearch() {
        val messages = listOf(
            UIMessage.user("Please look that up and tell me the latest news."),
            UIMessage.assistant("I'll check.")
        )

        assertTrue(hasExplicitWebSearchIntent(messages))
    }

    @Test
    fun latestUserMessageMustAuthorizeMemorySearch() {
        val messages = listOf(
            UIMessage.user("Do you remember what I told you last time about Lisbon?")
        )

        assertTrue(hasExplicitMemorySearchIntent(messages))
    }
}
