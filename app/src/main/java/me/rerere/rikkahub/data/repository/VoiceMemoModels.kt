package me.rerere.rikkahub.data.repository

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class VoiceMemoMetadata(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val isTranscribed: Boolean = false,
    val isSummarized: Boolean = false,
    val transcript: String? = null,
    val summary: String? = null,
    val bulletPoints: List<String> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val transcriptSegments: List<VoiceTranscriptSegment> = emptyList(),
)

@Serializable
data class VoiceTranscriptSegment(
    val timestamp: String,
    val text: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
)

data class VoiceMemoItem(
    val id: String,
    val title: String,
    val audioFile: File,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val isTranscribed: Boolean,
    val isSummarized: Boolean,
    val transcript: String?,
    val summary: String?,
    val bulletPoints: List<String>,
    val actionItems: List<String>,
    val transcriptSegments: List<VoiceTranscriptSegment>,
)
