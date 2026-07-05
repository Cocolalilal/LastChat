package me.rerere.rikkahub.data.memory

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.common.platform.PlatformLog
import me.rerere.rikkahub.data.ai.buildSummarizerGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.dao.MemoryStoreMetaDao
import me.rerere.rikkahub.data.db.entity.MemBudgetCategory
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaKeys
import me.rerere.rikkahub.data.model.Assistant

/**
 * Generates and caches the per-character memory profile (§6.1). One model call (budget category
 * PROFILE), keyed by a hash of `name + system prompt`, cached in `memory_store_meta`; regenerated
 * only when the hash changes. The detected frames are materialised as FRAME hub nodes through the
 * single-writer [MemoryOpApplier].
 *
 * Reads are free: [getCached] never calls the model, so retrieval (which runs on the hot path)
 * consumes the last cached profile — or the frameless default — without any latency or budget cost.
 * [refresh] is the write path, called off the hot path from the extraction worker.
 */
class CharacterProfileGenerator(
    private val providerManager: ProviderManager,
    private val applier: MemoryOpApplier,
    private val storeMetaDao: MemoryStoreMetaDao,
    private val budget: MemoryBudget,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "CharacterProfile"
    }

    /** The last cached profile for this assistant, or the frameless default if none is cached yet. */
    suspend fun getCached(assistantId: String): CharacterMemoryProfile {
        val json = storeMetaDao.get(MemoryStoreMetaKeys.PROFILE_JSON_PREFIX + assistantId) ?: return CharacterProfileLogic.defaultProfile()
        return CharacterProfileLogic.decode(json) ?: CharacterProfileLogic.defaultProfile()
    }

    /**
     * Ensure the profile is current. If the persona hash is unchanged, returns the cached profile with
     * no model call. Otherwise makes one budgeted PROFILE call; on success it persists the profile +
     * hash and materialises the frames. On no-budget / failure it returns the cached-or-default profile
     * *without* advancing the hash, so the next opportunity retries.
     */
    suspend fun refresh(assistant: Assistant, settings: Settings): CharacterMemoryProfile {
        val assistantId = assistant.id.toString()
        val hash = CharacterProfileLogic.profileHash(assistant.name, assistant.systemPrompt)
        val storedHash = storeMetaDao.get(MemoryStoreMetaKeys.PROFILE_HASH_PREFIX + assistantId)
        if (storedHash == hash) return getCached(assistantId)

        val caps = MemoryBudgetCaps.of(MemoryModels.presetFor(settings, assistantId))
        val resolved = MemoryModels.resolveConsolidation(settings) ?: return getCached(assistantId)
        if (!budget.tryConsumeDaily(MemBudgetCategory.PROFILE, caps.profileDailyCap)) return getCached(assistantId)

        val prompt = CharacterProfileLogic.buildPrompt(assistant.name, assistant.systemPrompt)
        val text = try {
            callModel(resolved.first, resolved.second, prompt, settings)
        } catch (e: Exception) {
            PlatformLog.e(TAG, "profile call failed for ${assistant.name}: ${e.message}")
            return getCached(assistantId)
        }
        val profile = CharacterProfileLogic.parse(text) ?: return getCached(assistantId)

        // Persist the profile + hash, then materialise the frames as hub nodes (single writer).
        storeMetaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.PROFILE_JSON_PREFIX + assistantId, CharacterProfileLogic.encode(profile)))
        storeMetaDao.put(MemoryStoreMetaEntity(MemoryStoreMetaKeys.PROFILE_HASH_PREFIX + assistantId, hash))
        runCatching { applier.ensureCharacterFrames(assistantId, profile.frames, clock()) }
            .onFailure { PlatformLog.e(TAG, "frame materialisation failed: ${it.message}") }
        return profile
    }

    private suspend fun callModel(provider: ProviderSetting, model: Model, prompt: String, settings: Settings): String {
        val handler = providerManager.getProviderByType(provider)
        val response = handler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = settings.buildSummarizerGenerationParams(model = model, temperature = 0.4f),
        )
        return response.choices.firstOrNull()?.message?.toContentText().orEmpty()
    }

}
