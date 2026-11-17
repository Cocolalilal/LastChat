package me.rerere.rikkahub.data.ai

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.promptPlaceholderResolver
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.getInitialMessageNodes
import me.rerere.rikkahub.data.model.resolveTemplatePlaceholders
import me.rerere.rikkahub.service.defaultChatInputTransformers
import me.rerere.rikkahub.service.defaultChatOutputTransformers
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform.getKoin

/** Runs inside the installed app on ART, including the minified stable release in CI. */
@RunWith(AndroidJUnit4::class)
class PlaceholderRuntimeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val display = DisplaySetting(userNickname = "Alex")

    @Test fun helperInitializesOnAndroidAndPreservesLiteralReplacementValues() {
        assertEquals("Mira / Alex / {{unknown}}", "{{CHAR}} / {user} / {{unknown}}".resolveTemplatePlaceholders {
            when (it) { "char" -> "Mira"; "user" -> "Alex"; else -> null }
        })
        assertEquals("{{user}} / {char}", "{char} / {{user}}".resolveTemplatePlaceholders {
            when (it) { "char" -> "{{user}}"; "user" -> "{char}"; else -> null }
        })
        repeat(3) { assertEquals("plain chat text", "plain chat text".resolveTemplatePlaceholders { error("Unused") }) }
    }

    @Test fun greetingCreationAcrossCharactersNeverRequestsLocationForDateOrTimeTags() {
        var locationPermissions = 0
        var locationLookups = 0
        val guardedContext = object : ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.ACCESS_COARSE_LOCATION) {
                    locationPermissions++
                    PackageManager.PERMISSION_GRANTED
                } else super.checkPermission(permission, pid, uid)
            override fun getSystemService(name: String): Any? {
                if (name == Context.LOCATION_SERVICE) {
                    locationLookups++
                    throw IllegalArgumentException("No location provider")
                }
                return super.getSystemService(name)
            }
        }
        listOf("Mira", "Nova", "Generical").forEach { name ->
            val assistant = Assistant(name = name,
                alternateGreetings = listOf("Hello {{user}}, I am {{char}}. {{cur_date}} {{cur_time}}"))
            val resolve = promptPlaceholderResolver(guardedContext, Settings(displaySetting = display), assistant)
            val greeting = assistant.getInitialMessageNodes(resolve).single().currentMessage.toContentText()
            assertTrue(greeting.startsWith("Hello Alex, I am $name."))
            assertFalse(greeting.contains("{{cur_"))
        }
        assertEquals("Date/time tags must not even consult location permission", 0, locationPermissions)
        assertEquals("Date/time tags must not access location providers", 0, locationLookups)
    }

    @Test fun unavailableLocationServiceCannotCrashCharacterCreation() {
        val guardedContext = object : ContextWrapper(context) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission == Manifest.permission.ACCESS_COARSE_LOCATION) PackageManager.PERMISSION_GRANTED
                else super.checkPermission(permission, pid, uid)
            override fun getSystemService(name: String): Any? {
                if (name == Context.LOCATION_SERVICE) throw SecurityException("Location changed while resolving")
                return super.getSystemService(name)
            }
        }
        val assistant = Assistant(name = "Mira", alternateGreetings = listOf("{{char}}: {{location}}"))
        val greeting = assistant.getInitialMessageNodes(
            promptPlaceholderResolver(guardedContext, Settings(displaySetting = display), assistant),
        ).single().currentMessage.toContentText()
        assertEquals("Mira: ${context.getString(R.string.placeholder_location_unavailable)}", greeting)
    }

    @Test fun promptAssemblyResolvesAllConfiguredPositionsWithoutRewritingHistory() = runBlocking {
        val handler = getKoin().get<GenerationHandler>()
        InjectionPosition.entries.forEach { position ->
            val book = Lorebook(name = "Facts", entries = listOf(LorebookEntry(name = "Fact",
                prompt = "Lore for {{user}}", activationType = LorebookActivationType.ALWAYS,
                injectionPosition = position)))
            val assistant = Assistant(name = "Mira", systemPrompt = "System for {{user}} by {{char}}",
                enableMemory = false, enableTimeAwareness = false, enabledLorebookIds = setOf(book.id))
            val skill = Skill(name = "write", instructions = "Skill for {{char}}", injectionPosition = position)
            val snippet = AdditionalContext(name = "Style", prompt = "Context for {{user}}", allAssistants = true,
                injectionPosition = position)
            val settings = Settings(init = false, displaySetting = display, assistants = listOf(assistant),
                skills = listOf(skill), lorebooks = listOf(book), additionalContexts = listOf(snippet))
            val user = UIMessage.user("User literal {{user}} / {char} / {{cur_date}}")
            val reply = UIMessage.assistant("Assistant literal {{user}} / {char}")
            val result = handler.buildMessages(assistant, settings, listOf(user, reply, UIMessage.user("Continue")),
                Model(abilities = emptyList()), emptyList(), emptyList(), -1,
                turnScopedEnabledModeIds = setOf(skill.id), contextSummary = "Summary literal {{user}}")
            val text = result.messages.joinToString("\n") { it.toContentText() }
            assertTrue("System: $position", text.contains("System for Alex by Mira"))
            assertTrue("Skill: $position", text.contains("Skill for Mira"))
            assertTrue("Context: $position", text.contains("Context for Alex"))
            assertTrue("Lore: $position", text.contains("Lore for Alex"))
            assertTrue(text.contains("User literal {{user}} / {char} / {{cur_date}}"))
            assertTrue(text.contains("Assistant literal {{user}} / {char}"))
            assertTrue(text.contains("Summary literal {{user}}"))
        }
    }

    @Test fun realProviderProtocolsCompleteAGenerationWithLiteralChatTags() = runBlocking {
        withTimeout(45_000) {
            val responses = listOf(
                ProviderSetting.OpenAI(apiKey = "device-smoke-key", baseUrl = "https://runtime-smoke.invalid/v1") to
                    """{"id":"test","object":"chat.completion","model":"runtime-smoke","choices":[{"index":0,"message":{"role":"assistant","content":"device smoke ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":3,"total_tokens":4}}""",
                ProviderSetting.Claude(apiKey = "device-smoke-key", baseUrl = "https://runtime-smoke.invalid/v1") to
                    """{"id":"test","type":"message","role":"assistant","model":"runtime-smoke","content":[{"type":"text","text":"device smoke ok"}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":3}}""",
                ProviderSetting.Google(apiKey = "device-smoke-key", baseUrl = "https://runtime-smoke.invalid/v1beta") to
                    """{"candidates":[{"content":{"role":"model","parts":[{"text":"device smoke ok"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":3,"totalTokenCount":4}}""",
            )
            responses.forEach { (provider, response) ->
                val streamResponse = when (provider) {
                    is ProviderSetting.OpenAI -> """{"id":"test","model":"runtime-smoke","choices":[{"index":0,"delta":{"role":"assistant","content":"device smoke ok"},"finish_reason":"stop"}]}"""
                    is ProviderSetting.Claude -> """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"device smoke ok"}}"""
                    else -> response
                }
                listOf(false, true).forEach { streaming ->
                    val http = RecordingHttpClient(response, streamResponse)
                    val koin = getKoin()
                    val handler = GenerationHandler(context, ProviderManager(http, koin.get(), koin.get()), JsonInstant,
                        koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
                    val model = Model(modelId = "runtime-smoke", abilities = emptyList())
                    val configuredProvider = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(models = listOf(model))
                        is ProviderSetting.Claude -> provider.copy(models = listOf(model))
                        is ProviderSetting.Google -> provider.copy(models = listOf(model))
                        else -> error("Unexpected smoke-test provider")
                    }
                    val assistant = Assistant(name = "Mira", systemPrompt = "{{char}} speaks to {{user}}",
                        streamOutput = streaming, enableMemory = false, enableTimeAwareness = false)
                    val settings = Settings(init = false, providers = listOf(configuredProvider), chatModelId = model.id,
                        assistants = listOf(assistant), assistantId = assistant.id, displaySetting = display)
                    val chunks = handler.generateText(settings = settings, model = model,
                        messages = listOf(UIMessage.user("User literal {{user}} / {char}")), assistant = assistant,
                        inputTransformers = defaultChatInputTransformers, outputTransformers = defaultChatOutputTransformers,
                        maxSteps = 1).toList()
                    assertEquals(provider.name, 1, http.requests.size)
                    val body = http.requests.single().body!!.decodeToString()
                    assertTrue(provider.name, body.contains("Mira speaks to Alex"))
                    assertTrue(provider.name, body.contains("User literal {{user}} / {char}"))
                    assertTrue(provider.name, chunks.filterIsInstance<GenerationChunk.Messages>().last().messages.any {
                        it.role == MessageRole.ASSISTANT && it.toContentText().contains("device smoke ok")
                    })
                }
            }
        }
    }

    private class RecordingHttpClient(
        private val response: String,
        private val streamResponse: String,
    ) : PlatformHttpClient {
        val requests = mutableListOf<PlatformHttpRequest>()
        override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
            requests += request
            return PlatformHttpResponse(200, body = response.encodeToByteArray())
        }
        override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = flow {
            requests += request
            emit(PlatformServerEvent.Open(statusCode = 200))
            emit(PlatformServerEvent.Event(event = "content_block_delta", data = streamResponse))
            emit(PlatformServerEvent.Closed)
        }
    }
}
