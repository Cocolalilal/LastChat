package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.ModelCatalogStatus
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.mergeCatalogIntoSettings
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.IconStorageManager
import me.rerere.common.platform.PlatformHttpClient

class SettingVM(
    private val settingsStore: SettingsStore,
    private val appScope: me.rerere.rikkahub.AppScope,
    private val mcpManager: McpManager,
    private val context: Context,
    private val platformHttpClient: PlatformHttpClient,
    private val appStorageRepository: AppStorageRepository,
    private val modelCatalogService: ModelCatalogService,
    private val modelMetadataResolver: ModelMetadataResolver,
    private val memoryRepository: MemoryRepository,
    private val localModelStore: me.rerere.locallm.LocalModelStore,
    private val localModelInstall: me.rerere.locallm.ModelInstall,
    private val liteRtRuntime: me.rerere.locallm.LiteRtRuntime,
    private val liteRtEmbedder: me.rerere.locallm.LiteRtEmbedder,
    private val sherpaModelStore: me.rerere.asr.local.SherpaModelStore,
    private val sherpaModelInstall: me.rerere.asr.local.SherpaModelInstall,
    private val sherpaSttRuntime: me.rerere.asr.local.SherpaSttRuntime,
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
    val modelCatalogStatus: StateFlow<ModelCatalogStatus> = modelCatalogService.status
    val modelCatalogSnapshot: StateFlow<ModelCatalogSnapshot?> = modelCatalogService.snapshotFlow

    private val skillEditWriter = SkillEditWriter(context, settingsStore, appScope)

    fun saveSkillEdit(
        skill: me.rerere.rikkahub.data.model.Skill,
        create: Boolean = false,
        synchronizePackage: Boolean = true,
        onSaved: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) = skillEditWriter.save(skill, create, synchronizePackage, onSaved, onError)

    fun deleteProvider(provider: me.rerere.ai.provider.ProviderSetting) {
        viewModelScope.launch {
            if (provider is me.rerere.ai.provider.ProviderSetting.LiteRtLocal) {
                withContext(Dispatchers.IO) {
                    runCatching { liteRtRuntime.unload() }
                    runCatching { liteRtEmbedder.unload() }
                    runCatching { sherpaSttRuntime.unload() }
                    localModelStore.current().forEach { model ->
                        runCatching { localModelInstall.delete(model) }
                    }
                    localModelStore.clear()
                    sherpaModelStore.current().forEach { model ->
                        runCatching { sherpaModelInstall.delete(model) }
                    }
                    sherpaModelStore.clear()
                }
            }
            val current = settings.value
            updateSettings { current -> current.copy(providers = current.providers.filter { it.id != provider.id }) }
        }
    }

    private val _pendingProviderEdit = kotlinx.coroutines.flow.MutableStateFlow<me.rerere.ai.provider.ProviderSetting?>(null)

    /** The provider being edited on the detail page, ahead of its debounced save. */
    val pendingProviderEdit: StateFlow<me.rerere.ai.provider.ProviderSetting?> = _pendingProviderEdit
    private var providerSaveJob: Job? = null
    private var pendingProviderBase: me.rerere.ai.provider.ProviderSetting? = null

    /**
     * Typing in provider fields (API key, base URL, headers…) updates local state at once and is
     * saved after a short pause, applied to the latest settings so other changes aren't lost.
     */
    fun editProvider(provider: me.rerere.ai.provider.ProviderSetting) {
        if (_pendingProviderEdit.value?.id != provider.id) {
            flushProviderEditNow()
            pendingProviderBase = settingsStore.settingsFlow.value.providers.firstOrNull { it.id == provider.id }
        }
        _pendingProviderEdit.value = provider
        providerSaveJob?.cancel()
        // Draft changes and the debounce handoff stay on Main. A background timer could
        // cancel a newer edit's save job while trying to flush the preceding snapshot.
        providerSaveJob = viewModelScope.launch {
            delay(PROVIDER_SAVE_DEBOUNCE_MS)
            enqueueProviderFlush()
        }
    }

    suspend fun flushProviderEdit() {
        enqueueProviderFlush()?.await()
    }

    private fun enqueueProviderFlush(): kotlinx.coroutines.Deferred<Pair<Settings, Settings>>? {
        val pending = _pendingProviderEdit.value ?: return null
        providerSaveJob?.cancel()
        val base = pendingProviderBase ?: pending
        val result = settingsStore.enqueueUpdate { latest ->
            latest.copy(providers = latest.providers.map {
                if (it.id == pending.id) me.rerere.rikkahub.data.datastore.mergeProviderEdit(base, pending, it) else it
            })
        }
        _pendingProviderEdit.compareAndSet(pending, null)
        return result
    }

    /** Saves a pending provider edit now (leaving the screen, app backgrounded). Survives the ViewModel. */
    fun flushProviderEditNow() {
        enqueueProviderFlush()
    }

    /** Drops the pending edit because the caller is saving a newer copy of the same provider. */
    fun supersedeProviderEdit() {
        providerSaveJob?.cancel()
        _pendingProviderEdit.value = null
    }

    override fun onCleared() {
        flushProviderEditNow()
        super.onCleared()
    }

    /**
     * Whole-snapshot save. Only the fields that differ from the snapshot the screen is showing
     * are applied to the latest settings (see [mergeSettingsEdit]), so nothing saved meanwhile
     * is overwritten. Prefer the transform overload.
     */
    fun updateSettings(
        newSettings: Settings,
        afterPersist: (() -> Unit)? = null,
        base: Settings = settings.value,
    ) = updateSettings(afterPersist) { latest -> me.rerere.rikkahub.data.datastore.mergeSettingsEdit(base, newSettings, latest) }

    /**
     * Applies [transform] to the latest stored settings through the store's ordered queue, so
     * quick successive changes from any screen all land and a change made while leaving the
     * screen still persists.
     */
    fun updateSettings(
        afterPersist: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
        transform: (Settings) -> Settings,
    ) {
        val result = settingsStore.enqueueUpdate(transform)
        appScope.launch(Dispatchers.Main.immediate) {
            val (oldSettings, newSettings) = try {
                result.await()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                onError?.invoke(error)
                return@launch
            }
            afterPersist?.invoke()
            val oldProviderIds = oldSettings.providers.map { it.id }.toSet()
            val newProviderIds = newSettings.providers.map { it.id }.toSet()
            if (oldProviderIds != newProviderIds) {
                launch(Dispatchers.IO) { cleanupUnusedIcons(newSettings) }
            }
        }
    }

    /**
     * Clean up icons that are no longer used by any model/provider.
     */
    private fun cleanupUnusedIcons(settings: Settings) {
        val iconManager = IconStorageManager.getInstance(context, platformHttpClient)
        
        // Collect all icon keys that are still in use
        val usedKeys = mutableSetOf<String>()
        
        for (provider in settings.providers) {
            // Add provider icon key (for both light and dark mode)
            val providerSlug = provider.name.lowercase().replace(" ", "-").replace("_", "-")
            usedKeys.add(IconStorageManager.generateIconKey(providerSlug, null, true))
            usedKeys.add(IconStorageManager.generateIconKey(providerSlug, null, false))
            
            // Add model icon keys
            for (model in provider.models) {
                val modelName = model.displayName.ifBlank { model.modelId }
                usedKeys.add(IconStorageManager.generateIconKey(model.providerSlug, modelName, true))
                usedKeys.add(IconStorageManager.generateIconKey(model.providerSlug, modelName, false))
                
                // Also add URL-based keys if model has iconUrl
                if (!model.iconUrl.isNullOrBlank()) {
                    usedKeys.add("url_${model.iconUrl.hashCode()}_dark")
                    usedKeys.add("url_${model.iconUrl.hashCode()}_light")
                }
            }
        }
        
        iconManager.cleanupUnusedIcons(usedKeys)
    }

    private val pendingFileCleanupJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    fun cleanupFilesIfUnreferenced(
        fileRefs: Collection<String>,
        delayMs: Long = 0L,
        cleanupKey: String? = null,
    ) {
        if (cleanupKey != null) {
            cancelUnreferencedFileCleanup(cleanupKey)
        }
        if (fileRefs.isEmpty()) {
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                appStorageRepository.deleteFilesIfUnreferenced(fileRefs)
            } finally {
                if (cleanupKey != null) {
                    pendingFileCleanupJobs.remove(cleanupKey, coroutineContext[Job])
                }
            }
        }
        if (cleanupKey != null) {
            pendingFileCleanupJobs[cleanupKey] = job
        }
    }

    fun cancelUnreferencedFileCleanup(cleanupKey: String) {
        pendingFileCleanupJobs.remove(cleanupKey)?.cancel()
    }

    fun refreshModelCatalog(
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                val status = modelCatalogService.refreshCatalog()
                modelCatalogService.snapshotOrNull()?.let { snapshot ->
                    settingsStore.update { latest ->
                        mergeCatalogIntoSettings(
                            settings = latest,
                            snapshot = snapshot,
                            resolver = modelMetadataResolver,
                        )
                    }
                }
                status
            }.onSuccess {
                onSuccess()
            }.onFailure {
                onError(it)
            }
        }
    }

    fun regenerateMemoryEmbeddings(
        onComplete: (success: Int, failure: Int) -> Unit = { _, _ -> },
        onError: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                var success = 0
                var failure = 0
                settings.value.assistants.forEach { assistant ->
                    val (assistantSuccess, assistantFailure) = memoryRepository.regenerateEmbeddings(
                        assistantId = assistant.id.toString(),
                        onProgress = { _, _ -> },
                    )
                    success += assistantSuccess
                    failure += assistantFailure
                }
                success to failure
            }.onSuccess { (success, failure) ->
                withContext(Dispatchers.Main) {
                    onComplete(success, failure)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    onError(error)
                }
            }
        }
    }
}

private const val PROVIDER_SAVE_DEBOUNCE_MS = 400L
