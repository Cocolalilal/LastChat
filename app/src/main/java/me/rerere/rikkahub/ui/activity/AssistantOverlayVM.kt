package me.rerere.rikkahub.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

/**
 * Drives the digital-assistant overlay as a REAL [ChatService] conversation — the same
 * pipeline the chat page uses — so the overlay gets full multi-turn back-and-forth, tools,
 * MCP, search, tool-approval and streaming for free.
 *
 * One fresh conversation is created per summon (per VM instance). "Open in app" simply
 * deep-links to that same [conversationId]; a ChatService reference keeps it alive across
 * the hand-off, and it's already persisted to the DB.
 *
 * STT/TTS remain owned by the Compose layer (rememberCustomSttState/…TtsState).
 */
class AssistantOverlayVM(
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
) : ViewModel() {

    val overlayAssistantId: Uuid =
        settingsStore.settingsFlow.value.resolveAssistantOverlayAssistant().id

    private val _conversationId = MutableStateFlow<Uuid?>(null)
    val conversationId: StateFlow<Uuid?> = _conversationId

    val errorFlow: SharedFlow<Throwable> get() = chatService.errorFlow

    init {
        viewModelScope.launch {
            val conversation = chatService.createConversation(overlayAssistantId)
            chatService.addConversationReference(conversation.id)
            _conversationId.value = conversation.id
        }
    }

    fun conversationFlow(id: Uuid): StateFlow<Conversation> = chatService.getConversationFlow(id)

    fun generationJobFlow(id: Uuid): Flow<Job?> = chatService.getGenerationJobStateFlow(id)

    fun send(parts: List<UIMessagePart>) {
        val id = _conversationId.value ?: return
        chatService.sendMessage(conversationId = id, content = parts)
    }

    fun handleToolApproval(
        toolCallId: String,
        approved: Boolean,
        reason: String,
        answer: String?,
    ) {
        val id = _conversationId.value ?: return
        viewModelScope.launch {
            chatService.handleToolApproval(
                conversationId = id,
                toolCallId = toolCallId,
                approved = approved,
                reason = reason,
                answer = answer,
            )
        }
    }

    /** Update the overlay's assistant (model, search mode, …) in settings. */
    fun updateOverlayAssistant(transform: (Assistant) -> Assistant) {
        viewModelScope.launch {
            settingsStore.update { s ->
                s.copy(
                    assistants = s.assistants.map { a ->
                        if (a.id == overlayAssistantId) transform(a) else a
                    }
                )
            }
        }
    }

    fun saveConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatService.saveConversation(conversation.id, conversation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _conversationId.value?.let { chatService.removeConversationReference(it) }
    }
}
