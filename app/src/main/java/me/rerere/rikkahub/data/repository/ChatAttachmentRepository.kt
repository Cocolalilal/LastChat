package me.rerere.rikkahub.data.repository

import android.util.Log
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.transformers.OcrStatus
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.ChatAttachmentDao
import me.rerere.rikkahub.data.db.dao.ConversationAttachmentRefDao
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.entity.ChatAttachmentEntity
import me.rerere.rikkahub.data.db.entity.ConversationAttachmentRefEntity
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.ChatAttachmentKind
import me.rerere.rikkahub.data.model.ChatAttachmentOcrStatus
import me.rerere.rikkahub.data.model.ChatAttachmentState
import me.rerere.rikkahub.data.model.ChatStorageSettings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.buildChatAttachmentMetadata
import me.rerere.rikkahub.data.model.chatAttachmentDisplayName
import me.rerere.rikkahub.data.model.chatAttachmentId
import me.rerere.rikkahub.data.model.chatAttachmentKind
import me.rerere.rikkahub.data.model.chatAttachmentMimeHint
import me.rerere.rikkahub.data.model.chatAttachmentOcrText
import me.rerere.rikkahub.data.model.chatAttachmentState
import me.rerere.rikkahub.data.model.chatAttachmentUrl
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.getFileNameFromUri
import okio.Buffer
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

private const val CHAT_UPLOAD_PREFIX = "chat-"
private const val TAG = "ChatAttachmentRepo"

data class ImportedChatFile(
    val uri: Uri,
    val fileName: String,
    val mime: String?,
)

data class ChatAttachmentUsage(
    val id: String,
    val uri: String,
    val displayName: String,
    val mime: String,
    val kind: ChatAttachmentKind,
    val sha256: String,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val referenceCount: Int,
    val lastUsedAt: Long?,
    val deleted: Boolean,
    val ocrStatus: ChatAttachmentOcrStatus,
)

data class ChatStorageOverview(
    val totalCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val duplicateSizeBytes: Long = 0L,
)

data class ChatStorageSummary(
    val overview: ChatStorageOverview = ChatStorageOverview(),
    val isSyncing: Boolean = true,
)

