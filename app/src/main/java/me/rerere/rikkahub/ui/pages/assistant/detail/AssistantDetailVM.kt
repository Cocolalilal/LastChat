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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
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

    val memories = memoryRepository.getMemoriesOfAssistantFlow(assistantId.toString())
        .stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

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
                val results = memoryRepository.retrieveRelevantMemories(
                    assistantId = assistantId.toString(),
                    query = query,
                    limit = 10,
                    similarityThreshold = 0.0f // Show all for debugging
                )
                // We need the scores, but retrieveRelevantMemories returns List<AssistantMemory>
                // The repository logic calculates scores internally but only returns the items.
                // For debugging, we might need to adjust the repository or just show the items returned.
                // Wait, the repository returns List<AssistantMemory>.
                // I should update the repository to return scores if I want to show them, or just show the order.
                // For now, let's just show the returned items in order.
                _retrievalResults.value = results.map { it to 0.0f } // Score not available in current API
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
