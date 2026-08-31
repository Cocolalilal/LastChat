package me.rerere.ai.provider

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexRequestPolicyTest {
    @Test
    fun `unsupported optional response parameters never reach codex transport`() {
        val sanitized = sanitizeCodexResponseRequest(
            buildJsonObject {
                put("model", "gpt-5-codex")
                put("input", "hello")
                put("max_output_tokens", 8_192)
                put("temperature", 0.7)
                put("top_p", 0.9)
                put("stream", true)
            }
        )

        assertFalse(sanitized.containsKey("max_output_tokens"))
        assertFalse(sanitized.containsKey("temperature"))
        assertFalse(sanitized.containsKey("top_p"))
        assertEquals("\"gpt-5-codex\"", sanitized["model"].toString())
        assertTrue(sanitized.containsKey("input"))
        assertTrue(sanitized.containsKey("stream"))
    }
}
