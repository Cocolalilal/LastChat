package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant

/**
 * Three-way merge for callers that hand back a whole edited snapshot: only what changed between
 * [base] (the snapshot the edit started from) and [edited] is applied on top of [latest], so a
 * change saved meanwhile by another screen or a quick second tap is kept. Objects merge per key;
 * ID-bearing lists merge edits and ordering; other lists and scalars are replaced when edited.
 */
fun mergeSettingsEdit(base: Settings, edited: Settings, latest: Settings): Settings {
    if (edited == base) return latest
    if (latest == base) return edited
    // Keep unchanged collections by reference. A display toggle must not serialize every
    // provider, model and assistant prompt just because another field changed meanwhile.
    return latest.copy(
        setupCompleted = selectEditedValue(base.setupCompleted, edited.setupCompleted, latest.setupCompleted),
        dynamicColor = selectEditedValue(base.dynamicColor, edited.dynamicColor, latest.dynamicColor),
        themeId = selectEditedValue(base.themeId, edited.themeId, latest.themeId),
        developerMode = selectEditedValue(base.developerMode, edited.developerMode, latest.developerMode),
        enableRagLogging = selectEditedValue(base.enableRagLogging, edited.enableRagLogging, latest.enableRagLogging),
        displaySetting = mergeSettingsValue(base.displaySetting, edited.displaySetting, latest.displaySetting),
        enableWebSearch = selectEditedValue(base.enableWebSearch, edited.enableWebSearch, latest.enableWebSearch),
        favoriteModels = selectEditedValue(base.favoriteModels, edited.favoriteModels, latest.favoriteModels),
        chatModelId = selectEditedValue(base.chatModelId, edited.chatModelId, latest.chatModelId),
        titleModelId = selectEditedValue(base.titleModelId, edited.titleModelId, latest.titleModelId),
        titleThinkingBudget = selectEditedValue(
            base.titleThinkingBudget,
            edited.titleThinkingBudget,
            latest.titleThinkingBudget,
        ),
        summarizerModelId = selectEditedValue(
            base.summarizerModelId,
            edited.summarizerModelId,
            latest.summarizerModelId,
        ),
        summarizerThinkingBudget = selectEditedValue(
            base.summarizerThinkingBudget,
            edited.summarizerThinkingBudget,
            latest.summarizerThinkingBudget,
        ),
        subagentModelId = selectEditedValue(base.subagentModelId, edited.subagentModelId, latest.subagentModelId),
        subagentThinkingBudget = selectEditedValue(
            base.subagentThinkingBudget,
            edited.subagentThinkingBudget,
            latest.subagentThinkingBudget,
        ),
        imageGenerationModelId = selectEditedValue(
            base.imageGenerationModelId,
            edited.imageGenerationModelId,
            latest.imageGenerationModelId,
        ),
        titlePrompt = selectEditedValue(base.titlePrompt, edited.titlePrompt, latest.titlePrompt),
        translateModeId = selectEditedValue(base.translateModeId, edited.translateModeId, latest.translateModeId),
        translatePrompt = selectEditedValue(base.translatePrompt, edited.translatePrompt, latest.translatePrompt),
        suggestionModelId = selectEditedValue(
            base.suggestionModelId,
            edited.suggestionModelId,
            latest.suggestionModelId,
        ),
        suggestionThinkingBudget = selectEditedValue(
            base.suggestionThinkingBudget,
            edited.suggestionThinkingBudget,
            latest.suggestionThinkingBudget,
        ),
        suggestionPrompt = selectEditedValue(base.suggestionPrompt, edited.suggestionPrompt, latest.suggestionPrompt),
        learningModePrompt = selectEditedValue(
            base.learningModePrompt,
            edited.learningModePrompt,
            latest.learningModePrompt,
        ),
        ocrModelId = selectEditedValue(base.ocrModelId, edited.ocrModelId, latest.ocrModelId),
        ocrThinkingBudget = selectEditedValue(
            base.ocrThinkingBudget,
            edited.ocrThinkingBudget,
            latest.ocrThinkingBudget,
        ),
        ocrPrompt = selectEditedValue(base.ocrPrompt, edited.ocrPrompt, latest.ocrPrompt),
        embeddingModelId = selectEditedValue(base.embeddingModelId, edited.embeddingModelId, latest.embeddingModelId),
        assistantId = selectEditedValue(base.assistantId, edited.assistantId, latest.assistantId),
        providers = mergeSettingsValue(base.providers, edited.providers, latest.providers),
        assistants = mergeSettingsValue(base.assistants, edited.assistants, latest.assistants),
        assistantTags = mergeSettingsValue(base.assistantTags, edited.assistantTags, latest.assistantTags),
        providerTags = mergeSettingsValue(base.providerTags, edited.providerTags, latest.providerTags),
        recentlyUsedAssistants = selectEditedValue(
            base.recentlyUsedAssistants,
            edited.recentlyUsedAssistants,
            latest.recentlyUsedAssistants,
        ),
        searchServices = mergeSettingsValue(base.searchServices, edited.searchServices, latest.searchServices),
        searchCommonOptions = mergeSettingsValue(
            base.searchCommonOptions,
            edited.searchCommonOptions,
            latest.searchCommonOptions,
        ),
        searchServiceSelected = selectEditedValue(
            base.searchServiceSelected,
            edited.searchServiceSelected,
            latest.searchServiceSelected,
        ),
        mcpServers = mergeSettingsValue(base.mcpServers, edited.mcpServers, latest.mcpServers),
        webDavConfig = mergeSettingsValue(base.webDavConfig, edited.webDavConfig, latest.webDavConfig),
        ttsProviders = mergeSettingsValue(base.ttsProviders, edited.ttsProviders, latest.ttsProviders),
        selectedTTSProviderId = selectEditedValue(
            base.selectedTTSProviderId,
            edited.selectedTTSProviderId,
            latest.selectedTTSProviderId,
        ),
        selectedTTSVoiceId = selectEditedValue(
            base.selectedTTSVoiceId,
            edited.selectedTTSVoiceId,
            latest.selectedTTSVoiceId,
        ),
        ttsAutoplayMode = selectEditedValue(base.ttsAutoplayMode, edited.ttsAutoplayMode, latest.ttsAutoplayMode),
        sttModelId = selectEditedValue(base.sttModelId, edited.sttModelId, latest.sttModelId),
        sttThinkingBudget = selectEditedValue(
            base.sttThinkingBudget,
            edited.sttThinkingBudget,
            latest.sttThinkingBudget,
        ),
        sttPrompt = selectEditedValue(base.sttPrompt, edited.sttPrompt, latest.sttPrompt),
        webServerEnabled = selectEditedValue(base.webServerEnabled, edited.webServerEnabled, latest.webServerEnabled),
        webServerPort = selectEditedValue(base.webServerPort, edited.webServerPort, latest.webServerPort),
        webServerJwtEnabled = selectEditedValue(
            base.webServerJwtEnabled,
            edited.webServerJwtEnabled,
            latest.webServerJwtEnabled,
        ),
        webServerAccessPassword = selectEditedValue(
            base.webServerAccessPassword,
            edited.webServerAccessPassword,
            latest.webServerAccessPassword,
        ),
        webServerBackgroundSetupShown = selectEditedValue(
            base.webServerBackgroundSetupShown,
            edited.webServerBackgroundSetupShown,
            latest.webServerBackgroundSetupShown,
        ),
        modes = mergeSettingsValue(base.modes, edited.modes, latest.modes),
        lorebooks = mergeSettingsValue(base.lorebooks, edited.lorebooks, latest.lorebooks),
        skills = mergeSettingsValue(base.skills, edited.skills, latest.skills),
        chatStorage = mergeSettingsValue(base.chatStorage, edited.chatStorage, latest.chatStorage),
        textSelectionConfig = mergeSettingsValue(
            base.textSelectionConfig,
            edited.textSelectionConfig,
            latest.textSelectionConfig,
        ),
        assistantOverlayConfig = mergeSettingsValue(
            base.assistantOverlayConfig,
            edited.assistantOverlayConfig,
            latest.assistantOverlayConfig,
        ),
        enableVoiceMemosMemoryBridge = selectEditedValue(
            base.enableVoiceMemosMemoryBridge,
            edited.enableVoiceMemosMemoryBridge,
            latest.enableVoiceMemosMemoryBridge,
        ),
        smartMicProfiles = mergeSettingsValue(
            base.smartMicProfiles,
            edited.smartMicProfiles,
            latest.smartMicProfiles,
        ),
        selectedSmartMicProfileId = selectEditedValue(
            base.selectedSmartMicProfileId,
            edited.selectedSmartMicProfileId,
            latest.selectedSmartMicProfileId,
        ),
    )
}

