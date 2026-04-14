package me.rerere.ai.registry

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameGeneratorTest {

    // ---------- Single-model generation (existing behavior) ----------

    @Test
    fun `single model - basic brand formatting`() {
        assertEquals("Gemini 2.5 Pro", ModelDisplayNameGenerator.generate("gemini-2.5-pro"))
        assertEquals("Claude 3.5 Sonnet", ModelDisplayNameGenerator.generate("claude-3.5-sonnet"))
        assertEquals("GPT-4o", ModelDisplayNameGenerator.generate("gpt-4o"))
        assertEquals("DeepSeek R1", ModelDisplayNameGenerator.generate("deepseek-r1"))
        assertEquals("Llama 3.1 70B", ModelDisplayNameGenerator.generate("llama-3.1-70b"))
    }

    @Test
    fun `single model - preview stripped`() {
        // Single model: preview is stripped as usual
        assertEquals("Gemini 2.5 Pro", ModelDisplayNameGenerator.generate("gemini-2.5-pro-preview"))
    }

    // ---------- Batch generation - no collisions ----------

    @Test
    fun `batch - no collisions stays unchanged`() {
        val entries = listOf(
            "gemini-2.5-pro" to null,
            "claude-3.5-sonnet" to null,
            "gpt-4o" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Gemini 2.5 Pro", names[0])
        assertEquals("Claude 3.5 Sonnet", names[1])
        assertEquals("GPT-4o", names[2])
    }

    // ---------- Batch generation - qualifier disambiguation ----------

    @Test
    fun `batch - preview vs base model`() {
        val entries = listOf(
            "gemini-2.5-pro" to null,
            "gemini-2.5-pro-preview" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Gemini 2.5 Pro", names[0])
        assertEquals("Gemini 2.5 Pro Preview", names[1])
    }

    @Test
    fun `batch - free vs base model`() {
        val entries = listOf(
            "deepseek-r1" to null,
            "deepseek-r1-free" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("DeepSeek R1", names[0])
        assertEquals("DeepSeek R1 Free", names[1])
    }

    @Test
    fun `batch - beta and preview both present`() {
        val entries = listOf(
            "some-model" to null,
            "some-model-preview" to null,
            "some-model-beta" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Some Model", names[0])
        assertEquals("Some Model Preview", names[1])
        assertEquals("Some Model Beta", names[2])
    }

    // ---------- Batch generation - parameter size disambiguation ----------

    @Test
    fun `batch - parameter sizes already differ`() {
        // These already canonicalize differently (8b vs 70b) so no collision
        val entries = listOf(
            "llama-3.1-8b" to null,
            "llama-3.1-70b" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Llama 3.1 8B", names[0])
        assertEquals("Llama 3.1 70B", names[1])
    }

    @Test
    fun `batch - qwen parameter variants`() {
        val entries = listOf(
            "qwen-3-8b" to null,
            "qwen-3-30b" to null,
            "qwen-3-235b" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Qwen 3 8B", names[0])
        assertEquals("Qwen 3 30B", names[1])
        assertEquals("Qwen 3 235B", names[2])
    }

    // ---------- Batch generation - date disambiguation ----------

    @Test
    fun `batch - date suffix preserves preview when needed`() {
        val entries = listOf(
            "gemini-2.5-flash" to null,
            "gemini-2.5-flash-preview-04-17" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Gemini 2.5 Flash", names[0])
        // The preview token should be restored for disambiguation
        assert(names[1].contains("Preview")) {
            "Expected 'Preview' in '${names[1]}', but it wasn't there"
        }
    }

    // ---------- Batch generation - mixed collisions ----------

    @Test
    fun `batch - mixed collection with collisions and non-collisions`() {
        val entries = listOf(
            "gpt-4o" to null,
            "gpt-4o-mini" to null,
            "gemini-2.5-pro" to null,
            "gemini-2.5-pro-preview" to null,
            "claude-3.5-sonnet" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("GPT-4o", names[0])
        assertEquals("GPT-4o Mini", names[1])
        assertEquals("Gemini 2.5 Pro", names[2])
        assertEquals("Gemini 2.5 Pro Preview", names[3])
        assertEquals("Claude 3.5 Sonnet", names[4])
    }

    @Test
    fun `batch - latest suffix disambiguation`() {
        val entries = listOf(
            "some-model" to null,
            "some-model-latest" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Some Model", names[0])
        assertEquals("Some Model Latest", names[1])
    }

    // ---------- Edge cases ----------

    @Test
    fun `batch - empty input`() {
        assertEquals(emptyList<String>(), ModelDisplayNameGenerator.generateBatch(emptyList()))
    }

    @Test
    fun `batch - single model passes through`() {
        val entries = listOf("gemini-2.5-pro-preview" to null)
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("Gemini 2.5 Pro", names[0]) // Still strips preview when alone
    }

    @Test
    fun `batch - provider namespace stripped before comparison`() {
        val entries = listOf(
            "openai/gpt-4o" to null,
            "gpt-4o-mini" to null,
        )
        val names = ModelDisplayNameGenerator.generateBatch(entries)
        assertEquals("GPT-4o", names[0])
        assertEquals("GPT-4o Mini", names[1])
    }

    // ---------- Normalizer stripped tokens ----------

    @Test
    fun `extractStrippedTokens - preview is detected`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("gemini-2.5-pro-preview")
        assert("preview" in tokens) { "Expected 'preview' in $tokens" }
    }

    @Test
    fun `extractStrippedTokens - free is detected`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("deepseek-r1-free")
        assert("free" in tokens) { "Expected 'free' in $tokens" }
    }

    @Test
    fun `extractStrippedTokens - no tokens stripped for clean ID`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("gemini-2.5-pro")
        assertEquals(emptyList<String>(), tokens)
    }

    @Test
    fun `extractStrippedTokens - date is detected`() {
        val tokens = ModelIdNormalizer.extractStrippedTokens("gemini-2.5-flash-preview-04-17")
        assert(tokens.isNotEmpty()) { "Expected stripped tokens for date-suffixed model" }
    }
}
