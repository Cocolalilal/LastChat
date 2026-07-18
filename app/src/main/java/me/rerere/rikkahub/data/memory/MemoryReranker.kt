package me.rerere.rikkahub.data.memory

import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.buildSubagentGenerationParams
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.MemoryRerankMode
import me.rerere.rikkahub.utils.JsonInstant

class MemoryReranker(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) {
    suspend fun rerank(
        mode: MemoryRerankMode,
        selectedModelId: Uuid?,
        query: String,
        candidates: List<TemporalRecallItem>,
    ): List<TemporalRecallItem> {
        if (candidates.size < 2 || mode != MemoryRerankMode.SELECTED_MODEL) {
            // OFF is rank fusion only. LOCAL/AUTOMATIC use the local/selected embedding score already
            // present in first-stage retrieval and never silently invoke a remote generation model.
            return candidates.sortedByDescending { it.score }
        }
        val settings = settingsStore.settingsFlow.value
        // The Default Models choice is authoritative. Keep the old per-assistant id only as a
        // silent compatibility fallback for users who configured reranking before this setting.
        val effectiveModelId = settings.memoryRerankModelId ?: selectedModelId
        val model = effectiveModelId?.let(settings::findModelById) ?: return candidates
        val providerSetting = model.findProvider(settings.providers) ?: return candidates
        val provider = providerManager.getProviderByType(providerSetting)
        val prompt = buildString {
            append("Rank memory candidates by how directly they help answer the query. Use only candidate ids. Output a JSON array of ids, best first.\n")
            append("Query: ").append(query.take(800)).append("\nCandidates:\n")
            candidates.take(MAX_RERANK_CANDIDATES).forEach { item ->
                append(item.stableId).append(": ").append(item.text.take(500)).append('\n')
            }
        }
        val response = runCatching {
            provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(UIMessage.user(prompt)),
                params = settings.buildSubagentGenerationParams(model = model, temperature = 0f),
            )
        }.getOrNull() ?: return candidates
        val text = response.choices.firstOrNull()?.message?.toContentText().orEmpty()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return candidates
        val ids = runCatching {
            (JsonInstant.parseToJsonElement(text.substring(start, end + 1)) as? JsonArray)
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                .orEmpty()
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return candidates
        val byId = candidates.associateBy { it.stableId }
        return (ids.mapNotNull(byId::get) + candidates.filterNot { it.stableId in ids }).distinctBy { it.stableId }
    }

    companion object {
        private const val MAX_RERANK_CANDIDATES = 30
    }
}
