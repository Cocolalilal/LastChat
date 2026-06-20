package me.rerere.rikkahub.ui.pages.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.repository.AppStorageRepository
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository

class AssistantVM(
    private val settingsStore: SettingsStore,
    private val memoryRepository: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val appScope: me.rerere.rikkahub.AppScope,
    private val appStorageRepository: AppStorageRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun addAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            val newAssistant = if (assistant.name.isBlank()) {
                assistant.copy(
                    name = "Generical",
                    avatar = Avatar.Resource(me.rerere.rikkahub.R.drawable.default_generical_pfp),
                    enableTimeAwareness = true,
                    systemPrompt = """
                        You are the best generic assistant, called {{char}}. {{char}} is a really nice guy. He doesn't use emojis though. Use the search tool when looking for factual info. You can have opinions if the user asks you for one. 

                        **Context:
                        - You are currently chatting to {{user}}
                        - You are running on {{model_name}}
                        - Date: {{cur_date}}

                        **Additional info:
                        - The UI supports LaTeX rendering
                        - The user is chatting to you trough an app called LastChat
                        - You are an AI/LLM and shouldn't hide this fact
                    """.trimIndent()
                )
            } else {
                assistant
            }
            val seededAssistant = newAssistant.copy(
                maxSearchResultsRetained = newAssistant.maxSearchResultsRetained ?: 10
            )
            settingsStore.update(
                settings.copy(
                    assistants = settings.assistants.plus(seededAssistant)
                )
            )
        }
    }

    private val deletionJobs = java.util.concurrent.ConcurrentHashMap<kotlin.uuid.Uuid, kotlinx.coroutines.Job>()

    fun removeAssistant(assistant: Assistant) {
        // Cancel any existing job for this assistant
        deletionJobs[assistant.id]?.cancel()

        viewModelScope.launch {
            // Optimistic update: Remove from settings immediately
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.filter { it.id != assistant.id }
                )
            }
        }

        // Start delayed deletion of data
        val job = appScope.launch {
            kotlinx.coroutines.delay(4000) // 4 seconds to undo
            memoryRepository.deleteMemoriesOfAssistant(assistant.id.toString())
            conversationRepo.deleteConversationOfAssistant(assistant.id)
            appStorageRepository.deleteFilesIfUnreferenced(assistant.collectMediaFileRefs())
            deletionJobs.remove(assistant.id)
        }
        deletionJobs[assistant.id] = job
    }

    fun undoRemoveAssistant(assistant: Assistant) {
        // Cancel deletion job if it exists
        deletionJobs[assistant.id]?.cancel()
        deletionJobs.remove(assistant.id)

        viewModelScope.launch {
            // Restore to settings
            settingsStore.update { settings ->
                if (settings.assistants.none { it.id == assistant.id }) {
                    settings.copy(
                        assistants = settings.assistants.plus(assistant)
                    )
                } else {
                    settings
                }
            }
        }
    }

    fun copyAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = settings.value
            val copiedAssistant = assistant.copy(
                id = kotlin.uuid.Uuid.random(),
                name = "${assistant.name} (Clone)",
                avatar = if(assistant.avatar is Avatar.Image) Avatar.Dummy else assistant.avatar,
            )
            settingsStore.update(
                settings.copy(
                    assistants = settings.assistants.plus(copiedAssistant)
                )
            )
        }
    }

    fun getMemories(assistant: Assistant) =
        memoryRepository.getMemoriesOfAssistantFlow(assistant.id.toString())
}

private fun Assistant.collectMediaFileRefs(): List<String> {
    return buildList {
        (avatar as? Avatar.Image)?.url?.let(::add)
        background?.let(::add)
    }
}
