package me.rerere.rikkahub.ui.pages.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.dao.MemoryStoreMetaDao
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryActivityEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryStoreMetaKeys
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.memory.MemoryApplyContext
import me.rerere.rikkahub.data.memory.MemoryBudget
import me.rerere.rikkahub.data.memory.MemoryGraphRepository
import me.rerere.rikkahub.data.memory.MemoryOpApplier
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.MemoryOp
import kotlin.uuid.Uuid

/**
 * Backs the Memory Center (P4a). [assistantId] null → the shared GLOBAL_USER layer (opened from
 * Settings); non-null → that character's view (its CHARACTER scope + the shared layer). The empty
 * string is used as the SQL owner filter for the global view so only GLOBAL_USER rows match.
 */
class MemoryCenterVM(
    /** The assistant id, or the empty string for the shared GLOBAL_USER view. */
    scopeAssistantId: String,
    private val settingsStore: SettingsStore,
    private val graphRepository: MemoryGraphRepository,
    private val applier: MemoryOpApplier,
    private val budget: MemoryBudget,
    private val storeMetaDao: MemoryStoreMetaDao,
    private val legacyMemoryDao: MemoryDAO,
    private val legacyEpisodeDao: ChatEpisodeDAO,
) : ViewModel() {

    val assistantId: String? = scopeAssistantId.ifBlank { null }
    val isGlobal: Boolean = assistantId == null
    private val scopeId: String = scopeAssistantId

    val settings: StateFlow<Settings> = settingsStore.settingsFlow

    val assistant: StateFlow<Assistant?> = settings
        .map { s -> assistantId?.let { id -> s.assistants.firstOrNull { it.id.toString() == id } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val nodes: StateFlow<List<MemoryNodeEntity>> = graphRepository.observeBrowsable(scopeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activity: StateFlow<List<MemoryActivityEntity>> = graphRepository.observeActivity(scopeId, limit = 300)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Reactive graph input for the Graph tab (§10.2): this scope's browsable nodes plus every edge.
     * Edges are loaded whole (see [MemoryGraphRepository.getAllEdges]) and only while the Graph tab is
     * subscribed (WhileSubscribed) — `MemoryGraphBuilder` filters them to the drawn neighborhood.
     */
    val graphInput: StateFlow<MemoryGraphInput> = nodes
        .mapLatest { ns ->
            val edges = if (ns.isEmpty()) emptyList() else graphRepository.getAllEdges()
            MemoryGraphInput(ns, edges)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryGraphInput.EMPTY)

    private val _importProgress = MutableStateFlow<MemoryGraphRepository.ImportProgress?>(null)
    val importProgress: StateFlow<MemoryGraphRepository.ImportProgress?> = _importProgress

    private val _budgetToday = MutableStateFlow<Map<String, Int>>(emptyMap())
    val budgetToday: StateFlow<Map<String, Int>> = _budgetToday

    /** Node ids briefly highlighted after a restore, for the Browse list flash. */
    val recentlyRestored = MutableStateFlow<Set<String>>(emptySet())

    init {
        refreshBookkeeping()
    }

    fun refreshBookkeeping() {
        viewModelScope.launch(Dispatchers.IO) {
            val completed = storeMetaDao.get(MemoryStoreMetaKeys.IMPORT_COMPLETED) == "true"
            val processed = storeMetaDao.get(MemoryStoreMetaKeys.IMPORT_WATERMARK)?.toIntOrNull() ?: 0
            val total = runCatching { legacyMemoryDao.countAll() + legacyEpisodeDao.getCount() }.getOrDefault(0)
            _importProgress.value = MemoryGraphRepository.ImportProgress(completed, processed.coerceAtMost(total.coerceAtLeast(processed)), total)
            _budgetToday.value = MemoryBudget.ALL_CATEGORIES.associateWith { budget.callsToday(it) }
        }
    }

    // ---- overview stats derived from the live node subset ----

    fun liveNodes(all: List<MemoryNodeEntity>): List<MemoryNodeEntity> =
        all.filter { it.status == MemStatus.ACTIVE || it.status == MemStatus.PROVISIONAL || it.status == MemStatus.DORMANT || it.status == MemStatus.CLOSED }

    // ---- node sheet ----

    private val _nodeDetail = MutableStateFlow<MemoryGraphRepository.NodeDetail?>(null)
    val nodeDetail: StateFlow<MemoryGraphRepository.NodeDetail?> = _nodeDetail

    fun openNode(nodeId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _nodeDetail.value = graphRepository.getNodeDetail(nodeId)
        }
    }

    fun closeNode() {
        _nodeDetail.value = null
    }

    private fun reloadNodeDetail() {
        val id = _nodeDetail.value?.node?.id ?: return
        openNode(id)
    }

    // ---- user actions ----

    fun setPinned(nodeId: String, pinned: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.setPinned(nodeId, pinned)
        reloadNodeDetail()
    }

    fun forget(nodeId: String) = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.forgetNode(nodeId)
        reloadNodeDetail()
    }

    fun restore(nodeId: String) = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.restoreNode(nodeId)
        recentlyRestored.value = recentlyRestored.value + nodeId
        reloadNodeDetail()
    }

    /** Hand-written fact via the "+ Add memory" FAB — routed through the applier (dedup gate) as a
     *  MANUAL, ACTIVE node. Only available in character view (extraction/manual writes need an owner). */
    fun addManual(content: String, importance: Int, pinned: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val ownerId = assistantId ?: return@launch
        if (content.isBlank()) return@launch
        val result = applier.apply(
            ops = listOf(
                MemoryOp.AddNode(
                    type = MemNodeType.FACT,
                    content = content.trim(),
                    importance = importance.coerceIn(1, 5),
                    confidence = 1f,
                    rationale = "added by you",
                )
            ),
            ctx = MemoryApplyContext(assistantId = ownerId, source = MemSource.MANUAL),
        )
        if (pinned) result.added.forEach { graphRepository.setPinned(it, true) }
    }

    /** Edit = a MANUAL supersede (append-only; the old version stays as history). */
    fun edit(nodeId: String, newContent: String) = viewModelScope.launch(Dispatchers.IO) {
        val ownerId = assistantId ?: nodeDetail.value?.node?.ownerAssistantId ?: return@launch
        if (newContent.isBlank()) return@launch
        applier.apply(
            ops = listOf(
                MemoryOp.UpdateNode(oldId = nodeId, content = newContent.trim(), rationale = "edited by you")
            ),
            ctx = MemoryApplyContext(assistantId = ownerId, source = MemSource.MANUAL),
        )
        reloadNodeDetail()
    }

    fun acceptPromotion(activityId: String) = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.acceptPromotionSuggestion(activityId)
    }

    fun dismissPromotion(activityId: String) = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.dismissPromotionSuggestion(activityId)
    }

    suspend fun getRecentlyForgotten(): List<MemoryNodeEntity> =
        withContext(Dispatchers.IO) { graphRepository.getRecentlyForgotten(scopeId) }

    // ---- export / wipe ----

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        if (isGlobal) graphRepository.exportGlobal() else graphRepository.exportCharacter(assistantId!!)
    }

    suspend fun exportEverythingJson(): String = withContext(Dispatchers.IO) { graphRepository.exportEverything() }

    fun wipeCharacter() = viewModelScope.launch(Dispatchers.IO) {
        assistantId?.let { graphRepository.wipeCharacter(it) }
        refreshBookkeeping()
    }

    fun wipeEverything() = viewModelScope.launch(Dispatchers.IO) {
        graphRepository.wipeEverything()
        refreshBookkeeping()
    }

    // ---- settings (§11) ----

    fun updateGlobalMemory(transform: (me.rerere.rikkahub.data.datastore.MemorySettings) -> me.rerere.rikkahub.data.datastore.MemorySettings) =
        viewModelScope.launch {
            settingsStore.update { it.copy(memory = transform(it.memory)) }
        }

    fun updateAssistant(transform: (Assistant) -> Assistant) = viewModelScope.launch {
        val id = assistantId ?: return@launch
        settingsStore.update { s ->
            s.copy(assistants = s.assistants.map { if (it.id.toString() == id) transform(it) else it })
        }
    }
}
