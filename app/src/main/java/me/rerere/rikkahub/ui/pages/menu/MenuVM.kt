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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MenuVM(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val stats: StateFlow<MenuStats> = combine(
        conversationRepository.getAllConversations(),
        conversationRepository.getEpisodeCountFlow(),
        settingsStore.settingsFlow
    ) { conversations, episodeCount, settings ->
        
        // Daily Chat Streak
        val dates = conversations
            .map { it.updateAt.atZone(ZoneId.systemDefault()).toLocalDate() }
            .distinct()
            .sortedDescending()
        
        var streak = 0
        var current: LocalDate? = LocalDate.now()
        
        // Check if chatted today or yesterday to maintain streak
        if (dates.contains(current)) {
            // Streak is active today
        } else if (dates.contains(current!!.minusDays(1))) {
            // Streak is active from yesterday
            current = current!!.minusDays(1)
        } else {
            // Streak broken
            current = null
        }

        if (current != null) {
            while (dates.contains(current)) {
                streak++
                current = current!!.minusDays(1)
            }
        }

        // Avg Messages/Day
        val today = LocalDate.now()
        val totalMessages = conversations.sumOf { conversation ->
            conversation.messageNodes.sumOf { node ->
                node.messages.count { message ->
                    val msgDate = LocalDate.of(
                        message.createdAt.year,
                        message.createdAt.monthNumber,
                        message.createdAt.dayOfMonth
                    )
                    msgDate.isBefore(today)
                }
            }
        }
        val firstDate = conversations.minOfOrNull { it.createAt }
        val daysActive = if (firstDate != null) {
            val firstDateLocalDate = firstDate.atZone(ZoneId.systemDefault()).toLocalDate()
            ChronoUnit.DAYS.between(firstDateLocalDate, today)
        } else 0
        val avgMessagesPerDay = if (daysActive > 0L) {
            totalMessages.toFloat() / daysActive
        } else {
            0f
        }

        // Most Active Assistant
        val mostActiveAssistantId = conversations
            .groupBy { it.assistantId }
            .maxByOrNull { it.value.size }
            ?.key
            
        val mostActiveAssistantName = mostActiveAssistantId?.let { id ->
            settings.assistants.find { it.id == id }?.name
        } ?: "None"

        MenuStats(
            totalConversations = conversations.size,
            totalMemories = episodeCount,
            mostActiveAssistantName = mostActiveAssistantName,
            totalAssistants = settings.assistants.size,
            dailyChatStreak = streak,
            avgMessagesPerDay = avgMessagesPerDay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MenuStats()
    )
}

data class MenuStats(
    val totalConversations: Int = 0,
    val totalMemories: Int = 0,
    val mostActiveAssistantName: String = "None",
    val totalAssistants: Int = 0,
    val dailyChatStreak: Int = 0,
    val avgMessagesPerDay: Float = 0f
)
