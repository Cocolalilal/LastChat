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
import me.rerere.rikkahub.data.db.entity.DailyActivityEntity
import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import me.rerere.rikkahub.data.repository.ConversationRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class TimeLabel {
    EARLY_BIRD,      // 5am-11am
    DAYTIME_CHATTER, // 11am-6pm
    NIGHT_OWL        // 6pm-5am
}

data class DayMessages(
    val dayLabel: String, // e.g. "Mon", "Tue"
    val count: Int,
    val isWeekend: Boolean
)

data class HeatmapDay(
    val date: LocalDate,
    val count: Int
)

class MenuVM(
    private val conversationRepository: ConversationRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val weekStartDate = LocalDate.now().minusDays(6)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    val currentAssistant = settingsStore.settingsFlow
        .map { it.getCurrentAssistant() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<MenuStats> = combine(
        conversationRepository.getDailyActivityDatesFlow(),
        conversationRepository.getConversationHoursFlow(),
        conversationRepository.getWeeklyActivityFlow(weekStartDate),
        conversationRepository.getUsageStatsFlow(),
        conversationRepository.getAllDailyActivityFlow()
    ) { distinctDates, hours, weeklyEntities, usageStats, allActivity ->
        // Daily Chat Streak
        val streak = calculateStreak(distinctDates)

        // Time Label based on when user chats most
        val timeLabel = calculateTimeLabel(hours)

        // Weekly messages graph - keep rolling 7 days with today on the right
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val entityMap = weeklyEntities.associate { it.date to it.messageCount }
        val weeklyMessages = (0..6).map { dayOffset ->
            val date = today.minusDays((6 - dayOffset).toLong())
            val dayLabel = date.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.SHORT,
                java.util.Locale.getDefault()
            )
            DayMessages(
                dayLabel = dayLabel,
                count = entityMap[date.format(formatter)] ?: 0,
                isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            )
        }

        val thisWeekMessageCount = entityMap
            .filterKeys { dateString ->
                val date = try {
                    LocalDate.parse(dateString, formatter)
                } catch (_: Exception) {
                    null
                }
                date != null && !date.isBefore(monday) && !date.isAfter(sunday)
            }
            .values
            .sum()

        // Build heatmap data from daily activity (last ~5 months)
        val heatmapStartDate = today.minusMonths(5).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val activityMap = allActivity.mapNotNull { entity ->
            try {
                LocalDate.parse(entity.date, formatter) to entity.messageCount
            } catch (_: Exception) {
                null
            }
        }.toMap()
        
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
            timeLabel = timeLabel,
            weeklyMessages = weeklyMessages,
            thisWeekMessageCount = thisWeekMessageCount,
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

    private fun calculateTimeLabel(hours: List<Int>): TimeLabel {
        if (hours.isEmpty()) return TimeLabel.DAYTIME_CHATTER
        
        var earlyBird = 0   // 5am-11am (5-10)
        var daytime = 0     // 11am-6pm (11-17)
        var nightOwl = 0    // 6pm-5am (18-23, 0-4)
        
        for (hour in hours) {
            when (hour) {
                in 5..10 -> earlyBird++
                in 11..17 -> daytime++
                else -> nightOwl++
            }
        }
        
        return when {
            earlyBird >= daytime && earlyBird >= nightOwl -> TimeLabel.EARLY_BIRD
            daytime >= earlyBird && daytime >= nightOwl -> TimeLabel.DAYTIME_CHATTER
            else -> TimeLabel.NIGHT_OWL
        }
    }

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
    val timeLabel: TimeLabel = TimeLabel.DAYTIME_CHATTER,
    val weeklyMessages: List<DayMessages> = emptyList(),
    val thisWeekMessageCount: Int = 0,
    val usageStats: UsageStatsEntity = UsageStatsEntity(),
    val heatmapData: List<HeatmapDay> = emptyList()
)
