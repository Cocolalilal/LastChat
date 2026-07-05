package me.rerere.rikkahub.data.memory

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider

/**
 * The single place memory model choices are resolved. The three memory models (parser, embedding,
 * consolidation) are configured globally in Settings → Default Models → Memory; there is
 * deliberately NO fallback to summarizer/background/chat models — an unset model pauses the
 * corresponding model-assisted work (deterministic stages, retrieval and FTS keep running) and the
 * memory UI surfaces a "set up memory models" notice via [health].
 */
object MemoryModels {

    /** Extraction ("parser") model. Null = extraction is paused; the watermark holds, nothing is lost. */
    fun resolveParser(settings: Settings): Pair<ProviderSetting, Model>? =
        resolve(settings, settings.memory.parserModelId)

    /** Consolidation model (sleep adjudication/compression, profiles, curiosity). Null = those stages pause. */
    fun resolveConsolidation(settings: Settings): Pair<ProviderSetting, Model>? =
        resolve(settings, settings.memory.consolidationModelId)

    private fun resolve(settings: Settings, modelId: kotlin.uuid.Uuid?): Pair<ProviderSetting, Model>? {
        if (modelId == null) return null
        val model = settings.findModelById(modelId) ?: return null
        val provider = model.findProvider(settings.providers) ?: return null
        return provider to model
    }

    /**
     * The cost preset governing a scope: a character scope uses its assistant's own preset; the
     * GLOBAL_USER scope (assistantId null/blank) uses [globalPreset].
     */
    fun presetFor(settings: Settings, assistantId: String?): MemoryPreset {
        if (assistantId.isNullOrBlank()) return globalPreset(settings)
        val assistant = settings.assistants.firstOrNull { it.id.toString() == assistantId }
            ?: return globalPreset(settings)
        return MemoryPreset.fromNameOrDefault(assistant.memoryPreset)
    }

    /**
     * Preset for the shared GLOBAL_USER scope: the max preset among memory-enabled assistants, so
     * the shared layer never runs leaner than the richest character feeding it. Balanced floor when
     * no assistant has memory enabled (the machinery is idle then anyway).
     */
    fun globalPreset(settings: Settings): MemoryPreset =
        settings.assistants
            .filter { it.enableMemory }
            .maxOfOrNull { MemoryPreset.fromNameOrDefault(it.memoryPreset) }
            ?: MemoryPreset.BALANCED

    /** Habit induction for the shared scope: on when any memory-enabled assistant wants it. */
    fun globalHabitInduction(settings: Settings): Boolean =
        settings.assistants.any { it.enableMemory && it.memoryHabitInduction }

    /** What the memory pipeline is missing, for the "set up memory models" notice. */
    data class Health(
        val parserMissing: Boolean,
        val consolidationMissing: Boolean,
        val embeddingMissing: Boolean,
    ) {
        val anyModelMissing: Boolean get() = parserMissing || consolidationMissing
    }

    fun health(settings: Settings): Health = Health(
        parserMissing = resolveParser(settings) == null,
        consolidationMissing = resolveConsolidation(settings) == null,
        embeddingMissing = resolve(settings, settings.embeddingModelId) == null,
    )
}