private fun <T> selectEditedValue(base: T, edited: T, latest: T): T = if (edited == base) latest else edited

/** Three-way merge for a changed settings section; unchanged sections never enter JSON. */
internal inline fun <reified T> mergeSettingsValue(base: T, edited: T, latest: T): T {
    if (edited == base) return latest
    if (latest == base || latest == edited) return edited
    val serializer = serializer<T>()
    return JsonInstant.decodeFromJsonElement(serializer, mergeJson(
        JsonInstant.encodeToJsonElement(serializer, base),
        JsonInstant.encodeToJsonElement(serializer, edited),
        JsonInstant.encodeToJsonElement(serializer, latest),
    ) ?: return latest)
}

internal fun mergeJson(base: JsonElement?, edited: JsonElement?, latest: JsonElement?): JsonElement? {
    if (edited == base) return latest
    if (base is JsonArray && edited is JsonArray && latest is JsonArray) {
        mergeIdentifiedArray(base, edited, latest)?.let { return it }
    }
    if (base is JsonObject && edited is JsonObject && latest is JsonObject) {
        val keys = base.keys + edited.keys + latest.keys
        val out = LinkedHashMap<String, JsonElement>()
        for (k in keys) {
            mergeJson(base[k], edited[k], latest[k])?.let { out[k] = it }
        }
        return JsonObject(out)
    }
    return edited
}

