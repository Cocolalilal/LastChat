package me.rerere.rikkahub.ui.pages.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class MenuVM(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val stats: StateFlow<MenuStats> = combine(
        conversationRepository.getAllLightConversations(),
        settingsStore.settingsFlow
    ) { conversations, settings ->
        val now = Instant.now()
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)

        val totalConversations = conversations.size
        val monthlyConversations = conversations.count { it.updateAt.isAfter(thirtyDaysAgo) }
        
        val mostActiveAssistantId = conversations
            .groupBy { it.assistantId }
            .maxByOrNull { it.value.size }
            ?.key
            
        val mostActiveAssistantName = mostActiveAssistantId?.let { id ->
            settings.assistants.find { it.id == id }?.name
        } ?: "None"

        MenuStats(
            totalConversations = totalConversations,
            monthlyConversations = monthlyConversations,
            mostActiveAssistantName = mostActiveAssistantName,
            totalAssistants = settings.assistants.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MenuStats()
    )
}

data class MenuStats(
    val totalConversations: Int = 0,
    val monthlyConversations: Int = 0,
    val mostActiveAssistantName: String = "None",
    val totalAssistants: Int = 0
)
