package me.rerere.rikkahub.web

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class WebApiHelpersTest {

    @Test
    fun issueWebAuthToken_acceptsCorrectPassword() {
        val response = issueWebAuthToken(
            settings = Settings(
                webServerJwtEnabled = true,
                webServerAccessPassword = "secret",
            ),
            request = WebAuthTokenRequest(password = "secret"),
        )

        assertTrue(response.token.isNotBlank())
        assertTrue(response.expiresAt > System.currentTimeMillis())
    }

    @Test
    fun issueWebAuthToken_rejectsWrongAndBlankPasswords() {
        assertThrows(UnauthorizedException::class.java) {
            issueWebAuthToken(
                settings = Settings(
                    webServerJwtEnabled = true,
                    webServerAccessPassword = "secret",
                ),
                request = WebAuthTokenRequest(password = "wrong"),
            )
        }

        assertThrows(BadRequestException::class.java) {
            issueWebAuthToken(
                settings = Settings(
                    webServerJwtEnabled = true,
                    webServerAccessPassword = "",
                ),
                request = WebAuthTokenRequest(password = "secret"),
            )
        }
    }

    @Test
    fun buildWebBootstrap_returnsAssistantsCurrentAssistantAndConversations() {
        val settings = Settings()
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            assistantId = settings.assistantId,
        ).copy(title = "Web chat")

        val bootstrap = buildWebBootstrap(
            settings = settings,
            assistants = listOf(
                WebAssistantDto(
                    id = settings.assistantId.toString(),
                    name = "Generical",
                )
            ),
            conversations = listOf(conversation),
            generationJobs = mapOf(conversation.id to Unit),
        )

        assertEquals(settings.assistantId.toString(), bootstrap.assistantId)
        assertTrue(bootstrap.assistants.any { it.id == settings.assistantId.toString() })
        assertEquals(1, bootstrap.conversations.size)
        assertEquals(conversation.id.toString(), bootstrap.conversations.single().id)
        assertTrue(bootstrap.conversations.single().isGenerating)
    }
}
