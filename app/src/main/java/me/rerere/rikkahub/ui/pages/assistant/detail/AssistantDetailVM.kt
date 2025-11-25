package me.rerere.rikkahub.ui.pages.assistant.detail

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val context: Application,
    private val chatEpisodeDAO: ChatEpisodeDAO,
    private val providerManager: me.rerere.ai.provider.ProviderManager,
) : ViewModel() {
    private val assistantId = Uuid.parse(id)

    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    val mcpServerConfigs = settingsStore
        .settingsFlow.map { settings ->
            settings.mcpServers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val assistant: StateFlow<Assistant> = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistants.find { it.id == assistantId } ?: Assistant()
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Assistant()
        )

    val memories = combine(
        memoryRepository.getMemoriesOfAssistantFlow(assistantId.toString()),
        chatEpisodeDAO.getEpisodesOfAssistantFlow(assistantId.toString())
    ) { coreMemories, episodes ->
        val core = coreMemories
        val episodic = episodes.map { 
            AssistantMemory(
                id = -it.id, // Negative ID to distinguish from core memories
                content = it.content, 
                type = 1, // EPISODIC
                hasEmbedding = it.embedding != null
            ) 
        }
        core + episodic
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
    )

    val episodes = chatEpisodeDAO.getEpisodesOfAssistantFlow(assistantId.toString())
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val episodeStats = combine(episodes, memories) { episodeList, memoryList ->
        val totalEpisodes = episodeList.size
        val avgSig = if (totalEpisodes > 0) {
            episodeList.sumOf { it.significance }.toDouble() / totalEpisodes
        } else {
            0.0
        }
        val coreCount = memoryList.count { it.type == 0 } // 0 is CORE
        EpisodeStats(totalEpisodes, avgSig, coreCount)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EpisodeStats(0, 0.0, 0))

    val providers = settingsStore
        .settingsFlow
        .map { settings ->
            settings.providers
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val tags = settingsStore
        .settingsFlow
        .map { settings ->
            settings.assistantTags
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    private val _isGeneratingGreetings = MutableStateFlow(false)
    val isGeneratingGreetings = _isGeneratingGreetings.asStateFlow()

    fun generateGreetings(assistantId: Uuid) {
        viewModelScope.launch {
            _isGeneratingGreetings.value = true
            runCatching {
                val assistant = assistant.value
                val backgroundModelId = assistant.backgroundModelId ?: return@runCatching
                val providerSetting = providers.value.find { it.models.any { m -> m.id == backgroundModelId } } ?: return@runCatching
                val model = providerSetting.models.find { it.id == backgroundModelId } ?: return@runCatching
                val provider = providerManager.getProviderByType(providerSetting)

                val timeOfDay = listOf("Morning", "Afternoon", "Evening", "Night")
                val newGreetings = mutableMapOf<String, List<String>>()

                val userName = settings.value.displaySetting.userNickname
                val systemPrompt = assistant.systemPrompt

                timeOfDay.forEach { time ->
                    val prompt = "How would you greet $userName at $time in 2 to 4 words? Just respond with the greeting and nothing else"

                    val messages = mutableListOf<me.rerere.ai.ui.UIMessage>()
                    if (systemPrompt.isNotBlank()) {
                        messages.add(me.rerere.ai.ui.UIMessage.system(systemPrompt))
                    }
                    messages.add(me.rerere.ai.ui.UIMessage.user(prompt))

                    val response = provider.generateText(
                        providerSetting = providerSetting,
                        messages = messages,
                        params = me.rerere.ai.provider.TextGenerationParams(
                            model = model,
                            temperature = 0.7f
                        )
                    )
                    
                    val text = response.choices.firstOrNull()?.message?.toText() ?: ""
                    val greetings = text.lines()
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.trim() }
                    
                    newGreetings[time] = greetings
                }

                update(
                    assistant.copy(personalizedGreetings = newGreetings)
                )
                android.widget.Toast.makeText(context, "Greetings generated successfully", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                it.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to generate greetings: ${it.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
            _isGeneratingGreetings.value = false
        }
    }

    fun updateTags(tagIds: List<Uuid>, tags: List<Tag>) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings = settings.copy(
                    assistantTags = tags
                )
            )
            update(
                assistant.value.copy(
                    tags = tagIds.toList()
                )
            )
            Log.d(TAG, "updateTags: ${tagIds.joinToString(",")}")
            cleanupUnusedTags()
        }
    }

    fun cleanupUnusedTags() {
        viewModelScope.launch {
            val settings = settings.value
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
            }
        }
    }

    fun update(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            settingsStore.update(
                settings = settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            checkAvatarDelete(old = it, new = assistant) // 删除旧头像
                            checkBackgroundDelete(old = it, new = assistant) // 删除旧背景
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
            memoryRepository.updateContent(id = memory.id, content = memory.content)
        }
    }

    fun deleteMemory(memory: AssistantMemory) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id = memory.id)
        }
    }

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private val _embeddingProgress = MutableStateFlow<EmbeddingProgress?>(null)
    val embeddingProgress = _embeddingProgress.asStateFlow()

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

    fun checkAvatarDelete(old: Assistant, new: Assistant) {
        if (old.avatar is Avatar.Image && old.avatar != new.avatar) {
            context.deleteChatFiles(listOf(old.avatar.url.toUri()))
        }
    }

    fun checkBackgroundDelete(old: Assistant, new: Assistant) {
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
