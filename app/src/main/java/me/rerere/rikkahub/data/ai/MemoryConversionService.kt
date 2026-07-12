package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryConversionDirection
import me.rerere.rikkahub.data.model.MemoryConversionEntryProposal
import me.rerere.rikkahub.data.model.MemoryConversionPreview
import me.rerere.rikkahub.data.model.memoryCodePointCount
import me.rerere.rikkahub.data.repository.HybridMemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

class MemoryConversionService(
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val hybridMemoryRepository: HybridMemoryRepository,
) {
    suspend fun generatePreview(
        assistant: Assistant,
        direction: MemoryConversionDirection,
    ): MemoryConversionPreview {
        val input = hybridMemoryRepository.buildConversionInput(assistant, direction)
        val settings = settingsStore.settingsFlow.value
        val model = settings.summarizerModelId?.let(settings::findModelById)
        val provider = model?.findProvider(settings.providers)
        val parsed = if (model != null && provider != null && input.changedEvidence.isNotEmpty()) {
            val prompt = when (direction) {
                MemoryConversionDirection.ENTRY_TO_DOCUMENT -> """
                    Produce an incremental conversion preview from Entry-based memory into two complete living documents.
                    Return JSON only: {"user_profile":"complete replacement","character_memory":"complete replacement","summary":"short explanation"}.
                    Preserve useful existing text, incorporate only supported evidence, deduplicate, and compact coherently.
                    User Profile hard limit: ${assistant.userProfileCharLimit} Unicode characters.
                    Character Memory hard limit: ${assistant.characterMemoryCharLimit} Unicode characters.
                    Existing User Profile:
                    ${input.userProfile}
                    Existing Character Memory:
                    ${input.characterMemory}
                    New source evidence:
                    ${input.changedEvidence.joinToString("\n").take(30_000)}
                """.trimIndent()
                MemoryConversionDirection.DOCUMENT_TO_ENTRY -> """
                    Produce an incremental conversion preview from living documents and newer distilled evidence into editable memory entries.
                    Return JSON only: {"entries":[{"content":"one self-contained fact","existing_entry_id":null,"reason":"why"}],"summary":"short explanation"}.
                    Deduplicate against existing entries. Only set existing_entry_id when correcting that exact entry.
                    Existing entries:
                    ${input.entries.joinToString("\n") { "${it.id}: ${it.content}" }.take(15_000)}
                    User Profile:
                    ${input.userProfile}
                    Character Memory:
                    ${input.characterMemory}
                    New source evidence:
                    ${input.changedEvidence.joinToString("\n").take(25_000)}
                """.trimIndent()
            }
            runCatching {
                val response = providerManager.getProviderByType(provider).generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = settings.buildSummarizerGenerationParams(model, temperature = 0.1f),
                ).choices.firstOrNull()?.message?.toContentText().orEmpty()
                response.extractJsonObject()
            }.getOrNull()
        } else null

        val fallback = fallbackPreviewContent(input, assistant)
        val userProfile = parsed?.get("user_profile")?.jsonPrimitive?.contentOrNull
            ?.compactToLimit(assistant.userProfileCharLimit) ?: fallback.first
        val characterMemory = parsed?.get("character_memory")?.jsonPrimitive?.contentOrNull
            ?.compactToLimit(assistant.characterMemoryCharLimit) ?: fallback.second
        val existingByNormalized = input.entries.associateBy { normalize(it.content) }
        val proposals = if (direction == MemoryConversionDirection.DOCUMENT_TO_ENTRY) {
            val modelEntries = (parsed?.get("entries") as? JsonArray).orEmpty().mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val content = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (content.isBlank()) return@mapNotNull null
                MemoryConversionEntryProposal(
                    content = content,
                    existingEntryId = obj["existing_entry_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    reason = obj["reason"]?.jsonPrimitive?.contentOrNull ?: "Converted from documents",
                )
            }
            (modelEntries.ifEmpty { fallback.third })
                .distinctBy { normalize(it.content) }
                .filter { proposal ->
                    val duplicate = existingByNormalized[normalize(proposal.content)]
                    duplicate == null || proposal.existingEntryId == duplicate.id
                }
                .take(100)
        } else emptyList()

        return MemoryConversionPreview(
            id = Uuid.random().toString(),
            assistantId = input.assistantId,
            direction = direction,
            sourceRevision = input.sourceRevision,
            previousWatermark = input.previousWatermark,
            expectedUserProfileRevision = input.userProfileRevision,
            expectedCharacterMemoryRevision = input.characterMemoryRevision,
            userProfileReplacement = userProfile.takeIf { direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT },
            characterMemoryReplacement = characterMemory.takeIf { direction == MemoryConversionDirection.ENTRY_TO_DOCUMENT },
            entryProposals = proposals,
            summary = parsed?.get("summary")?.jsonPrimitive?.contentOrNull
                ?: if (input.changedEvidence.isEmpty()) "No newer source memories were found." else "Preview generated from ${input.changedEvidence.size} newer memory sources.",
        )
    }

    private fun fallbackPreviewContent(
        input: me.rerere.rikkahub.data.model.MemoryConversionInput,
        assistant: Assistant,
    ): Triple<String, String, List<MemoryConversionEntryProposal>> {
        val newEntryText = input.entries
            .filter { it.timestamp > input.previousWatermark }
            .joinToString("\n") { "- ${it.content.trim()}" }
        val userProfile = appendSection(input.userProfile, "Imported entry memory", newEntryText, assistant.userProfileCharLimit)
        val characterMemory = appendSection(input.characterMemory, "Imported continuity", input.changedEvidence.joinToString("\n") { "- $it" }, assistant.characterMemoryCharLimit)
        val proposals = sequenceOf(input.userProfile, input.characterMemory)
            .flatMap { it.lines().asSequence() }
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.length >= 8 }
            .map { MemoryConversionEntryProposal(it) }
            .toList()
        return Triple(userProfile, characterMemory, proposals)
    }

    private fun appendSection(existing: String, heading: String, additions: String, limit: Int): String {
        if (additions.isBlank()) return existing.compactToLimit(limit)
        return listOf(existing.trim(), "$heading:\n$additions").filter { it.isNotBlank() }
            .joinToString("\n\n").compactToLimit(limit)
    }
}

private fun String.extractJsonObject(): JsonObject? {
    val body = substringAfter('{', "").substringBeforeLast('}', "")
    return if (body.isBlank()) null else JsonInstant.parseToJsonElement("{$body}") as? JsonObject
}

private fun String.compactToLimit(limit: Int): String {
    if (memoryCodePointCount() <= limit) return trim()
    val sections = split(Regex("\n{2,}"))
    val selected = mutableListOf<String>()
    sections.forEach { section ->
        val candidate = (selected + section.trim()).joinToString("\n\n")
        if (candidate.memoryCodePointCount() <= limit) selected += section.trim()
    }
    return selected.joinToString("\n\n")
}

private fun normalize(value: String) = value.lowercase().replace(Regex("\\s+"), " ").trim()
