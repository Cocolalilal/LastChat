package me.rerere.rikkahub.ui.pages.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class TimeLabel {
    EARLY_BIRD,      // 5am-11am
    DAYTIME_CHATTER, // 11am-6pm
    NIGHT_OWL        // 6pm-5am
}

data class HeatmapDay(
    val date: LocalDate,
    val count: Int
)

class MenuVM(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    val currentAssistant = settingsStore.settingsFlow
        .map { it.getCurrentAssistant() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<MenuStats> = combine(
        conversationRepository.getDailyActivityDatesFlow(),
        conversationRepository.getUsageStatsFlow(),
        conversationRepository.getAllDailyActivityFlow()
    ) { distinctDates, usageStats, allActivity ->
        // Daily Chat Streak
        val streak = calculateStreak(distinctDates)

        // Build heatmap data for the last 12 months
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val parsedActivity = allActivity.mapNotNull { entity ->
            try {
                LocalDate.parse(entity.date, formatter) to entity.messageCount
            } catch (_: Exception) {
                null
            }
        }
        val activityMap = parsedActivity.toMap()
        val heatmapStartDate = today.minusMonths(11)
            .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        
        val heatmapData = generateSequence(heatmapStartDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { date ->
                HeatmapDay(
                    date = date,
                    count = activityMap[date] ?: 0
                )
            }
            .toList()

        MenuStats(
            dailyChatStreak = streak,
            usageStats = usageStats ?: UsageStatsEntity(),
            heatmapData = heatmapData
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MenuStats()
        )

    private fun calculateStreak(distinctDates: List<String>): Int {
        if (distinctDates.isEmpty()) return 0
        
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val dates = distinctDates.mapNotNull { 
            try { LocalDate.parse(it, formatter) } catch (e: Exception) { null }
        }.sortedDescending()
        
        if (dates.isEmpty()) return 0
        
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        val startDate = when {
            dates.contains(today) -> today
            dates.contains(yesterday) -> yesterday
            else -> return 0
        }
        
        var streak = 0
        var current = startDate
        
        while (dates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        
        return streak
    }
}

data class MenuStats(
    val dailyChatStreak: Int = 0,
    val usageStats: UsageStatsEntity = UsageStatsEntity(),
    val heatmapData: List<HeatmapDay> = emptyList()
)