/** Field edits and ordering changes to ID-bearing lists commute instead of replacing the list. */
private fun mergeIdentifiedArray(base: JsonArray, edited: JsonArray, latest: JsonArray): JsonArray? {
    fun identify(array: JsonArray): LinkedHashMap<String, JsonElement>? {
        val result = linkedMapOf<String, JsonElement>()
        for (element in array) {
            val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.content ?: return null
            if (result.put(id, element) != null) return null
        }
        return result
    }
    val before = identify(base) ?: return null
    val after = identify(edited) ?: return null
    val current = identify(latest) ?: return null
    val removed = before.keys - after.keys
    val result = linkedMapOf<String, JsonElement>()
    for ((id, value) in current) {
        if (id !in removed) result[id] = if (id in after) mergeJson(before[id], after[id], value) ?: value else value
    }
    // An item deleted meanwhile must stay deleted; only genuinely new IDs may be inserted.
    for ((id, value) in after) if (id !in before && id !in result) result[id] = value
    val common = before.keys.intersect(after.keys)
    if ((after.keys - before.keys).isNotEmpty() ||
        before.keys.filter { it in common } != after.keys.filter { it in common }) {
        val requested = after.keys.filter { it in result }.iterator()
        val included = after.keys.intersect(result.keys)
        return JsonArray(result.keys.map { id -> result[if (id in included) requested.next() else id]!! })
    }
    return JsonArray(result.values.toList())
}

internal fun mergeProviderEdit(
    base: me.rerere.ai.provider.ProviderSetting,
    edited: me.rerere.ai.provider.ProviderSetting,
    latest: me.rerere.ai.provider.ProviderSetting,
): me.rerere.ai.provider.ProviderSetting {
    if (edited == base) return latest
    if (latest == base || latest == edited) return edited
    val serializer = me.rerere.ai.provider.ProviderSetting.serializer()
    val b = JsonInstant.encodeToJsonElement(serializer, base)
    val e = JsonInstant.encodeToJsonElement(serializer, edited)
    val l = JsonInstant.encodeToJsonElement(serializer, latest)
    if ((b as? JsonObject)?.get("type") != (e as? JsonObject)?.get("type")) return edited
    return JsonInstant.decodeFromJsonElement(serializer, mergeJson(b, e, l) ?: l)
}

internal fun mergeTtsProviderEdit(
    base: me.rerere.tts.provider.TTSProviderSetting,
    edited: me.rerere.tts.provider.TTSProviderSetting,
    latest: me.rerere.tts.provider.TTSProviderSetting,
): me.rerere.tts.provider.TTSProviderSetting {
    if (edited == base) return latest
    if (latest == base || latest == edited) return edited
    val serializer = me.rerere.tts.provider.TTSProviderSetting.serializer()
    val b = JsonInstant.encodeToJsonElement(serializer, base)
    val e = JsonInstant.encodeToJsonElement(serializer, edited)
    val l = JsonInstant.encodeToJsonElement(serializer, latest)
    return JsonInstant.decodeFromJsonElement(serializer, mergeJson(b, e, l) ?: l)
}

internal fun mergeAssistantEdit(
    base: me.rerere.rikkahub.data.model.Assistant,
    edited: me.rerere.rikkahub.data.model.Assistant,
    latest: me.rerere.rikkahub.data.model.Assistant,
): me.rerere.rikkahub.data.model.Assistant {
    if (edited == base) return latest
    if (latest == base || latest == edited) return edited
    val serializer = me.rerere.rikkahub.data.model.Assistant.serializer()
    val b = JsonInstant.encodeToJsonElement(serializer, base)
    val e = JsonInstant.encodeToJsonElement(serializer, edited)
    val l = JsonInstant.encodeToJsonElement(serializer, latest)
    return JsonInstant.decodeFromJsonElement(serializer, mergeJson(b, e, l) ?: l)
}
