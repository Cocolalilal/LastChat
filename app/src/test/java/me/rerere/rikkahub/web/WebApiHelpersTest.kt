package me.rerere.rikkahub.web

import io.ktor.http.HttpStatusCode
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun issuedWebAuthToken_validatesAgainstProtectedRoutes() {
        val settings = Settings(
            webServerJwtEnabled = true,
            webServerAccessPassword = "secret",
        )

        val response = issueWebAuthToken(
            settings = settings,
            request = WebAuthTokenRequest(password = "secret"),
        )

        assertNull(validateWebAccessToken(settings, response.token))
        assertEquals(
            HttpStatusCode.Unauthorized,
            validateWebAccessToken(settings.copy(webServerAccessPassword = "changed"), response.token),
        )
        assertEquals(HttpStatusCode.Unauthorized, validateWebAccessToken(settings, null))
    }

    @Test
    fun publicWebApiPaths_doNotRequireExistingToken() {
        assertTrue(isPublicWebApiPath("/api/auth/token"))
        assertTrue(isPublicWebApiPath("/api/bootstrap"))
        assertTrue(isPublicWebApiPath("/api/ai-icon"))
        assertTrue(!isPublicWebApiPath("/api/conversations/paged"))
        assertTrue(!isPublicWebApiPath("/api/settings/stream"))
    }

    @Test
    fun resolveAiIconAssetPath_acceptsCatalogPathStrings() {
        assertEquals(
            "openai.svg",
            resolveAiIconAssetPath(name = "ignored", icon = "icons/openai.svg"),
        )
        assertEquals(
            "anthropic.svg",
            resolveAiIconAssetPath(
                name = "ignored",
                icon = "https://raw.githubusercontent.com/RikkaApps/LastChat/main/catalog/icons/anthropic.svg",
            ),
        )
    }

    @Test
    fun resolveAiIconAssetPath_usesKnownNamesAndProviderSlugs() {
        assertEquals("gemini.svg", resolveAiIconAssetPath(name = "Google Gemini"))
        assertEquals("openrouter.svg", resolveAiIconAssetPath(name = "Model Router", providerSlug = "openrouter"))
    }

    @Test
    fun resolveAiIconAssetPath_fallsBackForUnknownNames() {
        assertNull(resolveAiIconAssetPath(name = "Mystery Provider"))
        assertNull(resolveAiIconAssetPath(name = "ignored", icon = "../openai.svg"))
    }

    @Test
    fun toLobeHubIconUrl_buildsThemeAwareProviderUrl() {
        assertEquals(
            "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/dark/openai.png",
            "OpenAI".toLobeHubIconUrl("dark"),
        )
        assertEquals(
            "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/light/meta.png",
            "Meta Llama".toLobeHubIconUrl("light"),
        )
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
