package me.rerere.ai.provider.providers

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ModelAbility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleProviderThinkingConfigTest {
    @Test
    fun `no reasoning ability omits thinkingConfig`() {
        assertNull(
            buildGoogleThinkingConfig(
                modelId = "gemini-2.5-flash",
                abilities = emptyList(),
                thinkingBudget = -1,
            )
        )
    }

    @Test
    fun `auto budget enables dynamic thinking with includeThoughts`() {
        for (budget in listOf(null, -1)) {
            val config = buildGoogleThinkingConfig(
                modelId = "gemini-2.5-flash-lite",
                abilities = listOf(ModelAbility.REASONING),
                thinkingBudget = budget,
            )
            assertNotNull("budget=$budget", config)
            assertEquals(true, config!!["includeThoughts"]?.jsonPrimitive?.booleanOrNull)
            assertEquals(-1, config["thinkingBudget"]?.jsonPrimitive?.intOrNull)
            assertNull(config["thinkingLevel"])
        }
    }

    @Test
    fun `off budget omits thinkingConfig for non-pro`() {
        assertNull(
            buildGoogleThinkingConfig(
                modelId = "gemini-2.5-flash",
                abilities = listOf(ModelAbility.REASONING),
                thinkingBudget = 0,
            )
        )
    }

    @Test
    fun `off budget keeps thoughts for gemini pro`() {
        val config = buildGoogleThinkingConfig(
            modelId = "gemini-2.5-pro",
            abilities = listOf(ModelAbility.REASONING),
            thinkingBudget = 0,
        )
        assertNotNull(config)
        assertEquals(true, config!!["includeThoughts"]?.jsonPrimitive?.booleanOrNull)
        assertNull(config["thinkingBudget"])
    }

    @Test
    fun `explicit budget is forwarded`() {
        val config = buildGoogleThinkingConfig(
            modelId = "gemini-2.5-flash",
            abilities = listOf(ModelAbility.REASONING),
            thinkingBudget = 1024,
        )
        assertNotNull(config)
        assertEquals(true, config!!["includeThoughts"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(1024, config["thinkingBudget"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun `gemini 3 auto only sets includeThoughts`() {
        val config = buildGoogleThinkingConfig(
            modelId = "gemini-3-flash-preview",
            abilities = listOf(ModelAbility.REASONING),
            thinkingBudget = -1,
        )
        assertNotNull(config)
        assertEquals(true, config!!["includeThoughts"]?.jsonPrimitive?.booleanOrNull)
        assertNull(config["thinkingBudget"])
        assertNull(config["thinkingLevel"])
    }

    @Test
    fun `gemini 3 off uses minimal thinkingLevel`() {
        val config = buildGoogleThinkingConfig(
            modelId = "gemini-3-pro-preview",
            abilities = listOf(ModelAbility.REASONING),
            thinkingBudget = 0,
        )
        assertNotNull(config)
        assertEquals(true, config!!["includeThoughts"]?.jsonPrimitive?.booleanOrNull)
        assertEquals("minimal", config["thinkingLevel"]?.jsonPrimitive?.contentOrNull)
        assertTrue(config["thinkingBudget"] == null)
    }
}
