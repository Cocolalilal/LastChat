package me.rerere.rikkahub.data.repository

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class StoryStatus {
    Outlining,
    Writing,
    Paused,
    Completed
}

@Serializable
enum class BeatStatus {
    Queued,
    Writing,
    Done
}

@Serializable
data class StoryCharacter(
    val name: String,
    val role: String = "",
    val description: String = "",
    val motive: String = "",
)

@Serializable
data class StoryBeat(
    val index: Int,
    val title: String,
    val objective: String,
    val targetWords: Int = 1000,
    val wordsWritten: Int = 0,
    val status: BeatStatus = BeatStatus.Queued,
    val steerNote: String? = null,
    val content: String = "",
    val summary: String = "",
)

@Serializable
data class StoryBible(
    val title: String,
    val premise: String,
    val genre: String = "Sci-Fi",
    val tone: String = "Gritty",
    val styleGuidelines: String = "Immersive, visceral, high narrative momentum.",
    val characters: List<StoryCharacter> = emptyList(),
    val worldRules: List<String> = emptyList(),
    val beats: List<StoryBeat> = emptyList(),
)

@Serializable
data class StoryProject(
    val id: String,
    val title: String,
    val premise: String,
    val genre: String = "Sci-Fi",
    val tone: String = "Gritty",
    val status: StoryStatus = StoryStatus.Outlining,
    val currentBeatIndex: Int = 0,
    val totalWords: Int = 0,
    val targetWords: Int = 5000,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val architectModelId: Uuid? = null,
    val proseModelId: Uuid? = null,
    val bible: StoryBible? = null,
)
