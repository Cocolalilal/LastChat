package me.rerere.rikkahub.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "VoiceMemoRepository"

class VoiceMemoRepository(
    private val context: Context,
    private val workspaceDao: WorkspaceDAO,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _recordingsFlow = MutableStateFlow<List<VoiceMemoItem>>(emptyList())
    val recordingsFlow: StateFlow<List<VoiceMemoItem>> = _recordingsFlow.asStateFlow()

    init {
        refresh()
    }

    fun getRecordingsDir(): File {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "LastChat")
        if (!publicDir.exists()) {
            val created = publicDir.mkdirs()
            if (!created && !publicDir.exists()) {
                val internalDir = File(context.filesDir, "recordings")
                if (!internalDir.exists()) internalDir.mkdirs()
                return internalDir
            }
        }
        return publicDir
    }

    fun refresh() {
        scope.launch {
            _recordingsFlow.value = scanRecordings()
        }
    }

    suspend fun getRecordings(): List<VoiceMemoItem> = withContext(Dispatchers.IO) {
        scanRecordings()
    }

    private suspend fun scanRecordings(): List<VoiceMemoItem> = withContext(Dispatchers.IO) {
        val dir = getRecordingsDir()
        if (!dir.exists()) return@withContext emptyList()

        val audioExtensions = setOf("m4a", "aac", "mp3", "wav", "ogg")
        val audioFiles = dir.listFiles { file ->
            file.isFile && file.extension.lowercase() in audioExtensions
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        audioFiles.map { audioFile ->
            val id = audioFile.nameWithoutExtension
            val metaFile = File(dir, "$id.json")
            val meta = if (metaFile.exists()) {
                runCatching {
                    JsonInstant.decodeFromString<VoiceMemoMetadata>(metaFile.readText())
                }.getOrNull()
            } else null

            val durationMs = meta?.durationMs?.takeIf { it > 0 } ?: extractAudioDuration(audioFile)
            val defaultTitle = runCatching {
                val date = Date(audioFile.lastModified())
                SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(date)
            }.getOrDefault(audioFile.nameWithoutExtension)

            VoiceMemoItem(
                id = id,
                title = meta?.title?.ifBlank { defaultTitle } ?: defaultTitle,
                audioFile = audioFile,
                createdAt = meta?.createdAt ?: audioFile.lastModified(),
                durationMs = durationMs,
                sizeBytes = audioFile.length(),
                isTranscribed = meta?.isTranscribed ?: false,
                isSummarized = meta?.isSummarized ?: false,
                transcript = meta?.transcript,
                summary = meta?.summary,
                bulletPoints = meta?.bulletPoints ?: emptyList(),
                actionItems = meta?.actionItems ?: emptyList(),
                transcriptSegments = meta?.transcriptSegments ?: emptyList(),
            )
        }
    }

    suspend fun saveTranscript(
        id: String,
        transcript: String,
        segments: List<VoiceTranscriptSegment> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        val dir = getRecordingsDir()
        val metaFile = File(dir, "$id.json")
        val existing = if (metaFile.exists()) {
            runCatching { JsonInstant.decodeFromString<VoiceMemoMetadata>(metaFile.readText()) }.getOrNull()
        } else null

        val audioFile = File(dir, "$id.m4a")
        val updated = VoiceMemoMetadata(
            id = id,
            title = existing?.title ?: id,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            durationMs = existing?.durationMs ?: extractAudioDuration(audioFile),
            sizeBytes = audioFile.length(),
            isTranscribed = true,
            isSummarized = existing?.isSummarized ?: false,
            transcript = transcript,
            summary = existing?.summary,
            bulletPoints = existing?.bulletPoints ?: emptyList(),
            actionItems = existing?.actionItems ?: emptyList(),
            transcriptSegments = segments,
        )

        metaFile.writeText(JsonInstant.encodeToString(updated))
        refresh()

        // Sync to workspace
        val item = _recordingsFlow.value.firstOrNull { it.id == id }
        if (item != null) {
            syncToWorkspace(item.copy(isTranscribed = true, transcript = transcript, transcriptSegments = segments))
        }
    }

    suspend fun saveAnalysis(
        id: String,
        title: String? = null,
        summary: String? = null,
        bulletPoints: List<String>? = null,
        actionItems: List<String>? = null,
    ) = withContext(Dispatchers.IO) {
        val dir = getRecordingsDir()
        val metaFile = File(dir, "$id.json")
        val existing = if (metaFile.exists()) {
            runCatching { JsonInstant.decodeFromString<VoiceMemoMetadata>(metaFile.readText()) }.getOrNull()
        } else null

        val audioFile = File(dir, "$id.m4a")
        val updated = VoiceMemoMetadata(
            id = id,
            title = title?.ifBlank { existing?.title ?: id } ?: existing?.title ?: id,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            durationMs = existing?.durationMs ?: extractAudioDuration(audioFile),
            sizeBytes = audioFile.length(),
            isTranscribed = existing?.isTranscribed ?: false,
            isSummarized = summary != null || existing?.isSummarized == true,
            transcript = existing?.transcript,
            summary = summary ?: existing?.summary,
            bulletPoints = bulletPoints ?: existing?.bulletPoints ?: emptyList(),
            actionItems = actionItems ?: existing?.actionItems ?: emptyList(),
            transcriptSegments = existing?.transcriptSegments ?: emptyList(),
        )

        metaFile.writeText(JsonInstant.encodeToString(updated))
        refresh()

        // Sync to workspace
        val item = _recordingsFlow.value.firstOrNull { it.id == id }
        if (item != null) {
            syncToWorkspace(
                item.copy(
                    title = updated.title,
                    isSummarized = updated.isSummarized,
                    summary = updated.summary,
                    bulletPoints = updated.bulletPoints,
                    actionItems = updated.actionItems,
                )
            )
        }
    }

    suspend fun deleteRecording(item: VoiceMemoItem): Boolean = withContext(Dispatchers.IO) {
        val dir = getRecordingsDir()
        val metaFile = File(dir, "${item.id}.json")
        val audioDeleted = item.audioFile.delete()
        if (metaFile.exists()) metaFile.delete()

        // Also clean up from workspace if present
        runCatching {
            val workspaceTranscriptsDir = getWorkspaceTranscriptsDir()
            if (workspaceTranscriptsDir != null) {
                File(workspaceTranscriptsDir, "${item.id}.md").delete()
                File(workspaceTranscriptsDir, "${item.id}.json").delete()
                updateIndexJson(workspaceTranscriptsDir)
            }
        }

        refresh()
        audioDeleted
    }

    suspend fun syncToWorkspace(item: VoiceMemoItem) = withContext(Dispatchers.IO) {
        runCatching {
            val transcriptsDir = getWorkspaceTranscriptsDir() ?: return@runCatching
            if (!transcriptsDir.exists()) transcriptsDir.mkdirs()

            // 1. Write metadata JSON
            val jsonFile = File(transcriptsDir, "${item.id}.json")
            val meta = VoiceMemoMetadata(
                id = item.id,
                title = item.title,
                createdAt = item.createdAt,
                durationMs = item.durationMs,
                sizeBytes = item.sizeBytes,
                isTranscribed = item.isTranscribed,
                isSummarized = item.isSummarized,
                transcript = item.transcript,
                summary = item.summary,
                bulletPoints = item.bulletPoints,
                actionItems = item.actionItems,
                transcriptSegments = item.transcriptSegments,
            )
            jsonFile.writeText(JsonInstant.encodeToString(meta))

            // 2. Write readable Markdown
            val mdFile = File(transcriptsDir, "${item.id}.md")
            val mdContent = buildString {
                appendLine("# ${item.title}")
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))
                appendLine("Date: $dateStr • Duration: ${item.durationMs / 1000}s • Audio: ${item.audioFile.name}")
                appendLine()
                if (!item.summary.isNullOrBlank()) {
                    appendLine("## Executive Summary")
                    appendLine(item.summary)
                    appendLine()
                }
                if (item.bulletPoints.isNotEmpty()) {
                    appendLine("## Key Bullet Points")
                    item.bulletPoints.forEach { pt -> appendLine("- $pt") }
                    appendLine()
                }
                if (item.actionItems.isNotEmpty()) {
                    appendLine("## Action Items")
                    item.actionItems.forEach { act -> appendLine("- [ ] $act") }
                    appendLine()
                }
                if (!item.transcript.isNullOrBlank()) {
                    appendLine("## Full Transcript")
                    if (item.transcriptSegments.isNotEmpty()) {
                        item.transcriptSegments.forEach { seg ->
                            appendLine("[${seg.timestamp}] ${seg.text}")
                        }
                    } else {
                        appendLine(item.transcript)
                    }
                }
            }
            mdFile.writeText(mdContent)

            // 3. Update master index catalog
            updateIndexJson(transcriptsDir)
        }.onFailure { Log.e(TAG, "Failed to sync voice memo to workspace", it) }
    }

    private suspend fun getWorkspaceTranscriptsDir(): File? = withContext(Dispatchers.IO) {
        val workspace = workspaceDao.getAll().firstOrNull() ?: return@withContext null
        File(context.filesDir, "workspaces/${workspace.root}/files/transcripts")
    }

    private fun updateIndexJson(transcriptsDir: File) {
        runCatching {
            val jsonFiles = transcriptsDir.listFiles { f -> f.isFile && f.extension == "json" && f.name != "index.json" } ?: return
            val catalog = jsonFiles.mapNotNull { f ->
                runCatching {
                    val m = JsonInstant.decodeFromString<VoiceMemoMetadata>(f.readText())
                    mapOf(
                        "id" to m.id,
                        "title" to m.title,
                        "createdAt" to m.createdAt,
                        "durationSeconds" to (m.durationMs / 1000),
                        "summary" to (m.summary ?: ""),
                        "path" to "/workspace/transcripts/${m.id}.md"
                    )
                }.getOrNull()
            }.sortedByDescending { (it["createdAt"] as? Long) ?: 0L }

            val indexFile = File(transcriptsDir, "index.json")
            indexFile.writeText(JsonInstant.encodeToString(catalog))
        }
    }

    private fun extractAudioDuration(file: File): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }
}
