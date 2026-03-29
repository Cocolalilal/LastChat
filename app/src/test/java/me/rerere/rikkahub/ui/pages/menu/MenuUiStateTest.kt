package me.rerere.rikkahub.ui.pages.menu

import me.rerere.rikkahub.data.db.entity.UsageStatsEntity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MenuUiStateTest {
    @Test
    fun classifyMenuUiStateReturnsEmptyWhenEverythingIsZero() {
        val state = classifyMenuUiState(
            MenuStats(
                heatmapData = listOf(
                    HeatmapDay(date = LocalDate.of(2026, 3, 20), count = 0)
                )
            )
        )

        assertTrue(state is MenuUiState.Empty)
    }

    @Test
    fun classifyMenuUiStateReturnsReadyWhenUsageExistsWithoutHeatmapCounts() {
        val state = classifyMenuUiState(
            MenuStats(
                usageStats = UsageStatsEntity(
                    totalConversations = 4,
                    totalMessages = 12
                ),
                heatmapData = listOf(
                    HeatmapDay(date = LocalDate.of(2026, 3, 20), count = 0)
                )
            )
        )

        assertTrue(state is MenuUiState.Ready)
    }

    @Test
    fun classifyMenuUiStateReturnsReadyWhenHeatmapHasActivity() {
        val state = classifyMenuUiState(
            MenuStats(
                heatmapData = listOf(
                    HeatmapDay(date = LocalDate.of(2026, 3, 20), count = 3)
                )
            )
        )

        assertTrue(state is MenuUiState.Ready)
    }
}
