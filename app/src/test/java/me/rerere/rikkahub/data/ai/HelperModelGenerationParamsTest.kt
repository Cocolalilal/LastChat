package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Test

class HelperModelGenerationParamsTest {
    private val model = Model(
        modelId = "qwen3-32b",
        displayName = "Qwen 3",
        abilities = listOf(ModelAbility.REASONING),
    )

    @Test
    fun helperReasoningBudgetsDefaultToOff() {
        val settings = Settings()

        assertEquals(0, settings.titleThinkingBudget)
        assertEquals(0, settings.summarizerThinkingBudget)
        assertEquals(0, settings.suggestionThinkingBudget)
        assertEquals(0, settings.ocrThinkingBudget)
    }

    @Test
    fun assistantReasoningDefaultsToAuto() {
        assertEquals(-1, Assistant().thinkingBudget)
    }

    @Test
    fun helperGenerationParamsUseConfiguredBudgets() {
        val settings = Settings(
            titleThinkingBudget = -1,
            summarizerThinkingBudget = 32_000,
            suggestionThinkingBudget = 1_024,
            ocrThinkingBudget = 16_000,
        )

        assertEquals(-1, settings.buildTitleGenerationParams(model).thinkingBudget)
        assertEquals(32_000, settings.buildSummarizerGenerationParams(model, 0.3f).thinkingBudget)
        assertEquals(1_024, settings.buildSuggestionGenerationParams(model).thinkingBudget)
        assertEquals(16_000, settings.buildOcrGenerationParams(model).thinkingBudget)
    }
}
