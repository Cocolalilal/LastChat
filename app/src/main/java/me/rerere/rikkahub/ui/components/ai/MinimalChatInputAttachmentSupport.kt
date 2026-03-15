package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.net.Uri
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileNameFromUri

private const val PDF_MIME_TYPE = "application/pdf"

internal data class ImportedPickerFiles(
    val imageUris: List<Uri>,
    val documents: List<UIMessagePart.Document>,
    val unsupportedFileNames: List<String>,
    val failedFileNames: List<String>,
)

private data class PendingDocumentImport(
    val sourceUri: Uri,
    val fileName: String,
    val mimeType: String,
)

internal fun Context.prepareImportedPickerFiles(
    selectedUris: List<Uri>,
    isPythonEnabled: Boolean,
): ImportedPickerFiles {
    val imageUris = mutableListOf<Uri>()
    val documentsToImport = mutableListOf<PendingDocumentImport>()
    val unsupportedFileNames = mutableListOf<String>()

    selectedUris.forEach { uri ->
        val mimeType = contentResolver.getType(uri).orEmpty()
        val fileName = getFileNameFromUri(uri) ?: "file"
        val isNativelySupported =
            mimeType.startsWith("image/") ||
                mimeType.startsWith("text/") ||
                mimeType == PDF_MIME_TYPE

        if (!isPythonEnabled && !isNativelySupported) {
            unsupportedFileNames.add(fileName)
            return@forEach
        }

        if (mimeType.startsWith("image/")) {
            imageUris.add(uri)
        } else {
            documentsToImport.add(
                PendingDocumentImport(
                    sourceUri = uri,
                    fileName = fileName,
                    mimeType = mimeType.ifEmpty { "application/octet-stream" }
                )
            )
        }
    }

    val importedImages = if (imageUris.isNotEmpty()) {
        createChatFilesByContents(imageUris)
    } else {
        emptyList()
    }

    val importedDocuments = mutableListOf<UIMessagePart.Document>()
    val failedFileNames = mutableListOf<String>()
    documentsToImport.forEach { document ->
        val localUri = createChatFilesByContents(listOf(document.sourceUri)).firstOrNull()
        if (localUri == null) {
            failedFileNames.add(document.fileName)
            return@forEach
        }

        importedDocuments.add(
            UIMessagePart.Document(
                url = localUri.toString(),
                fileName = document.fileName,
                mime = document.mimeType
            )
        )
    }

    return ImportedPickerFiles(
        imageUris = importedImages,
        documents = importedDocuments,
        unsupportedFileNames = unsupportedFileNames,
        failedFileNames = failedFileNames,
    )
}
