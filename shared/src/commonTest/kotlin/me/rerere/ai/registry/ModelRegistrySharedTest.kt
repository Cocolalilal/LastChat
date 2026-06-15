package me.rerere.ai.registry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelRegistrySharedTest {
    @Test
    fun matchesModelCapabilitiesInCommonCode() {
        assertTrue(ModelRegistry.VISION_MODELS.match("gpt-4o"))
        assertTrue(ModelRegistry.TOOL_MODELS.match("gemini-2.5-pro"))
        assertTrue(ModelRegistry.REASONING_MODELS.match("deepseek-r1"))
        assertFalse(ModelRegistry.GPT_5.match("gpt-5-chat"))
    }
}