@OptIn(ExperimentalAtomicApi::class)
class ChatAttachmentRepository(
    private val context: Context,
    private val chatAttachmentDao: ChatAttachmentDao,
    private val conversationAttachmentRefDao: ConversationAttachmentRefDao,
    private val conversationDao: ConversationDAO,
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
) {
    private val storageSyncCount = MutableStateFlow(0)
    private val initialStorageSyncStarted = AtomicBoolean(false)

    fun observeAttachmentUsage(): Flow<List<ChatAttachmentUsage>> {
        return combine(
            chatAttachmentDao.observeAll(),
            conversationAttachmentRefDao.observeAll(),
            conversationDao.getAllLight(),
        ) { attachments, refs, conversations ->
            val lastUsedByConversationId = conversations.associateBy({ it.id }, { it.updateAt })
            val refsByAttachment = refs.groupBy { it.attachmentId }
            attachments
                .filter { !it.deleted }
                .map { attachment ->
                    val attachmentRefs = refsByAttachment[attachment.id].orEmpty()
                    ChatAttachmentUsage(
                        id = attachment.id,
                        uri = attachment.toUriString(),
                        displayName = attachment.displayName.ifBlank { attachment.toDefaultDisplayName() },
                        mime = attachment.mime,
                        kind = attachment.kindAsEnum(),
                        sha256 = attachment.sha256,
                        sizeBytes = attachment.sizeBytes,
                        width = attachment.width,
                        height = attachment.height,
                        referenceCount = attachmentRefs.size,
                        lastUsedAt = attachmentRefs.maxOfOrNull { ref ->
                            lastUsedByConversationId[ref.conversationId] ?: 0L
                        }?.takeIf { it > 0L },
                        deleted = attachment.deleted,
                        ocrStatus = attachment.ocrStatusAsEnum(),
                    )
                }
                .sortedWith(compareByDescending<ChatAttachmentUsage> { it.lastUsedAt ?: 0L }.thenByDescending { it.sizeBytes })
        }
    }

    fun observeOverview(): Flow<ChatStorageOverview> {
        return observeAttachmentUsage().map { usage ->
            val duplicateBytes = usage
                .groupBy { attachment -> attachment.sha256 }
                .values
                .sumOf { duplicates -> duplicates.drop(1).sumOf { it.sizeBytes } }
            ChatStorageOverview(
                totalCount = usage.size,
                totalSizeBytes = usage.sumOf { it.sizeBytes },
                duplicateSizeBytes = duplicateBytes,
            )
        }
    }

    fun observeStorageSummary(): Flow<ChatStorageSummary> {
        ensureStorageSummarySync()
        return combine(
            observeOverview(),
            storageSyncCount,
        ) { overview, syncCount ->
            ChatStorageSummary(
                overview = overview,
                isSyncing = syncCount > 0,
            )
        }
    }

    suspend fun getUsageSnapshot(): List<ChatAttachmentUsage> = withContext(Dispatchers.IO) {
        val attachments = chatAttachmentDao.getAll()
        val refs = conversationAttachmentRefDao.getAll()
        val conversations = conversationDao.getAllLight().first()
        val lastUsedByConversationId = conversations.associateBy({ it.id }, { it.updateAt })
        val refsByAttachment = refs.groupBy { it.attachmentId }
        attachments
            .filter { !it.deleted }
            .map { attachment ->
                val attachmentRefs = refsByAttachment[attachment.id].orEmpty()
                ChatAttachmentUsage(
                    id = attachment.id,
                    uri = attachment.toUriString(),
                    displayName = attachment.displayName.ifBlank { attachment.toDefaultDisplayName() },
                    mime = attachment.mime,
                    kind = attachment.kindAsEnum(),
                    sha256 = attachment.sha256,
                    sizeBytes = attachment.sizeBytes,
                    width = attachment.width,
                    height = attachment.height,
                    referenceCount = attachmentRefs.size,
                    lastUsedAt = attachmentRefs.maxOfOrNull { ref ->
                        lastUsedByConversationId[ref.conversationId] ?: 0L
                    }?.takeIf { it > 0L },
                    deleted = attachment.deleted,
                    ocrStatus = attachment.ocrStatusAsEnum(),
                )
            }
            .sortedWith(compareByDescending<ChatAttachmentUsage> { it.lastUsedAt ?: 0L }.thenByDescending { it.sizeBytes })
    }

    suspend fun getTrackedAttachmentFilePaths(): Set<String> = withContext(Dispatchers.IO) {
        chatAttachmentDao.getAll()
            .filter { !it.deleted && it.filePath.isNotBlank() }
            .map { attachment -> File(attachment.filePath).canonicalPath }
            .toSet()
    }

    suspend fun hasTrackedAttachmentFile(file: File): Boolean = withContext(Dispatchers.IO) {
        val canonicalPath = runCatching { file.canonicalPath }.getOrNull() ?: return@withContext false
        chatAttachmentDao.getAll().any { attachment ->
            val attachmentPath = attachment.filePath.takeIf { it.isNotBlank() } ?: return@any false
            runCatching { File(attachmentPath).canonicalPath }.getOrNull() == canonicalPath
        }
    }

    suspend fun importChatFiles(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            runCatching {
                importChatFile(uri)?.uri
            }.onFailure { error ->
                android.util.Log.w("ChatAttachmentRepository", "Failed to import chat attachment: $uri", error)
            }.getOrNull()
        }
    }

    suspend fun importChatFile(
        sourceUri: Uri,
        fileNameHint: String? = null,
        mimeHint: String? = null,
    ): ImportedChatFile? = withContext(Dispatchers.IO) {
        val mime = mimeHint ?: context.getFileMimeType(sourceUri).orEmpty()
        val kind = resolveKind(mime)
        val displayName = fileNameHint ?: context.getFileNameFromUri(sourceUri) ?: "file"
        val settings = settingsStore.settingsFlow.value.chatStorage
        val storedEntity = when (kind) {
            ChatAttachmentKind.IMAGE -> storeImageAttachment(sourceUri, displayName, mime, settings)
            else -> storeBinaryAttachment(sourceUri, displayName, mime, kind)
        } ?: return@withContext null

        ImportedChatFile(
            uri = storedEntity.toUri(),
            fileName = displayName,
            mime = mime.ifBlank { storedEntity.mime },
        )
    }

    suspend fun copyOrReuseUrl(url: String): String = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return@withContext url
        if (uri.scheme == "file" && isInsideChatUploadDirectory(uri.toFile())) {
            return@withContext importChatFile(uri)?.uri?.toString() ?: url
        }
        importChatFile(uri)?.uri?.toString() ?: url
    }

    suspend fun syncConversationAttachments(conversation: Conversation): Conversation = withContext(Dispatchers.IO) {
        var hasChanges = false
        val attachmentIds = linkedSetOf<String>()
        val updatedNodes = conversation.messageNodes.map { node ->
            val updatedMessages = node.messages.map { message ->
                val updatedParts = message.parts.map { part ->
                    val synchronizedPart = synchronizePart(part)
                    synchronizedPart.chatAttachmentId()?.let { attachmentIds += it }
                    if (synchronizedPart != part) {
                        hasChanges = true
                    }
                    synchronizedPart
                }
                if (updatedParts != message.parts) {
                    message.copy(parts = updatedParts)
                } else {
                    message
                }
            }
            if (updatedMessages != node.messages) {
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }
        replaceConversationRefs(conversation.id.toString(), attachmentIds)
        if (hasChanges) {
            conversation.copy(messageNodes = updatedNodes)
        } else {
            conversation
        }
    }

    suspend fun removeConversationReferences(conversationId: Uuid) = withContext(Dispatchers.IO) {
        conversationAttachmentRefDao.deleteConversationRefs(conversationId.toString())
        cleanupOrphans()
    }

    suspend fun deleteAttachment(attachmentId: String) = withContext(Dispatchers.IO) {
        markAttachmentDeleted(attachmentId)
        cleanupOrphans()
    }

    suspend fun ensureAttachmentOcr(
        attachmentId: String,
        onBeforeProviderCall: (suspend () -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        val attachment = chatAttachmentDao.getById(attachmentId) ?: return@withContext null
        if (attachment.deleted || attachment.kindAsEnum() != ChatAttachmentKind.IMAGE) {
            return@withContext null
        }
        if (!attachment.ocrText.isNullOrBlank()) {
            return@withContext attachment.ocrText
        }
        val file = attachment.filePath.takeIf { it.isNotBlank() }?.let(::File)
        if (file == null || !file.exists()) {
            chatAttachmentDao.update(
                attachment.copy(
                    ocrStatus = ChatAttachmentOcrStatus.FAILED.name,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            return@withContext null
        }
        chatAttachmentDao.update(
            attachment.copy(
                ocrStatus = ChatAttachmentOcrStatus.PENDING.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
        val result = OcrTransformer.performOcrWithMetadata(
            part = UIMessagePart.Image(file.toUri().toString()),
            onBeforeProviderCall = onBeforeProviderCall,
        )
        val updatedAttachment = when (result.status) {
            OcrStatus.SUCCESS, OcrStatus.CACHE_HIT -> attachment.copy(
                ocrText = result.promptText,
                ocrStatus = ChatAttachmentOcrStatus.SUCCESS.name,
                updatedAt = System.currentTimeMillis(),
            )
            OcrStatus.UNAVAILABLE -> attachment.copy(
                ocrStatus = ChatAttachmentOcrStatus.UNAVAILABLE.name,
                updatedAt = System.currentTimeMillis(),
            )
            OcrStatus.FAILED -> attachment.copy(
                ocrStatus = ChatAttachmentOcrStatus.FAILED.name,
                updatedAt = System.currentTimeMillis(),
            )
        }
        chatAttachmentDao.update(updatedAttachment)
        if (!updatedAttachment.ocrText.isNullOrBlank()) {
            refreshAttachmentMetadataInConversations(updatedAttachment.id)
        }
        updatedAttachment.ocrText
    }

    suspend fun resolveAttachmentOcrText(
        part: UIMessagePart.Image,
        ensureAvailable: Boolean,
        onBeforeProviderCall: (suspend () -> Unit)? = null,
    ): String? = withContext(Dispatchers.IO) {
        part.chatAttachmentOcrText()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val attachment = resolveAttachmentForPart(part) ?: return@withContext null
        if (!attachment.ocrText.isNullOrBlank()) {
            return@withContext attachment.ocrText
        }
        if (!ensureAvailable) {
            return@withContext null
        }
        ensureAttachmentOcr(
            attachmentId = attachment.id,
            onBeforeProviderCall = onBeforeProviderCall,
        )
    }

    suspend fun runMaintenance() = withContext(Dispatchers.IO) {
        runWithStorageSyncing {
            indexAllConversationAttachments()
            compactOversizedImages()
            canonicalizeDuplicateAttachments()
            autoDeleteOldImages()
            cleanupOrphans()
        }
    }

    suspend fun indexAllConversationAttachments() = withContext(Dispatchers.IO) {
        conversationDao.getAll().first().forEach { entity ->
            val conversation = entity.toConversation()
            val syncedConversation = syncConversationAttachments(conversation)
            if (syncedConversation != conversation) {
                conversationDao.update(syncedConversation.toEntity())
            }
        }
    }

    suspend fun discardImportedUri(uri: Uri) = withContext(Dispatchers.IO) {
        val file = runCatching { uri.toFile() }.getOrNull() ?: return@withContext
        if (!isInsideChatUploadDirectory(file)) {
            return@withContext
        }
        val attachment = chatAttachmentDao.getByFilePath(file.absolutePath)
        if (attachment == null) {
            file.delete()
            return@withContext
        }
        val references = conversationAttachmentRefDao.getConversationIdsForAttachment(attachment.id)
        if (references.isEmpty()) {
            file.takeIf { it.exists() && isInsideChatUploadDirectory(it) }?.delete()
            chatAttachmentDao.deleteById(attachment.id)
        }
    }

    private suspend fun synchronizePart(part: UIMessagePart): UIMessagePart {
        val kind = part.chatAttachmentKind() ?: return part
        val currentAttachment = resolveAttachmentForPart(part)
        val attachment = when {
            currentAttachment != null -> currentAttachment
            else -> {
                val rawUrl = part.chatAttachmentUrl() ?: return part
                val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return part
                val sourceDisplayName = part.chatAttachmentDisplayName()
                    ?: context.getFileNameFromUri(uri)
                    ?: when (kind) {
                        ChatAttachmentKind.IMAGE -> "Image"
                        ChatAttachmentKind.DOCUMENT -> "File"
                        ChatAttachmentKind.VIDEO -> "Video"
                        ChatAttachmentKind.AUDIO -> "Audio"
                    }
                if (uri.scheme == "file") {
                    val file = runCatching { uri.toFile() }.getOrNull()
                    if (file != null && isInsideChatUploadDirectory(file)) {
                        indexExistingAttachmentFile(
                            file = file,
                            displayName = sourceDisplayName,
                            mimeHint = part.chatAttachmentMimeHint() ?: context.getFileMimeType(uri),
                            kindHint = kind,
                        )
                    } else {
                        importChatFile(
                            sourceUri = uri,
                            fileNameHint = sourceDisplayName,
                            mimeHint = part.chatAttachmentMimeHint() ?: context.getFileMimeType(uri),
                        )?.uri?.let { importedUri ->
                            chatAttachmentDao.getByFilePath(importedUri.toFile().absolutePath)
                        }
                    }
                } else {
                    importChatFile(
                        sourceUri = uri,
                        fileNameHint = sourceDisplayName,
                        mimeHint = part.chatAttachmentMimeHint() ?: context.getFileMimeType(uri),
                    )?.uri?.let { importedUri ->
                        chatAttachmentDao.getByFilePath(importedUri.toFile().absolutePath)
                    }
                }
            }
        } ?: return part

        return part.withAttachmentEntity(attachment)
    }

    private suspend fun replaceConversationRefs(
        conversationId: String,
        attachmentIds: Set<String>,
    ) {
        conversationAttachmentRefDao.deleteConversationRefs(conversationId)
        if (attachmentIds.isNotEmpty()) {
            conversationAttachmentRefDao.insertAll(
                attachmentIds.map { attachmentId ->
                    ConversationAttachmentRefEntity(
                        conversationId = conversationId,
                        attachmentId = attachmentId,
                    )
                }
            )
        }
    }

    private suspend fun cleanupOrphans() {
        val referencedAttachmentIds = conversationAttachmentRefDao.getAll()
            .map { it.attachmentId }
            .toSet()

        chatAttachmentDao.getAll()
            .filter { it.id !in referencedAttachmentIds }
            .forEach { attachment ->
                attachment.filePath
                    .takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.exists() && isInsideChatUploadDirectory(it) }
                    ?.delete()
                chatAttachmentDao.deleteById(attachment.id)
            }
    }

    private suspend fun markAttachmentDeleted(attachmentId: String) {
        val attachment = chatAttachmentDao.getById(attachmentId) ?: return
        val newState = if (
            attachment.kindAsEnum() == ChatAttachmentKind.IMAGE &&
            !attachment.ocrText.isNullOrBlank()
        ) {
            ChatAttachmentState.ARCHIVED
        } else {
            ChatAttachmentState.DELETED
        }

        val conversationIds = conversationAttachmentRefDao.getConversationIdsForAttachment(attachmentId)
        conversationIds.forEach { conversationId ->
            val entity = conversationDao.getConversationById(conversationId) ?: return@forEach
            val conversation = entity.toConversation()
            val rewritten = conversation.rewriteAttachmentParts(
                predicate = { part -> part.chatAttachmentId() == attachmentId },
                updatePart = { part ->
                    part.withAttachmentEntity(
                        attachment = attachment.copy(deleted = true),
                        state = newState,
                        overrideUrl = "",
                    )
                }
            )
            if (rewritten != conversation) {
                conversationDao.update(rewritten.toEntity())
            }
        }

        conversationAttachmentRefDao.deleteAttachmentRefs(attachmentId)
        chatAttachmentDao.update(
            attachment.copy(
                deleted = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun refreshAttachmentMetadataInConversations(attachmentId: String) {
        val attachment = chatAttachmentDao.getById(attachmentId) ?: return
        val conversationIds = conversationAttachmentRefDao.getConversationIdsForAttachment(attachmentId)
        conversationIds.forEach { conversationId ->
            val entity = conversationDao.getConversationById(conversationId) ?: return@forEach
            val conversation = entity.toConversation()
            val updatedConversation = conversation.rewriteAttachmentParts(
                predicate = { part -> part.chatAttachmentId() == attachmentId },
                updatePart = { part ->
                    part.withAttachmentEntity(
                        attachment = attachment,
                        state = if (attachment.deleted) ChatAttachmentState.DELETED else part.chatAttachmentState(),
                        overrideUrl = if (attachment.deleted) "" else attachment.toUriString(),
                    )
                }
            )
            if (updatedConversation != conversation) {
                conversationDao.update(updatedConversation.toEntity())
            }
        }
    }

    private suspend fun resolveAttachmentForPart(part: UIMessagePart): ChatAttachmentEntity? {
        part.chatAttachmentId()?.let { attachmentId ->
            chatAttachmentDao.getById(attachmentId)?.let { return it }
        }
        val url = part.chatAttachmentUrl() ?: return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (uri.scheme != "file") {
            return null
        }
        val file = runCatching { uri.toFile() }.getOrNull() ?: return null
        return chatAttachmentDao.getByFilePath(file.absolutePath)
    }

    private fun resolveKind(mime: String): ChatAttachmentKind {
        return when {
            mime.startsWith("image/") -> ChatAttachmentKind.IMAGE
            mime.startsWith("video/") -> ChatAttachmentKind.VIDEO
            mime.startsWith("audio/") -> ChatAttachmentKind.AUDIO
            else -> ChatAttachmentKind.DOCUMENT
        }
    }

    private suspend fun storeImageAttachment(
        sourceUri: Uri,
        displayName: String,
        mime: String,
        settings: ChatStorageSettings,
    ): ChatAttachmentEntity? {
        val existingFile = if (sourceUri.scheme == "file") {
            runCatching { sourceUri.toFile() }.getOrNull()
        } else {
            null
        }
        if (existingFile != null && isInsideChatUploadDirectory(existingFile)) {
            chatAttachmentDao.getByFilePath(existingFile.absolutePath)?.let { return it }
        }

        val longEdgeLimit = settings.imageMaxLongEdgePx?.takeIf { it > 0 }
        val imageInfo = ImageUtils.getImageInfo(context, sourceUri)
        val effectiveMime = mime.ifBlank { imageInfo?.mimeType.orEmpty() }.ifBlank { "image/png" }

        if (longEdgeLimit == null) {
            val file = getUploadDirectory().resolve(
                buildStoredFileName(displayName, effectiveMime)
            )
            val input = openInputStream(sourceUri) ?: return null
            val copyResult = copyAndHash(input, file) ?: return null
            val existing = chatAttachmentDao.getActiveBySha256(copyResult.sha256)
            if (existing != null) {
                file.delete()
                return existing
            }
            val entity = ChatAttachmentEntity(
                id = Uuid.random().toString(),
                filePath = file.absolutePath,
                displayName = displayName,
                sha256 = copyResult.sha256,
                mime = effectiveMime,
                kind = ChatAttachmentKind.IMAGE.name,
                sizeBytes = file.length(),
                width = imageInfo?.width,
                height = imageInfo?.height,
                ocrStatus = ChatAttachmentOcrStatus.NONE.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            chatAttachmentDao.insert(entity)
            return entity
        }

        val bitmap = ImageUtils.loadOptimizedBitmap(
            context = context,
            uri = sourceUri,
            maxSize = longEdgeLimit.coerceAtLeast(1024),
        ) ?: return storeBinaryAttachment(
            sourceUri = sourceUri,
            displayName = displayName,
            mime = effectiveMime,
            kind = ChatAttachmentKind.IMAGE,
        )?.copy(kind = ChatAttachmentKind.IMAGE.name)

        val scaledBitmap = scaleBitmapIfNeeded(bitmap, longEdgeLimit)
        val preserveAlpha = scaledBitmap.hasAlpha()
        val outputMime = if (preserveAlpha) "image/png" else "image/jpeg"
        val outputBuffer = Buffer()
        outputBuffer.outputStream().use { output ->
            scaledBitmap.compress(
                if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                if (preserveAlpha) 100 else 90,
                output,
            )
        }
        val outputBytes = outputBuffer.readByteArray()
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()

        val sha = sha256(outputBytes)
        chatAttachmentDao.getActiveBySha256(sha)?.let { return it }

        val file = getUploadDirectory().resolve(
            buildStoredFileName(
                displayName = displayName,
                mime = outputMime,
            )
        )
        file.sink().buffer().use { output ->
            output.write(outputBytes)
        }
        // Use inJustDecodeBounds to get dimensions without allocating a full bitmap
        val outputBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size, outputBounds)
        val entity = ChatAttachmentEntity(
            id = Uuid.random().toString(),
            filePath = file.absolutePath,
            displayName = displayName,
            sha256 = sha,
            mime = outputMime,
            kind = ChatAttachmentKind.IMAGE.name,
            sizeBytes = file.length(),
            width = outputBounds.outWidth.takeIf { it > 0 } ?: imageInfo?.width,
            height = outputBounds.outHeight.takeIf { it > 0 } ?: imageInfo?.height,
            ocrStatus = ChatAttachmentOcrStatus.NONE.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        chatAttachmentDao.insert(entity)
        return entity
    }

    private suspend fun storeBinaryAttachment(
        sourceUri: Uri,
        displayName: String,
        mime: String,
        kind: ChatAttachmentKind,
    ): ChatAttachmentEntity? {
        val existingFile = if (sourceUri.scheme == "file") {
            runCatching { sourceUri.toFile() }.getOrNull()
        } else {
            null
        }
        if (existingFile != null && isInsideChatUploadDirectory(existingFile)) {
            chatAttachmentDao.getByFilePath(existingFile.absolutePath)?.let { return it }
        }

        val file = getUploadDirectory().resolve(
            buildStoredFileName(displayName, mime)
        )
        val input = openInputStream(sourceUri) ?: return null
        val copyResult = copyAndHash(input, file) ?: return null
        val existing = chatAttachmentDao.getActiveBySha256(copyResult.sha256)
        if (existing != null) {
            file.delete()
            return existing
        }

        val entity = ChatAttachmentEntity(
            id = Uuid.random().toString(),
            filePath = file.absolutePath,
            displayName = displayName,
            sha256 = copyResult.sha256,
            mime = mime.ifBlank { "application/octet-stream" },
            kind = kind.name,
            sizeBytes = file.length(),
            width = null,
            height = null,
            ocrStatus = ChatAttachmentOcrStatus.NONE.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        chatAttachmentDao.insert(entity)
        return entity
    }

    private suspend fun compactOversizedImages() {
        val maxLongEdge = settingsStore.settingsFlow.value.chatStorage.imageMaxLongEdgePx?.takeIf { it > 0 }
            ?: return
        chatAttachmentDao.getAll()
            .filter { !it.deleted && it.kindAsEnum() == ChatAttachmentKind.IMAGE }
            .forEach { attachment ->
                val currentLongEdge = maxOf(attachment.width ?: 0, attachment.height ?: 0)
                if (currentLongEdge in 1..maxLongEdge) {
                    return@forEach
                }
                val file = attachment.filePath.takeIf { it.isNotBlank() }?.let(::File)
                if (file == null || !file.exists()) {
                    return@forEach
                }
                val replacement = storeImageAttachment(
                    sourceUri = file.toUri(),
                    displayName = attachment.displayName.ifBlank { attachment.toDefaultDisplayName() },
                    mime = attachment.mime,
                    settings = settingsStore.settingsFlow.value.chatStorage,
                ) ?: return@forEach
                if (replacement.id != attachment.id) {
                    repointAttachmentReferences(attachment.id, replacement)
                }
            }
    }

    private suspend fun canonicalizeDuplicateAttachments() {
        val duplicates = chatAttachmentDao.getAll()
            .filter { !it.deleted }
            .groupBy { it.sha256 }
            .values
            .filter { it.size > 1 }

        duplicates.forEach { group ->
            val canonical = group.minByOrNull { it.createdAt } ?: return@forEach
            group.filter { it.id != canonical.id }.forEach { duplicate ->
                repointAttachmentReferences(duplicate.id, canonical)
            }
        }
    }

    private suspend fun autoDeleteOldImages() {
        val days = settingsStore.settingsFlow.value.chatStorage.autoDeleteChatImagesAfterDays?.takeIf { it > 0 }
            ?: return
        val cutoffTime = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        val conversationsById = conversationDao.getAllLight().first().associateBy { it.id }
        chatAttachmentDao.getAll()
            .filter { !it.deleted && it.kindAsEnum() == ChatAttachmentKind.IMAGE }
            .forEach { attachment ->
                val lastConversationUpdate = conversationAttachmentRefDao
                    .getConversationIdsForAttachment(attachment.id)
                    .mapNotNull { conversationId -> conversationsById[conversationId]?.updateAt }
                    .maxOrNull()
                    ?: return@forEach
                if (lastConversationUpdate >= cutoffTime) {
                    return@forEach
                }
                markAttachmentDeleted(attachment.id)
            }
    }

    private fun ensureStorageSummarySync() {
        if (!initialStorageSyncStarted.compareAndSet(false, true)) {
            return
        }

        storageSyncCount.update { it + 1 }
        appScope.launch(Dispatchers.IO) {
            try {
                indexAllConversationAttachments()
            } catch (error: Exception) {
                Log.e(TAG, "Failed to reconcile chat attachments for storage stats", error)
            } finally {
                storageSyncCount.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    private suspend fun <T> runWithStorageSyncing(
        block: suspend () -> T,
    ): T {
        storageSyncCount.update { it + 1 }
        return try {
            block()
        } finally {
            storageSyncCount.update { (it - 1).coerceAtLeast(0) }
        }
    }

    private fun isInsideChatUploadDirectory(file: File): Boolean {
        val uploadDir = getUploadDirectory()
        return runCatching {
            file.canonicalPath.startsWith(uploadDir.canonicalPath)
        }.getOrDefault(false)
    }

    private suspend fun indexExistingAttachmentFile(
        file: File,
        displayName: String,
        mimeHint: String?,
        kindHint: ChatAttachmentKind?,
    ): ChatAttachmentEntity? {
        if (!file.exists()) {
            return null
        }
        chatAttachmentDao.getByFilePath(file.absolutePath)?.let { return it }

        val mime = mimeHint
            ?.takeIf { it.isNotBlank() }
            ?: context.getFileMimeType(file.toUri())
            ?: "application/octet-stream"
        val kind = kindHint ?: resolveKind(mime)
        val sha = sha256(file)
        chatAttachmentDao.getActiveBySha256(sha)?.let { existing ->
            if (existing.filePath != file.absolutePath && isInsideChatUploadDirectory(file)) {
                file.delete()
            }
            return existing
        }

        val imageInfo = if (kind == ChatAttachmentKind.IMAGE) {
            ImageUtils.getImageInfo(context, file.toUri())
        } else {
            null
        }
        val entity = ChatAttachmentEntity(
            id = Uuid.random().toString(),
            filePath = file.absolutePath,
            displayName = displayName,
            sha256 = sha,
            mime = mime,
            kind = kind.name,
            sizeBytes = file.length(),
            width = imageInfo?.width,
            height = imageInfo?.height,
            ocrStatus = ChatAttachmentOcrStatus.NONE.name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        chatAttachmentDao.insert(entity)
        return entity
    }

    private suspend fun repointAttachmentReferences(
        sourceAttachmentId: String,
        targetAttachment: ChatAttachmentEntity,
    ) {
        if (sourceAttachmentId == targetAttachment.id) {
            return
        }
        val conversationIds = conversationAttachmentRefDao.getConversationIdsForAttachment(sourceAttachmentId)
        conversationIds.forEach { conversationId ->
            val entity = conversationDao.getConversationById(conversationId) ?: return@forEach
            val conversation = entity.toConversation()
            val rewritten = conversation.rewriteAttachmentParts(
                predicate = { part -> part.chatAttachmentId() == sourceAttachmentId },
                updatePart = { part ->
                    part.withAttachmentEntity(
                        attachment = targetAttachment,
                        overrideUrl = targetAttachment.toUriString(),
                    )
                }
            )
            val syncedConversation = syncConversationAttachments(rewritten)
            if (syncedConversation != conversation) {
                conversationDao.update(syncedConversation.toEntity())
            }
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> runCatching { uri.toFile().inputStream() }.getOrNull()
            else -> null
        }
    }

    private fun copyAndHash(input: InputStream, outputFile: File): CopyAndHashResult? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            outputFile.parentFile?.mkdirs()
            outputFile.sink().buffer().outputStream().use { output ->
                input.use { inputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            CopyAndHashResult(
                sha256 = digest.digest().toHex(),
                sizeBytes = outputFile.length(),
            )
        }.getOrNull()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.source().buffer().inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    private fun scaleBitmapIfNeeded(
        source: Bitmap,
        maxLongEdge: Int,
    ): Bitmap {
        val currentLongEdge = maxOf(source.width, source.height)
        if (currentLongEdge <= maxLongEdge || maxLongEdge <= 0) {
            return source
        }
        val scale = maxLongEdge.toFloat() / currentLongEdge.toFloat()
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun getUploadDirectory(): File {
        return context.filesDir.resolve("upload").apply { mkdirs() }
    }

    private fun resolveExtension(
        displayName: String,
        mime: String,
    ): String? {
        val fromName = displayName.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it != displayName }
            ?.lowercase()
        if (!fromName.isNullOrBlank()) {
            return fromName
        }
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()
    }

    private fun buildStoredFileName(
        displayName: String,
        mime: String,
    ): String {
        val safeBaseName = displayName
            .substringBeforeLast('.', displayName)
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(48)
            .ifBlank { CHAT_UPLOAD_PREFIX + Uuid.random().toString().take(8) }
        val extension = resolveExtension(displayName, mime)
        return buildString {
            append(safeBaseName)
            append('-')
            append(Uuid.random())
            extension?.takeIf { it.isNotBlank() }?.let {
                append('.')
                append(it)
            }
        }
    }
}

private data class CopyAndHashResult(
    val sha256: String,
    val sizeBytes: Long,
)

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun ChatAttachmentEntity.toUri(): Uri = File(filePath).toUri()

private fun ChatAttachmentEntity.toUriString(): String {
    return if (filePath.isBlank()) "" else File(filePath).toUri().toString()
}

private fun ChatAttachmentEntity.kindAsEnum(): ChatAttachmentKind {
    return runCatching { ChatAttachmentKind.valueOf(kind) }.getOrDefault(ChatAttachmentKind.DOCUMENT)
}

private fun ChatAttachmentEntity.ocrStatusAsEnum(): ChatAttachmentOcrStatus {
    return runCatching { ChatAttachmentOcrStatus.valueOf(ocrStatus) }.getOrDefault(ChatAttachmentOcrStatus.NONE)
}

private fun ChatAttachmentEntity.toDefaultDisplayName(): String {
    return when (kindAsEnum()) {
        ChatAttachmentKind.IMAGE -> "Image"
        ChatAttachmentKind.DOCUMENT -> "File"
        ChatAttachmentKind.VIDEO -> "Video"
        ChatAttachmentKind.AUDIO -> "Audio"
    }
}

private fun ConversationEntity.toConversation(): Conversation {
    return Conversation(
        id = Uuid.parse(id),
        assistantId = Uuid.parse(assistantId),
        title = title,
        messageNodes = JsonInstant.decodeFromString<List<MessageNode>>(nodes).filter { it.messages.isNotEmpty() },
        truncateIndex = truncateIndex,
        chatSuggestions = JsonInstant.decodeFromString(chatSuggestions),
        isPinned = isPinned,
        createAt = Instant.ofEpochMilli(createAt),
        updateAt = Instant.ofEpochMilli(updateAt),
        isConsolidated = isConsolidated,
        enabledModeIds = runCatching {
            JsonInstant.decodeFromString<List<String>>(enabledModeIds).map(Uuid::parse).toSet()
        }.getOrDefault(emptySet()),
        enabledLorebookIds = if (enabledLorebookIds.isBlank()) {
            null
        } else {
            runCatching {
                JsonInstant.decodeFromString<List<String>>(enabledLorebookIds).map(Uuid::parse).toSet()
            }.getOrNull()
        },
        contextSummary = contextSummary.takeIf { it.isNotBlank() },
        contextSummaryUpToIndex = contextSummaryUpToIndex,
        lastPruneTime = lastPruneTime,
        lastPruneMessageCount = lastPruneMessageCount,
        lastRefreshTime = lastRefreshTime,
    )
}

private fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id.toString(),
        assistantId = assistantId.toString(),
        title = title,
        nodes = JsonInstant.encodeToString(messageNodes),
        createAt = createAt.toEpochMilli(),
        updateAt = updateAt.toEpochMilli(),
        truncateIndex = truncateIndex,
        chatSuggestions = JsonInstant.encodeToString(chatSuggestions),
        isPinned = isPinned,
        isConsolidated = isConsolidated,
        enabledModeIds = JsonInstant.encodeToString(enabledModeIds.map { it.toString() }),
        enabledLorebookIds = enabledLorebookIds
            ?.let { JsonInstant.encodeToString(it.map { id -> id.toString() }) }
            .orEmpty(),
        contextSummary = contextSummary ?: "",
        contextSummaryUpToIndex = contextSummaryUpToIndex,
        lastPruneTime = lastPruneTime,
        lastPruneMessageCount = lastPruneMessageCount,
        lastRefreshTime = lastRefreshTime,
    )
}

private fun Conversation.rewriteAttachmentParts(
    predicate: (UIMessagePart) -> Boolean,
    updatePart: (UIMessagePart) -> UIMessagePart,
): Conversation {
    val updatedNodes = messageNodes.map { node ->
        val updatedMessages = node.messages.map { message ->
            val updatedParts = message.parts.map { part ->
                if (predicate(part)) updatePart(part) else part
            }
            if (updatedParts != message.parts) message.copy(parts = updatedParts) else message
        }
        if (updatedMessages != node.messages) node.copy(messages = updatedMessages) else node
    }
    return if (updatedNodes != messageNodes) copy(messageNodes = updatedNodes) else this
}

private fun UIMessagePart.withAttachmentEntity(
    attachment: ChatAttachmentEntity,
    state: ChatAttachmentState = if (attachment.deleted) {
        if (attachment.kindAsEnum() == ChatAttachmentKind.IMAGE && !attachment.ocrText.isNullOrBlank()) {
            ChatAttachmentState.ARCHIVED
        } else {
            ChatAttachmentState.DELETED
        }
    } else {
        chatAttachmentState()
    },
    overrideUrl: String = if (attachment.deleted) "" else attachment.toUriString(),
): UIMessagePart {
    val displayName = attachment.displayName.ifBlank {
        chatAttachmentDisplayName() ?: attachment.toDefaultDisplayName()
    }
    return when (this) {
        is UIMessagePart.Image -> copy(
            url = overrideUrl,
            metadata = buildChatAttachmentMetadata(
                existing = metadata,
                fileId = attachment.id,
                state = state,
                ocrText = attachment.ocrText ?: chatAttachmentOcrText(),
                ocrStatus = attachment.ocrStatusAsEnum(),
                displayName = displayName,
                mime = attachment.mime,
            )
        )

        is UIMessagePart.Document -> copy(
            url = overrideUrl,
            metadata = buildChatAttachmentMetadata(
                existing = metadata,
                fileId = attachment.id,
                state = state,
                ocrText = attachment.ocrText ?: chatAttachmentOcrText(),
                ocrStatus = attachment.ocrStatusAsEnum(),
                displayName = displayName,
                mime = attachment.mime,
            )
        )

        is UIMessagePart.Video -> copy(
            url = overrideUrl,
            metadata = buildChatAttachmentMetadata(
                existing = metadata,
                fileId = attachment.id,
                state = state,
                ocrText = attachment.ocrText ?: chatAttachmentOcrText(),
                ocrStatus = attachment.ocrStatusAsEnum(),
                displayName = displayName,
                mime = attachment.mime,
            )
        )

        is UIMessagePart.Audio -> copy(
            url = overrideUrl,
            metadata = buildChatAttachmentMetadata(
                existing = metadata,
                fileId = attachment.id,
                state = state,
                ocrText = attachment.ocrText ?: chatAttachmentOcrText(),
                ocrStatus = attachment.ocrStatusAsEnum(),
                displayName = displayName,
                mime = attachment.mime,
            )
        )

        else -> this
    }
}
