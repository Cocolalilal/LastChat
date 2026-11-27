package me.rerere.rikkahub.data.repository

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.db.entity.GenMediaEntity
import kotlin.uuid.Uuid

@Serializable
enum class StudioItemType {
    NOTE,
    TASK_LIST,
    REMINDER,
    IMAGE_NOTE,
}

@Serializable
data class StudioChecklistEntry(
    val id: String = Uuid.random().toString(),
    val text: String,
    val isChecked: Boolean = false,
)

@Serializable
data class StudioHubItem(
    val id: String = Uuid.random().toString(),
    val type: StudioItemType,
    val title: String = "",
    val body: String = "",
    val checklist: List<StudioChecklistEntry> = emptyList(),
    val attachmentPaths: List<String> = emptyList(), // App-private copies under filesDir/studio_attachments/
    val tags: List<String> = emptyList(),
    val colorToken: String? = null, // Subtle M3 tonal tint key ("amber", "sage", "sky", "rose", "lavender")
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isCompleted: Boolean = false,
    val reminderAtMillis: Long? = null,
    val reminderTriggered: Boolean = false,
    val sourceUrl: String? = null, // For web/share clips
    val createdBy: String = "user", // "user" | "assistant" | "quick_ask"
    val briefSummary: String = "", // 1-2 line summary (<= 140 chars) for AI catalog manifest
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class StudioCatalogItemEntry(
    val id: String,
    val type: String,
    val title: String,
    val briefSummary: String,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isCompleted: Boolean = false,
    val reminderAtMillis: Long? = null,
    val createdBy: String = "user",
    val updatedAt: Long = 0L,
    val path: String,
)

@Serializable
data class StudioCatalogCrossRef(
    val id: String,
    val title: String,
    val summary: String,
    val updatedAt: Long,
    val path: String,
)

@Serializable
data class StudioMasterIndex(
    val generatedAt: Long = System.currentTimeMillis(),
    val items: List<StudioCatalogItemEntry> = emptyList(),
    val voiceMemosRef: String = "/workspace/transcripts/index.json",
    val storiesRef: String = "/workspace/studio/stories/index.json",
    val voiceMemos: List<StudioCatalogCrossRef> = emptyList(),
    val stories: List<StudioCatalogCrossRef> = emptyList(),
)

sealed interface StudioFeedEntry {
    val stableKey: String
    val isPinned: Boolean
    val updatedAt: Long

    data class HubItem(val item: StudioHubItem) : StudioFeedEntry {
        override val stableKey: String = "hub:${item.id}"
        override val isPinned: Boolean = item.isPinned
        override val updatedAt: Long = item.updatedAt
    }

    data class VoiceMemo(val memo: VoiceMemoItem) : StudioFeedEntry {
        override val stableKey: String = "voice:${memo.id}"
        override val isPinned: Boolean = false
        override val updatedAt: Long = memo.createdAt
    }

    data class Story(val project: StoryProject) : StudioFeedEntry {
        override val stableKey: String = "story:${project.id}"
        override val isPinned: Boolean = false
        override val updatedAt: Long = project.updatedAt
    }

    data class GeneratedImage(val media: GenMediaEntity) : StudioFeedEntry {
        override val stableKey: String = "image:${media.id}"
        override val isPinned: Boolean = false
        override val updatedAt: Long = media.createAt
    }
}
