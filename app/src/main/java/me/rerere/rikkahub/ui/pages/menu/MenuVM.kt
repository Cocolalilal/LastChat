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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class TimeLabel {
    EARLY_BIRD,
    DAYTIME_CHATTER,
    NIGHT_OWL
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

    val uiState: StateFlow<MenuUiState> = combine(
        conversationRepository.getUsageStatsLast12MonthsFlow(),
        conversationRepository.getAllDailyActivityFlow()
    ) { usageStats, allActivity ->
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val parsedActivity = allActivity.mapNotNull { entity ->
            runCatching { LocalDate.parse(entity.date, formatter) to entity.messageCount }.getOrNull()
        }
        val activityMap = parsedActivity.toMap()
        val strictWindowStartDate = today.withDayOfMonth(1).minusMonths(11)
        val heatmapStartDate = strictWindowStartDate
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val heatmapData = generateSequence(heatmapStartDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { date ->
                HeatmapDay(
                    date = date,
                    count = if (date.isBefore(strictWindowStartDate)) 0 else (activityMap[date] ?: 0)
                )
            }
            .toList()

        classifyMenuUiState(
            MenuStats(
                usageStats = usageStats,
                heatmapData = heatmapData
            )
        )
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MenuUiState.Loading
        )
}

sealed interface MenuUiState {
    data object Loading : MenuUiState

    data class Ready(
        val stats: MenuStats
    ) : MenuUiState

    data class Empty(
        val stats: MenuStats
    ) : MenuUiState
}

data class MenuStats(
    val usageStats: UsageStatsEntity = UsageStatsEntity(),
    val heatmapData: List<HeatmapDay> = emptyList()
)

internal fun classifyMenuUiState(stats: MenuStats): MenuUiState {
    return if (stats.isEmptyState()) {
        MenuUiState.Empty(stats)
    } else {
        MenuUiState.Ready(stats)
    }
}

internal fun MenuStats.isEmptyState(): Boolean {
    return usageStats.totalConversations == 0L &&
        usageStats.totalMessages == 0L &&
        usageStats.inputTokens == 0L &&
        usageStats.outputTokens == 0L &&
        usageStats.cachedTokens == 0L &&
        heatmapData.none { it.count > 0 }
}
