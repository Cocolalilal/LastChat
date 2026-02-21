package me.rerere.rikkahub.ui.pages.assistant.detail

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.entity.GraphEpisodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.NodeType
import me.rerere.rikkahub.data.db.entity.TimelineEventEntity
import me.rerere.rikkahub.data.db.entity.PersonProfileEntity
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.deleteChatFiles
import kotlin.uuid.Uuid

private const val TAG = "AssistantDetailVM"

class AssistantDetailVM(
    private val id: String,
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: me.rerere.rikkahub.data.repository.ConversationRepository,
    private val context: Application,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val providerManager: me.rerere.ai.provider.ProviderManager,
    private val graphMemoryRepo: me.rerere.rikkahub.data.repository.GraphMemoryRepository,
    private val memoryAgent: me.rerere.rikkahub.data.ai.memory.MemoryAgent,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Lazily, initialValue = Assistant()
        )

    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery = _memorySearchQuery.asStateFlow()

    fun updateMemorySearchQuery(query: String) {
        _memorySearchQuery.value = query
    }

    val memories = combine(
        memoryRepository.getMemoriesOfAssistantFlow(assistantId.toString()),
        _memorySearchQuery
    ) { coreMemories, query ->
        // Only core memories are shown here — episodic memory is exclusive to Advanced (graph) mode
        // and is displayed via allGraphEpisodes flow in the graph memory explorer
        if (query.isBlank()) {
            coreMemories
        } else {
            coreMemories.filter { it.content.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
    )

    // Current embedding model ID for this assistant (for detecting model mismatch)
    val currentEmbeddingModelId: StateFlow<String> = combine(
        assistant,
        settings
    ) { assistant, settings ->
        (assistant.embeddingModelId ?: settings.embeddingModelId).toString()
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.Lazily, initialValue = ""
    )

    val episodes = chatEpisodeDAO.getEpisodesOfAssistantFlow(assistantId.toString())
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
        )

    // Graph memory stat flows
    val graphNodeCount = graphMemoryRepo.getNodeCountFlow(id)
    val graphEdgeCount = graphMemoryRepo.getEdgeCountFlow(id)
    val graphActiveEventCount = graphMemoryRepo.getActiveEventCountFlow(id)
    val graphEpisodeCount = graphMemoryRepo.getEpisodeCountFlow(id)

    // Graph data browsing flows
    val allNodes = graphMemoryRepo.getAllNodesFlow(id)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allEdges = graphMemoryRepo.getAllEdgesFlow(id)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTimelineEvents = graphMemoryRepo.getAllEventsFlow(id)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allGraphEpisodes = graphMemoryRepo.getAllEpisodesFlow(id)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun clearGraphMemory() {
        viewModelScope.launch {
            graphMemoryRepo.deleteAllGraphData(id)
        }
    }

    fun deleteGraphNode(nodeId: Int) {
        viewModelScope.launch {
            graphMemoryRepo.deleteNode(nodeId)
        }
    }

    fun deleteGraphEdge(edgeId: Int) {
        viewModelScope.launch {
            graphMemoryRepo.deleteEdge(edgeId)
        }
    }

    fun deleteTimelineEvent(eventId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.deleteTimelineEvent(eventId)
            }
        }
    }

    fun deleteEpisode(episodeId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.deleteEpisode(episodeId)
            }
        }
    }

    fun updateNode(node: MemoryNodeEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.updateNode(node)
            }
        }
    }

    fun updateEdge(edge: MemoryEdgeEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.updateEdge(edge)
            }
        }
    }

    fun updateTimelineEvent(event: TimelineEventEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.updateTimelineEvent(event)
            }
        }
    }

    fun updateEpisode(episode: GraphEpisodeEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.updateEpisode(episode)
            }
        }
    }

    private val _graphProcessing = MutableStateFlow(false)
    val graphProcessing = _graphProcessing.asStateFlow()

    fun processTextIntoGraph(text: String) {
        viewModelScope.launch {
            _graphProcessing.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    memoryAgent.processExchange(
                        assistantId = id,
                        userMessage = text,
                        assistantReply = "(Manual graph memory ingestion)",
                        isManualIngestion = true, // Skip episodic memory creation
                    )
                }
                if (result.error != null) {
                    _snackbarMessage.value = "Graph extraction failed: ${result.error}"
                } else if (result.isEmpty) {
                    _snackbarMessage.value = "No meaningful information extracted from this text"
                } else {
                    _snackbarMessage.value = "Extracted ${result.nodesUpserted} entities, ${result.edgesCreated} relations"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process text into graph", e)
                _snackbarMessage.value = "Failed: ${e.message}"
            } finally {
                _graphProcessing.value = false
            }
        }
    }

    val episodeStats = combine(episodes, memories) { episodeList, memoryList ->
        val totalEpisodes = episodeList.size
        val avgSig = if (totalEpisodes > 0) {
            episodeList.sumOf { it.significance }.toDouble() / totalEpisodes
        } else {
            0.0
        }
        val coreCount = memoryList.count { it.type == 0 } // 0 is CORE
        EpisodeStats(totalEpisodes, avgSig, coreCount)
    }.stateIn(viewModelScope, SharingStarted.Lazily, EpisodeStats(0, 0.0, 0))

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
        )

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            // First, update the global tags list
            val currentSettings = settingsStore.settingsFlow.value
            settingsStore.update(
                settings = currentSettings.copy(
                    assistantTags = tags
                )
            )
            
            // Then, update this assistant's tags
            val updatedAssistant = assistant.value.copy(tags = tagIds.toList())
            val latestSettings = settingsStore.settingsFlow.value
            settingsStore.update(
                settings = latestSettings.copy(
                    assistants = latestSettings.assistants.map {
                        if (it.id == updatedAssistant.id) updatedAssistant else it
                    }
                )
            )
            
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            
            // Now cleanup unused tags using the fresh state
            cleanupUnusedTagsInternal()
        }
    }

    private suspend fun cleanupUnusedTagsInternal() {
        // Use fresh settings after all updates
        val settings = settingsStore.settingsFlow.value
        val validTagIds = settings.assistantTags.map { it.id }.toSet()

        // 清理 assistant 中的无效 tag id
        val cleanedAssistants = settings.assistants.map { assistant ->
            val validTags = assistant.tags.filter { tagId ->
                validTagIds.contains(tagId)
            }
            if (validTags.size != assistant.tags.size) {
                assistant.copy(tags = validTags)
            } else {
                assistant
            }
        }

        // 获取清理后的 assistant 中使用的 tag id
        val usedTagIds = cleanedAssistants.flatMap { it.tags }.toSet()

        // 清理未使用的 tags
        val cleanedTags = settings.assistantTags.filter { tag ->
            usedTagIds.contains(tag.id)
        }

        // 检查是否需要更新
        val needUpdateAssistants = cleanedAssistants != settings.assistants
        val needUpdateTags = cleanedTags.size != settings.assistantTags.size

        if (needUpdateAssistants || needUpdateTags) {
            settingsStore.update(
                settings = settings.copy(
                    assistants = cleanedAssistants,
                    assistantTags = cleanedTags
                )
            )
            Log.d(TAG, "cleanupUnusedTags: removed ${settings.assistantTags.size - cleanedTags.size} unused tags")
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            cleanupUnusedTagsInternal()
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            val currentSettings = settingsStore.settingsFlow.value
            val oldAssistant = currentSettings.assistants.find { it.id == assistant.id }
            if (oldAssistant != null) {
                checkAvatarDelete(old = oldAssistant, new = assistant)
                checkBackgroundDelete(old = oldAssistant, new = assistant)
                // Auto-seed person profiles when graph memory is first enabled
                if (!oldAssistant.useGraphMemory && assistant.useGraphMemory) {
                    seedDefaultProfiles()
                }
            }
            settingsStore.update(
                settings = currentSettings.copy(
                    assistants = currentSettings.assistants.map {
                        if (it.id == assistant.id) {
                            assistant
                        } else {
                            it
                        }
                    })
            )
        }
    }

    fun addMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.addMemory(
                assistantId = assistantId.toString(),
                content = memory.content
            )
        }
    }

    fun updateMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            if (memory.id < 0) {
                memoryRepository.updateEpisodeContent(id = -memory.id, content = memory.content)
            } else {
                memoryRepository.updateContent(id = memory.id, content = memory.content)
            }
        }
    }

    fun deleteMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            if (memory.id > 0) {
                memoryRepository.deleteMemory(id = memory.id)
            }
        }
    }

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private val _embeddingProgress = MutableStateFlow<EmbeddingProgress?>(null)
    val embeddingProgress = _embeddingProgress.asStateFlow()

    // Check if any memories need embedding (just checks if embedding exists, cache handles model switching)
    val needsEmbeddingRegeneration: StateFlow<Boolean> = memories.map { memories ->
        memories.any { memory -> !memory.hasEmbedding }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = false
    )

    private val _retrievalResults = MutableStateFlow<List<Pair<AssistantMemory, Float>>>(emptyList())
    val retrievalResults = _retrievalResults.asStateFlow()

    fun testRetrieval(query: String) {
        viewModelScope.launch {
            try {
                val currentAssistant = assistant.value
                val threshold = if (currentAssistant.ragSimilarityThreshold > 0f) {
                    currentAssistant.ragSimilarityThreshold
                } else {
                    0.0f // Show all for debugging
                }
                val limit = if (currentAssistant.ragLimit > 0) {
                    currentAssistant.ragLimit
                } else {
                    10 // Default for debugging
                }
                
                val results = memoryRepository.retrieveRelevantMemoriesWithScores(
                    assistantId = assistantId.toString(),
                    query = query,
                    limit = limit,
                    similarityThreshold = threshold,
                    includeCore = currentAssistant.ragIncludeCore,
                    includeEpisodes = currentAssistant.ragIncludeEpisodes
                )
                _retrievalResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "Failed to test retrieval", e)
                _snackbarMessage.value = "Retrieval failed: ${e.message}"
            }
        }
    }

    fun clearRetrievalResults() {
        _retrievalResults.value = emptyList()
    }

    fun regenerateEmbeddings() {
        viewModelScope.launch {
            try {
                _embeddingProgress.value = EmbeddingProgress(0, 1, true)
                
                val (success, failure) = memoryRepository.regenerateEmbeddings(
                    assistantId = assistantId.toString(),
                    onProgress = { current, total ->
                        _embeddingProgress.value = EmbeddingProgress(current, total, true)
                    }
                )
                
                _embeddingProgress.value = null
                if (failure > 0) {
                    _snackbarMessage.value = "Completed: $success success, $failure failed. Check your API key or Model settings."
                } else if (success > 0) {
                    _snackbarMessage.value = "Successfully generated $success embeddings."
                } else {
                    _snackbarMessage.value = "No embeddings needed regeneration."
                }
                Log.i(TAG, "Regenerated embeddings: $success success, $failure failed")
            } catch (e: Exception) {
                _embeddingProgress.value = null
                _snackbarMessage.value = "Error: ${e.message}"
                Log.e(TAG, "Failed to regenerate embeddings", e)
            }
        }
    }

    fun consolidateMemories(isFullScan: Boolean) {
        val request = androidx.work.OneTimeWorkRequestBuilder<me.rerere.rikkahub.service.MemoryConsolidationWorker>()
            .setInputData(
                androidx.work.workDataOf("FULL_SCAN" to isFullScan)
            )
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(request)
        _snackbarMessage.value = "Memory consolidation started (Full Scan: $isFullScan)"
    }

    suspend fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            context.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    suspend fun checkBackgroundDelete(old: Assistant, new: Assistant) {
        val oldBackground = old.background
        val newBackground = new.background

        if (oldBackground != null && oldBackground != newBackground) {
            try {
                val oldUri = oldBackground.toUri()
                if (oldUri.scheme == "content" || oldUri.scheme == "file") {
                    context.deleteChatFiles(listOf(oldUri))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete background file: $oldBackground", e)
            }
        }
    }

    // Token Estimation Logic
    fun estimateTokens(text: String): Int = text.length / 4

    val averageMessageLength = conversationRepository.getAverageMessageLength(assistantId)
        .stateIn(viewModelScope, SharingStarted.Lazily, 100)

    val averageMemoryLength = memoryRepository.getAverageMemoryLength(assistantId.toString())
        .stateIn(viewModelScope, SharingStarted.Lazily, 150)

    val systemPromptTokenCount = assistant.map {
        estimateTokens(it.systemPrompt)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val smartMinTokenUsage = combine(
        assistant,
        averageMessageLength,
        averageMemoryLength
    ) { assistant, avgMsgLen, avgMemLen ->
        val sysPrompt = estimateTokens(assistant.systemPrompt)
        
        // Dynamic estimates based on history
        val avgMsgTokens = (avgMsgLen / 4).coerceAtLeast(10)
        val avgMemTokens = (avgMemLen / 4).coerceAtLeast(10)

        val minHistory = avgMsgTokens * 2 // At least 2 messages
        val minMemory = if (assistant.enableMemory) avgMemTokens * 2 else 0 // At least 2 memories
        val buffer = 200
        sysPrompt + minHistory + minMemory + buffer
    }.stateIn(viewModelScope, SharingStarted.Lazily, 1000)

    val estimatedMemoryCapacity = combine(
        assistant,
        smartMinTokenUsage,
        averageMemoryLength
    ) { assistant, minUsage, avgMemLen ->
        val total = assistant.maxTokenUsage
        val available = (total - minUsage).coerceAtLeast(0)
        val avgMemTokens = (avgMemLen / 4).coerceAtLeast(10)
        
        // If RAG is enabled, how many memories can we fit in the remaining space?
        // This is a rough upper bound for the slider
        (available / avgMemTokens).coerceAtLeast(5) // Minimum 5
    }.stateIn(viewModelScope, SharingStarted.Lazily, 10)

    val estimatedAllocation = combine(
        assistant,
        averageMessageLength,
        averageMemoryLength
    ) { assistant, avgMsgLen, avgMemLen ->
        val total = assistant.maxTokenUsage
        val sysPrompt = estimateTokens(assistant.systemPrompt)
        val remaining = total - sysPrompt

        if (remaining <= 0) {
            "System prompt consumes all tokens!"
        } else {
            val avgMsgTokens = (avgMsgLen / 4).coerceAtLeast(10)
            val avgMemTokens = (avgMemLen / 4).coerceAtLeast(10)

            // Calculate how many messages OR memories can fit in the remaining space
            val estHistoryMsgs = remaining / avgMsgTokens
            val estMemories = remaining / avgMemTokens

            "Est. History: ~$estHistoryMsgs msgs or Memories: ~$estMemories"
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Calculating...")

    // Validation for Export UI
    val hasMemories = combine(memories, episodes) { m, e ->
        m.isNotEmpty() || e.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val hasLorebooks = assistant.map { 
        it.enabledLorebookIds.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ─── Person Profile Operations ────────────────────────────────────────

    fun getProfileFlow(nodeId: Int) = graphMemoryRepo.getProfileFlow(nodeId)

    fun updateProfile(profile: PersonProfileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.updateProfile(profile)
            }
        }
    }

    fun getPersonRelationships(nodeId: Int, callback: (List<MemoryEdgeEntity>) -> Unit) {
        viewModelScope.launch {
            val edges = withContext(Dispatchers.IO) {
                graphMemoryRepo.getPersonRelationships(nodeId)
            }
            callback(edges)
        }
    }

    fun getPersonalityNodes(nodeId: Int, callback: (List<MemoryNodeEntity>) -> Unit) {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                graphMemoryRepo.getPersonalityNodes(nodeId)
            }
            callback(nodes)
        }
    }

    fun getPhysicalAttributeNodes(nodeId: Int, callback: (List<MemoryNodeEntity>) -> Unit) {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                graphMemoryRepo.getPhysicalAttributeNodes(nodeId)
            }
            callback(nodes)
        }
    }

    fun getOtherInfoNodes(nodeId: Int, callback: (List<MemoryNodeEntity>) -> Unit) {
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                graphMemoryRepo.getOtherInfoNodes(nodeId)
            }
            callback(nodes)
        }
    }

    fun addPersonRelationship(sourceNodeId: Int, targetNodeId: Int, relationType: String, description: String = "") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.upsertEdge(MemoryEdgeEntity(
                    assistantId = id,
                    sourceNodeId = sourceNodeId,
                    targetNodeId = targetNodeId,
                    relationType = relationType,
                    description = description,
                ))
            }
        }
    }

    fun removePersonRelationship(edgeId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                graphMemoryRepo.deleteEdge(edgeId)
            }
        }
    }

    /**
     * Get all person-type nodes for this assistant (for relationship picker).
     */
    val personNodes = allNodes.map { nodes ->
        nodes.filter { it.nodeType == NodeType.PERSON }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * All person profiles for this assistant, keyed by nodeId for quick lookup.
     */
    val allProfiles = graphMemoryRepo.getAllProfilesFlow(id)
        .map { profiles -> profiles.associateBy { it.nodeId } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /**
     * Seed default User + Character profiles when graph memory is first enabled.
     * Called from update() when useGraphMemory transitions false → true.
     */
    fun seedDefaultProfiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val currentSettings = settingsStore.settingsFlow.value
                val currentAssistant = assistant.value
                graphMemoryRepo.seedDefaultProfiles(
                    assistantId = id,
                    userName = currentSettings.displaySetting.userNickname,
                    characterName = currentAssistant.name,
                )
            }
        }
    }

    fun calculateAge(birthYear: Int?, birthMonth: Int? = null, birthDay: Int? = null): Int? =
        graphMemoryRepo.calculateAge(birthYear, birthMonth, birthDay)
}

data class EmbeddingProgress(
    val current: Int,
    val total: Int,
    val isRunning: Boolean
)

data class EpisodeStats(
    val totalEpisodes: Int,
    val averageSignificance: Double,
    val coreMemoryCount: Int
)
