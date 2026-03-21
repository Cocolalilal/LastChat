package me.rerere.rikkahub.data.repository

import android.net.Uri
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object ChatAttachmentManager : KoinComponent {
    private val repository: ChatAttachmentRepository by inject()

    suspend fun importChatFiles(uris: List<Uri>): List<Uri> = repository.importChatFiles(uris)

    suspend fun importChatFile(
        uri: Uri,
        fileNameHint: String? = null,
        mimeHint: String? = null,
    ): ImportedChatFile? = repository.importChatFile(
        sourceUri = uri,
        fileNameHint = fileNameHint,
        mimeHint = mimeHint,
    )

    suspend fun copyOrReuseUrl(url: String): String = repository.copyOrReuseUrl(url)
}
