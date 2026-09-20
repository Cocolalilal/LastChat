package me.rerere.rikkahub.data.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

class AILoggingManagerTest {
    @Test
    fun generationLogKeepsLastTenAndFormatsDeveloperLine() {
        val manager = AILoggingManager()
        val provider = ProviderSetting.OpenAI(name = "OpenAI")
        val model = Model(modelId = "gpt-4.1-mini", displayName = "Mini")
        repeat(12) { index ->
            manager.addLog(
                AILogging.Generation(
                    params = TextGenerationParams(model = model),
                    messages = listOf(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = listOf(UIMessagePart.Text("hello $index")),
                        ),
                    ),
                    providerSetting = provider,
                    stream = true,
                ),
            )
        }
        val logs = manager.getLogs().value
        assertEquals(10, logs.size)
        val last = logs.last() as AILogging.Generation
        assertTrue(last.developerLine().contains("OpenAI"))
        assertTrue(last.developerLine().contains("gpt-4.1-mini"))
        assertTrue(last.developerLine().contains("hello 11"))
        manager.clearLogs()
        assertEquals(emptyList(), manager.getLogs().value)
    }
}
