package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformFilePicker
import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformPickedFile
import me.rerere.common.platform.PlatformPickedFileKind
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
class IosPlatformFilePicker(
    private val fileStore: PlatformFileStore,
    private val presentingViewController: () -> UIViewController?,
) : PlatformFilePicker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pendingResult: ((Result<PlatformPickedFile?>) -> Unit)? = null
    private val pickerDelegate = IosDocumentPickerDelegate(
        onPicked = ::handlePickedUrl,
        onCancelled = { finish(Result.success(null)) },
    )

    override fun pickFile(onResult: (Result<PlatformPickedFile?>) -> Unit) {
        if (pendingResult != null) {
            onResult(Result.failure(IllegalStateException("A file picker is already open.")))
            return
        }
        val presenter = presentingViewController()
        if (presenter == null) {
            onResult(Result.failure(IllegalStateException("The iOS window is not ready.")))
            return
        }
        pendingResult = onResult
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeImage, UTTypeMovie, UTTypeAudio),
            asCopy = true,
        ).apply {
            allowsMultipleSelection = false
            delegate = pickerDelegate
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun handlePickedUrl(sourceUrl: NSURL) {
        val displayName = sourceUrl.lastPathComponent?.takeIf { it.isNotBlank() } ?: "attachment"
        scope.launch {
            val result: Result<PlatformPickedFile?> = withContext(Dispatchers.Default) {
                runCatching {
                    val sourceData = NSData.dataWithContentsOfURL(sourceUrl)
                        ?: error("The selected file could not be read.")
                    require(sourceData.length <= MAX_ATTACHMENT_BYTES.toULong()) {
                        "Attachments larger than 50 MB are not supported yet."
                    }
                    val bytes = sourceData.toByteArray()
                    val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val storagePath = "attachments/${Uuid.random()}-$safeName"
                    fileStore.writeBytes(storagePath, bytes)
                    val localUrl = fileStore.localUrl(storagePath)
                        ?: error("The selected file could not be exposed to the media pipeline.")
                    val mimeType = mimeTypeFor(displayName)
                    PlatformPickedFile(
                        storagePath = storagePath,
                        localUrl = localUrl,
                        displayName = displayName,
                        mimeType = mimeType,
                        kind = kindFor(mimeType),
                    )
                }
            }
            finish(result)
        }
    }

    private fun finish(result: Result<PlatformPickedFile?>) {
        val callback = pendingResult ?: return
        pendingResult = null
        callback(result)
    }

    private fun NSData.toByteArray(): ByteArray {
        if (length == 0uL) return ByteArray(0)
        return ByteArray(length.toInt()).also { output ->
            output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        }
    }

    private fun mimeTypeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "pdf" -> "application/pdf"
        "txt", "md" -> "text/plain"
        "json" -> "application/json"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    private fun kindFor(mimeType: String): PlatformPickedFileKind = when {
        mimeType.startsWith("image/") -> PlatformPickedFileKind.Image
        mimeType.startsWith("video/") -> PlatformPickedFileKind.Video
        mimeType.startsWith("audio/") -> PlatformPickedFileKind.Audio
        else -> PlatformPickedFileKind.Document
    }

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 50L * 1024L * 1024L
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentPickerDelegate(
    private val onPicked: (NSURL) -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val sourceUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (sourceUrl != null) onPicked(sourceUrl) else onCancelled()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancelled()
    }
}
