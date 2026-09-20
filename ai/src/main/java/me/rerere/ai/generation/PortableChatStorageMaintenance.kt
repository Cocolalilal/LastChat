package me.rerere.ai.generation

import me.rerere.common.platform.PlatformFileStore

/**
 * Deletes unreferenced upload/image/attachment files. Android WorkManager still
 * runs the richer Room attachment-index maintenance; iOS uses this cleaner.
 */
object PortableChatStorageMaintenance {
    const val UNIQUE_NAME = "chat_storage_maintenance"
    const val WORK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    val DEFAULT_ROOTS = listOf("uploads", "images", "attachments")

    data class Result(
        val scanned: Int = 0,
        val deleted: Int = 0,
    )

    suspend fun run(
        store: PortableConversationStore,
        fileStore: PlatformFileStore,
        roots: List<String> = DEFAULT_ROOTS,
    ): Result {
        val referenced = PortableConversationQueries.referencedFilePaths(store.list())
            .flatMap { path ->
                listOf(path, path.trimStart('/'), "/$path".trimStart('/'))
            }
            .toHashSet()
        var scanned = 0
        var deleted = 0
        roots.forEach { root ->
            fileStore.listFiles(root).forEach { path ->
                scanned += 1
                val normalized = path.trimStart('/')
                val candidates = listOf(path, normalized, "/$normalized")
                if (candidates.none { it in referenced || referencedPathContains(referenced, normalized) }) {
                    val removed = fileStore.delete(path) || fileStore.delete(normalized)
                    if (removed) deleted += 1
                }
            }
        }
        return Result(scanned = scanned, deleted = deleted)
    }

    private fun referencedPathContains(referenced: Set<String>, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        return referenced.any { known ->
            known == candidate ||
                known.endsWith("/$candidate") ||
                candidate.endsWith("/$known") ||
                known.endsWith(candidate)
        }
    }
}
