package me.rerere.rikkahub.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.service.StudioReminderScheduler
import me.rerere.rikkahub.service.WorkManagerStudioReminderScheduler
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "StudioHubRepository"

class StudioHubRepository internal constructor(
    private val context: Context,
    private val workspaceDao: WorkspaceDAO,
    private val storyStudioRepository: StoryStudioRepository? = null,
    private val voiceMemoRepository: VoiceMemoRepository? = null,
    private val reminderScheduler: StudioReminderScheduler = WorkManagerStudioReminderScheduler(context),
    autoLoadOnInit: Boolean = true,
) {
    constructor(
        context: Context,
        workspaceDao: WorkspaceDAO,
        storyStudioRepository: StoryStudioRepository? = null,
        voiceMemoRepository: VoiceMemoRepository? = null,
    ) : this(
        context = context,
        workspaceDao = workspaceDao,
        storyStudioRepository = storyStudioRepository,
        voiceMemoRepository = voiceMemoRepository,
        reminderScheduler = WorkManagerStudioReminderScheduler(context),
        autoLoadOnInit = true,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()

    private val _itemsFlow = MutableStateFlow<List<StudioHubItem>>(emptyList())
    val itemsFlow: StateFlow<List<StudioHubItem>> = _itemsFlow.asStateFlow()

    private val _revisionFlow = MutableStateFlow(0)
    val revisionFlow: StateFlow<Int> = _revisionFlow.asStateFlow()

    init {
        if (autoLoadOnInit) {
            scope.launch {
                refreshSync()
                reschedulePendingReminders()
                syncAllToWorkspace()
            }
            scope.launch {
                workspaceDao.listFlow()
                    .map { list -> list.map { it.id to it.root } }
                    .distinctUntilChanged()
                    .collect { workspaces ->
                        if (workspaces.isNotEmpty()) {
                            syncAllToWorkspace()
                        }
                    }
            }
        }
    }

    fun getItemsDir(): File {
        val dir = File(context.filesDir, "studio/items")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAttachmentsDir(): File {
        val dir = File(context.filesDir, "studio_attachments")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun refresh() {
        scope.launch {
            refreshSync()
        }
    }

    suspend fun refreshSync(): List<StudioHubItem> = withContext(Dispatchers.IO) {
        val loaded = loadItemsFromDisk()
        _itemsFlow.value = loaded
        _revisionFlow.update { it + 1 }
        loaded
    }

    suspend fun getItems(): List<StudioHubItem> = withContext(Dispatchers.IO) {
        if (_itemsFlow.value.isNotEmpty()) _itemsFlow.value else refreshSync()
    }

    suspend fun getItem(id: String): StudioHubItem? = withContext(Dispatchers.IO) {
        _itemsFlow.value.firstOrNull { it.id == id } ?: run {
            val file = File(getItemsDir(), "$id.json")
            if (file.exists()) {
                runCatching { JsonInstant.decodeFromString<StudioHubItem>(file.readText()) }.getOrNull()
            } else null
        }
    }

    suspend fun upsertItem(item: StudioHubItem): StudioHubItem = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = getItem(item.id)
        val normalized = item.copy(
            createdAt = existing?.createdAt ?: item.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
            tags = item.tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            reminderTriggered = if (existing?.reminderAtMillis != item.reminderAtMillis) {
                false
            } else {
                item.reminderTriggered
            },
        ).let { candidate ->
            candidate.copy(briefSummary = generateBriefSummary(candidate))
        }

        writeMutex.withLock {
            writeItemAtomic(normalized)
            val current = loadItemsFromDisk()
            _itemsFlow.value = current
            _revisionFlow.update { it + 1 }
            syncItemToWorkspace(normalized, current)
        }

        reminderScheduler.schedule(normalized)
        normalized
    }

    suspend fun createItem(
        type: StudioItemType,
        title: String = "",
        body: String = "",
        checklist: List<StudioChecklistEntry> = emptyList(),
        attachmentSources: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        colorToken: String? = null,
        themeId: String? = null,
        isPinned: Boolean = false,
        reminderAtMillis: Long? = null,
        sourceUrl: String? = null,
        createdBy: String = "user",
        createdByAssistantName: String? = null,
        createdByAssistantId: String? = null,
    ): StudioHubItem = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val copiedAttachments = importAttachments(id = id, sources = attachmentSources)
        val resolvedType = if (copiedAttachments.isNotEmpty() && type == StudioItemType.NOTE) {
            StudioItemType.IMAGE_NOTE
        } else {
            type
        }
        val item = StudioHubItem(
            id = id,
            type = resolvedType,
            title = title.trim(),
            body = body.trim(),
            checklist = checklist,
            attachmentPaths = copiedAttachments,
            tags = tags,
            colorToken = colorToken,
            themeId = themeId,
            isPinned = isPinned,
            reminderAtMillis = reminderAtMillis,
            sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() },
            createdBy = createdBy,
            createdByAssistantName = createdByAssistantName?.trim()?.takeIf { it.isNotBlank() },
            createdByAssistantId = createdByAssistantId?.trim()?.takeIf { it.isNotBlank() },
        )
        upsertItem(item)
    }

    suspend fun importAttachments(
        id: String,
        sources: List<String>,
        startIndex: Int = 0,
    ): List<String> = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext emptyList()
        val attachmentsDir = getAttachmentsDir()
        val copied = mutableListOf<String>()
        sources.forEachIndexed { offset, rawSource ->
            val trimmed = rawSource.trim()
            if (trimmed.isBlank()) return@forEachIndexed

            // Keep files already inside studio_attachments
            val existingFile = runCatching {
                when {
                    trimmed.startsWith("file:") -> trimmed.toUri().toFile()
                    trimmed.startsWith("/") -> File(trimmed)
                    else -> null
                }
            }.getOrNull()
            if (existingFile != null &&
                existingFile.exists() &&
                existingFile.parentFile?.canonicalPath == attachmentsDir.canonicalPath
            ) {
                copied += existingFile.absolutePath
                return@forEachIndexed
            }

            val index = startIndex + offset
            val ext = inferExtension(trimmed)
            val destFile = File(attachmentsDir, "${id}_$index.$ext")
            val success = copySourceToFile(trimmed, destFile)
            if (success && destFile.exists() && destFile.length() > 0L) {
                copied += destFile.absolutePath
            } else {
                destFile.delete()
            }
        }
        copied
    }

    suspend fun toggleChecklistEntry(itemId: String, entryId: String): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        val updatedChecklist = item.checklist.map { entry ->
            if (entry.id == entryId) entry.copy(isChecked = !entry.isChecked) else entry
        }
        return upsertItem(item.copy(checklist = updatedChecklist))
    }

    suspend fun togglePin(itemId: String): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        return upsertItem(item.copy(isPinned = !item.isPinned))
    }

    suspend fun toggleArchive(itemId: String): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        return upsertItem(item.copy(isArchived = !item.isArchived))
    }

    suspend fun toggleCompleted(itemId: String): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        return upsertItem(item.copy(isCompleted = !item.isCompleted))
    }

    suspend fun updateColor(itemId: String, colorToken: String?): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        return upsertItem(item.copy(colorToken = colorToken))
    }

    suspend fun updateAppearance(
        itemId: String,
        colorToken: String?,
        themeId: String?,
    ): StudioHubItem? {
        val item = getItem(itemId) ?: return null
        return upsertItem(item.copy(colorToken = colorToken, themeId = themeId))
    }

    suspend fun markReminderTriggered(itemId: String) {
        val item = getItem(itemId) ?: return
        if (!item.reminderTriggered) {
            upsertItem(item.copy(reminderTriggered = true))
        }
    }

    suspend fun deleteItem(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val existing = getItem(itemId)
        val deleted = writeMutex.withLock {
            val file = File(getItemsDir(), "$itemId.json")
            val wasDeleted = if (file.exists()) file.delete() else false
            existing?.attachmentPaths?.forEach { path ->
                runCatching {
                    val f = File(path)
                    if (f.exists() && f.parentFile?.canonicalPath == getAttachmentsDir().canonicalPath) {
                        f.delete()
                    }
                }
            }
            val current = loadItemsFromDisk()
            _itemsFlow.value = current
            _revisionFlow.update { it + 1 }
            deleteItemFromWorkspace(itemId, current)
            wasDeleted || existing != null
        }
        reminderScheduler.cancel(itemId)
        deleted
    }

    suspend fun reschedulePendingReminders() = withContext(Dispatchers.IO) {
        val items = getItems()
        items.forEach { item ->
            if (item.reminderAtMillis != null && !item.isCompleted && !item.isArchived && !item.reminderTriggered) {
                reminderScheduler.schedule(item)
            }
        }
    }

    suspend fun syncAllToWorkspace() = withContext(Dispatchers.IO) {
        runCatching {
            val studioDir = getWorkspaceStudioDir() ?: return@runCatching
            if (!studioDir.exists()) studioDir.mkdirs()

            val items = loadItemsFromDisk()
            items.forEach { item ->
                writeWorkspaceItemFiles(studioDir, item)
            }
            mirrorStoriesToWorkspace(studioDir)
            writeMasterIndex(studioDir, items)
        }.onFailure { error ->
            Log.e(TAG, "Failed to sync Studio Hub to workspace", error)
        }
    }

    private fun writeItemAtomic(item: StudioHubItem) {
        val dir = getItemsDir()
        val target = File(dir, "${item.id}.json")
        val tmp = File(dir, "${item.id}.json.tmp")
        val payload = JsonInstant.encodeToString(item)
        tmp.writeText(payload)
        if (!tmp.renameTo(target)) {
            target.writeText(payload)
            tmp.delete()
        }
    }

    private fun loadItemsFromDisk(): List<StudioHubItem> {
        val dir = getItemsDir()
        val files = dir.listFiles { file ->
            file.isFile && file.extension == "json" && !file.name.endsWith(".tmp")
        } ?: return emptyList()

        return files.mapNotNull { file ->
            runCatching {
                JsonInstant.decodeFromString<StudioHubItem>(file.readText())
            }.getOrNull()
        }.sortedWith(
            compareByDescending<StudioHubItem> { it.isPinned }
                .thenByDescending { it.updatedAt }
        )
    }

    private suspend fun syncItemToWorkspace(item: StudioHubItem, allItems: List<StudioHubItem>) {
        runCatching {
            val studioDir = getWorkspaceStudioDir() ?: return@runCatching
            if (!studioDir.exists()) studioDir.mkdirs()
            writeWorkspaceItemFiles(studioDir, item)
            mirrorStoriesToWorkspace(studioDir)
            writeMasterIndex(studioDir, allItems)
        }.onFailure { error ->
            Log.e(TAG, "Failed to mirror studio item ${item.id} to workspace", error)
        }
    }

    private suspend fun deleteItemFromWorkspace(itemId: String, allItems: List<StudioHubItem>) {
        runCatching {
            val studioDir = getWorkspaceStudioDir() ?: return@runCatching
            File(studioDir, "$itemId.md").delete()
            File(studioDir, "$itemId.json").delete()
            writeMasterIndex(studioDir, allItems)
        }.onFailure { error ->
            Log.e(TAG, "Failed to delete studio item $itemId from workspace", error)
        }
    }

    private fun writeWorkspaceItemFiles(studioDir: File, item: StudioHubItem) {
        File(studioDir, "${item.id}.json").writeText(JsonInstant.encodeToString(item))
        File(studioDir, "${item.id}.md").writeText(formatItemMarkdown(item))
    }

    private suspend fun mirrorStoriesToWorkspace(studioDir: File) {
        val stories = storyStudioRepository?.projectsFlow?.value.orEmpty()
        if (stories.isEmpty()) return
        val storiesDir = File(studioDir, "stories")
        if (!storiesDir.exists()) storiesDir.mkdirs()
        stories.forEach { project ->
            val md = buildString {
                appendLine("# ${project.title}")
                appendLine("id: ${project.id}")
                appendLine("genre: ${project.genre} | tone: ${project.tone} | status: ${project.status.name}")
                appendLine("total_words: ${project.totalWords}")
                appendLine()
                appendLine("## Premise")
                appendLine(project.premise)
                appendLine()
                project.bible?.beats?.forEach { beat ->
                    appendLine("## Beat ${beat.index + 1}: ${beat.title} [${beat.status.name}]")
                    if (beat.content.isNotBlank()) {
                        appendLine(beat.content)
                    } else if (beat.objective.isNotBlank()) {
                        appendLine("_Objective:_ ${beat.objective}")
                    }
                    appendLine()
                }
            }
            File(storiesDir, "${project.id}.md").writeText(md)
        }
        val storiesIndex = stories.map { p ->
            StudioCatalogCrossRef(
                id = p.id,
                title = p.title,
                summary = p.premise.trim().replace(Regex("\\s+"), " ").take(120),
                updatedAt = p.updatedAt,
                path = "/workspace/studio/stories/${p.id}.md",
            )
        }
        File(storiesDir, "index.json").writeText(JsonInstant.encodeToString(storiesIndex))
    }

    private suspend fun writeMasterIndex(studioDir: File, items: List<StudioHubItem>) {
        val voiceMemos = voiceMemoRepository?.recordingsFlow?.value.orEmpty().map { memo ->
            StudioCatalogCrossRef(
                id = memo.id,
                title = memo.title,
                summary = (memo.summary ?: memo.transcript ?: "").trim().replace(Regex("\\s+"), " ").take(120),
                updatedAt = memo.createdAt,
                path = "/workspace/transcripts/${memo.id}.md",
            )
        }
        val stories = storyStudioRepository?.projectsFlow?.value.orEmpty().map { story ->
            StudioCatalogCrossRef(
                id = story.id,
                title = story.title,
                summary = story.premise.trim().replace(Regex("\\s+"), " ").take(120),
                updatedAt = story.updatedAt,
                path = "/workspace/studio/stories/${story.id}.md",
            )
        }
        val index = StudioMasterIndex(
            generatedAt = System.currentTimeMillis(),
            items = items.map { item ->
                StudioCatalogItemEntry(
                    id = item.id,
                    type = item.type.name,
                    title = item.title,
                    briefSummary = item.briefSummary.ifBlank { generateBriefSummary(item) },
                    tags = item.tags,
                    isPinned = item.isPinned,
                    isArchived = item.isArchived,
                    isCompleted = item.isCompleted,
                    reminderAtMillis = item.reminderAtMillis,
                    createdBy = item.createdBy,
                    updatedAt = item.updatedAt,
                    path = "/workspace/studio/${item.id}.md",
                )
            },
            voiceMemos = voiceMemos,
            stories = stories,
        )
        File(studioDir, "index.json").writeText(JsonInstant.encodeToString(index))
    }

    private suspend fun getWorkspaceStudioDir(): File? {
        val workspace = workspaceDao.getAll().firstOrNull() ?: return null
        return File(context.filesDir, "workspaces/${workspace.root}/files/studio")
    }

    private fun copySourceToFile(rawSource: String, destFile: File): Boolean {
        return runCatching {
            when {
                rawSource.startsWith("content://") -> {
                    context.contentResolver.openInputStream(rawSource.toUri())?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching false
                    true
                }

                rawSource.startsWith("file://") -> {
                    val src = rawSource.toUri().toFile()
                    if (!src.exists()) return@runCatching false
                    src.copyTo(destFile, overwrite = true)
                    true
                }

                else -> {
                    val src = File(rawSource)
                    if (!src.exists()) return@runCatching false
                    src.copyTo(destFile, overwrite = true)
                    true
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to copy attachment from $rawSource", error)
            false
        }
    }

    private fun inferExtension(rawSource: String): String {
        val fromUri = runCatching {
            val uri = rawSource.toUri()
            if (uri.scheme == "content") {
                context.contentResolver.getType(uri)
                    ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            } else null
        }.getOrNull()
        if (!fromUri.isNullOrBlank()) return fromUri.lowercase()

        val ext = rawSource.substringBefore('?').substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
        return ext ?: "jpg"
    }

    companion object {
        fun generateBriefSummary(item: StudioHubItem): String {
            val parts = mutableListOf<String>()
            if (item.checklist.isNotEmpty()) {
                val done = item.checklist.count { it.isChecked }
                val total = item.checklist.size
                val openPreview = item.checklist.firstOrNull { !it.isChecked }?.text?.trim()
                parts += if (!openPreview.isNullOrBlank()) {
                    "$done/$total done • Next: $openPreview"
                } else {
                    "$done/$total done"
                }
            }
            if (item.reminderAtMillis != null) {
                val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                val status = if (item.isCompleted) "Done" else "Due ${fmt.format(Date(item.reminderAtMillis))}"
                parts += status
            }
            if (item.attachmentPaths.isNotEmpty()) {
                val count = item.attachmentPaths.size
                parts += if (count == 1) "1 image" else "$count images"
            }
            if (item.body.isNotBlank()) {
                parts += item.body.trim().replace(Regex("\\s+"), " ")
            } else if (!item.sourceUrl.isNullOrBlank()) {
                val host = runCatching { Uri.parse(item.sourceUrl).host }.getOrNull() ?: item.sourceUrl
                parts += "Clip from $host"
            }
            val raw = if (parts.isNotEmpty()) {
                parts.joinToString(" • ")
            } else {
                item.title.ifBlank { item.type.name.lowercase().replace('_', ' ') }
            }
            return if (raw.length <= 140) raw else raw.take(137).trimEnd() + "..."
        }

        fun formatItemMarkdown(item: StudioHubItem): String = buildString {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            appendLine("---")
            appendLine("id: ${item.id}")
            appendLine("type: ${item.type.name}")
            appendLine("pinned: ${item.isPinned}")
            appendLine("archived: ${item.isArchived}")
            appendLine("completed: ${item.isCompleted}")
            appendLine("created_by: ${item.createdBy}")
            if (!item.createdByAssistantName.isNullOrBlank()) {
                appendLine("created_by_assistant: ${item.createdByAssistantName}")
            }
            if (item.tags.isNotEmpty()) {
                appendLine("tags: [${item.tags.joinToString(", ")}]")
            }
            if (item.reminderAtMillis != null) {
                appendLine("reminder_at: ${fmt.format(Date(item.reminderAtMillis))}")
            }
            if (!item.sourceUrl.isNullOrBlank()) {
                appendLine("source_url: ${item.sourceUrl}")
            }
            appendLine("updated_at: ${fmt.format(Date(item.updatedAt))}")
            appendLine("brief_summary: ${item.briefSummary}")
            appendLine("---")
            appendLine()
            appendLine("# ${item.title.ifBlank { "Untitled ${item.type.name.lowercase()}" }}")
            appendLine()
            if (item.body.isNotBlank()) {
                appendLine(item.body.trim())
                appendLine()
            }
            if (item.checklist.isNotEmpty()) {
                appendLine("## Checklist")
                item.checklist.forEach { entry ->
                    val mark = if (entry.isChecked) "x" else " "
                    appendLine("- [$mark] ${entry.text}")
                }
                appendLine()
            }
            if (item.attachmentPaths.isNotEmpty()) {
                appendLine("## Attachments")
                item.attachmentPaths.forEach { path ->
                    appendLine("- $path")
                }
                appendLine()
            }
        }
    }
}
