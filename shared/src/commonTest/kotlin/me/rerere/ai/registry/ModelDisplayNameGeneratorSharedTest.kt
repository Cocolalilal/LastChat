package me.rerere.ai.registry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelDisplayNameGeneratorSharedTest {
    @Test
    fun generatesDisplayNamesInCommonCode() {
        assertEquals("Gemini 2.5 Pro", ModelDisplayNameGenerator.generate("gemini-2.5-pro-preview"))
        assertEquals("GPT-4o", ModelDisplayNameGenerator.generate("openai/gpt-4o"))
        assertEquals("DeepSeek R1", ModelDisplayNameGenerator.generate("deepseek-r1"))
    }

    @Test
    fun disambiguatesBatchCollisionsInCommonCode() {
        val names = ModelDisplayNameGenerator.generateBatch(
            listOf(
                "gemini-2.5-pro" to null,
                "gemini-2.5-pro-preview" to null,
            )
        )

        assertEquals("Gemini 2.5 Pro", names[0])
        assertEquals("Gemini 2.5 Pro Preview", names[1])
        assertTrue("preview" in ModelIdNormalizer.extractStrippedTokens("gemini-2.5-pro-preview"))
    }
}

