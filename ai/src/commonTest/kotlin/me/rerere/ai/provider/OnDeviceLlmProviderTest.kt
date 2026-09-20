package me.rerere.ai.provider

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.runtime.OnDeviceChatChunk
import me.rerere.common.runtime.OnDeviceLlmModel
import me.rerere.common.runtime.OnDeviceLlmRuntime
import me.rerere.common.runtime.UnavailableOnDeviceLlmRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnDeviceLlmProviderTest {
    @Test
    fun flattenFallbackUsesRuntimeGenerateChat() = runBlocking {
        val runtime = RecordingRuntime()
        val provider = OnDeviceLlmProvider(runtime)
        val chunk = provider.generateText(
            providerSetting = ProviderSetting.LiteRtLocal(),
            messages = listOf(
                UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("Hello"))),
            ),
            params = TextGenerationParams(model = Model(modelId = "local", displayName = "Local")),
        )
        assertEquals("user: Hello", runtime.lastPrompt)
        assertEquals(
            "pong",
            chunk.choices.first().message?.parts?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text },
        )
    }

    @Test
    fun streamFallbackEmitsDeltaFromRuntime() = runBlocking {
        val provider = OnDeviceLlmProvider(RecordingRuntime())
        val chunks = provider.streamText(
            providerSetting = ProviderSetting.LiteRtLocal(),
            messages = listOf(UIMessage.user("Hi")),
            params = TextGenerationParams(model = Model(modelId = "local", displayName = "Local")),
        ).toList()
        assertEquals(1, chunks.size)
        assertEquals("pong", chunks.first().choices.first().delta?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text })
        assertEquals("stop", chunks.first().choices.first().finishReason)
    }

    @Test
    fun unavailableRuntimeStaysOnSharedLoopAndIsHonest() = runBlocking {
        val provider = OnDeviceLlmProvider(UnavailableOnDeviceLlmRuntime())
        val failure = assertFailsWith<IllegalStateException> {
            provider.generateText(
                providerSetting = ProviderSetting.LiteRtLocal(),
                messages = listOf(UIMessage.user("Hi")),
                params = TextGenerationParams(model = Model(modelId = "local", displayName = "Local")),
            )
        }
        assertTrue(failure.message.orEmpty().contains("LiteRT-LM"))
    }

    @Test
    fun listModelsFallsBackToProviderSettingWhenRuntimeIsEmpty() = runBlocking {
        val models = listOf(Model(modelId = "catalog", displayName = "Catalog"))
        val listed = OnDeviceLlmProvider(UnavailableOnDeviceLlmRuntime()).listModels(
            ProviderSetting.LiteRtLocal(models = models),
        )
        assertEquals(listOf("catalog"), listed.map { it.modelId })
    }

    @Test
    fun losslessEngineBridgeIsPreferredOverFlatten() = runBlocking {
        val runtime = BridgedRuntime()
        val provider = OnDeviceLlmProvider(runtime)
        val chunk = provider.generateText(
            providerSetting = ProviderSetting.LiteRtLocal(),
            messages = listOf(UIMessage.user("keep tools")),
            params = TextGenerationParams(model = Model(modelId = "local", displayName = "Local")),
        )
        assertTrue(runtime.usedEngine)
        assertEquals(
            "engine",
            chunk.choices.first().message?.parts?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text },
        )
    }
}

private open class RecordingRuntime : OnDeviceLlmRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null
    var lastPrompt: String? = null

    override suspend fun listModels(): List<OnDeviceLlmModel> =
        listOf(OnDeviceLlmModel("local", "Local"))

    override suspend fun generateText(modelId: String, prompt: String): String {
        lastPrompt = prompt
        return "pong"
    }

    override suspend fun embed(modelId: String, text: String): List<Float>? = listOf(1f)
}

private class BridgedRuntime : RecordingRuntime(), OnDeviceLlmChatEngine {
    var usedEngine: Boolean = false

    override suspend fun generateAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): me.rerere.ai.ui.MessageChunk {
        usedEngine = true
        return OnDeviceChatChunk(text = "engine", finishReason = "stop", finished = true)
            .toMessageChunk(params.model.modelId)
    }

    override fun streamAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ) = throw UnsupportedOperationException("not used")

    override suspend fun embedAsProvider(
        providerSetting: ProviderSetting.LiteRtLocal,
        input: List<String>,
        model: Model,
    ): List<List<Float>> = emptyList()
}
