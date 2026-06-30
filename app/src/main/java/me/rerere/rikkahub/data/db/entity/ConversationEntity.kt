package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    indices = [
        Index(value = ["assistant_id", "is_pinned", "update_at"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo("assistant_id", defaultValue = "0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
    val assistantId: String,
    @ColumnInfo("title")
    val title: String,
    @ColumnInfo("nodes")
    val nodes: String,
    @ColumnInfo("create_at")
    val createAt: Long,
    @ColumnInfo("update_at")
    val updateAt: Long,
    @ColumnInfo("truncate_index", defaultValue = "-1")
    val truncateIndex: Int,
    @ColumnInfo("suggestions", defaultValue = "[]")
    val chatSuggestions: String,
    @ColumnInfo("is_pinned", defaultValue = "0")
    val isPinned: Boolean,
    @ColumnInfo(name = "is_consolidated", defaultValue = "0")
    val isConsolidated: Boolean = false,
    @ColumnInfo(name = "enabled_mode_ids", defaultValue = "[]")
    val enabledModeIds: String = "[]",
    @ColumnInfo(name = "enabled_lorebook_ids", defaultValue = "")
    val enabledLorebookIds: String = "",
    @ColumnInfo(name = "context_summary", defaultValue = "")
    val contextSummary: String = "",
    @ColumnInfo(name = "context_summary_up_to_index", defaultValue = "-1")
    val contextSummaryUpToIndex: Int = -1,
    @ColumnInfo(name = "last_prune_time", defaultValue = "0")
    val lastPruneTime: Long = 0L,
    @ColumnInfo(name = "last_prune_message_count", defaultValue = "0")
    val lastPruneMessageCount: Int = 0,
    @ColumnInfo(name = "last_refresh_time", defaultValue = "0")
    val lastRefreshTime: Long = 0L,
    @ColumnInfo(name = "is_fork", defaultValue = "0")
    val isFork: Boolean = false,
    @ColumnInfo(name = "last_model_id", defaultValue = "")
    val lastModelId: String = "",
)
