package me.rerere.rikkahub.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.navigation.CHAT_ROUTE_TARGET_KEY
import me.rerere.rikkahub.navigation.ChatRouteTarget
import okio.Buffer
import okio.buffer
import okio.sink
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

private const val TAG = "ChatUtil"

fun navigateToChatPage(
    navController: NavHostController,
    chatId: Uuid = Uuid.random(),
    initText: String? = null,
    initFiles: List<Uri> = emptyList(),
    searchQuery: String? = null,
    persistenceMode: String? = null,
    focusLatestMessageKey: String? = null,
) {
    Log.i(TAG, "navigateToChatPage: navigate to $chatId")
    val target = ChatRouteTarget(
        id = chatId.toString(),
        text = initText,
        files = initFiles.map { it.toString() },
        searchQuery = searchQuery,
        persistenceMode = persistenceMode,
        focusLatestMessageKey = focusLatestMessageKey,
    )
    val isAlreadyOnChat = navController.currentBackStackEntry
        ?.let { backStackEntry ->
            runCatching { backStackEntry.toRoute<Screen.Chat>() }.isSuccess
        } == true
    if (isAlreadyOnChat) {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(CHAT_ROUTE_TARGET_KEY, target)
        return
    }
    navController.navigate(
        route = Screen.Chat(
            id = target.id,
            text = target.text,
            files = target.files,
            searchQuery = target.searchQuery,
            persistenceMode = target.persistenceMode,
            focusLatestMessageKey = target.focusLatestMessageKey,
        ),
    ) {
        popUpTo(0) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun Context.copyMessageToClipboard(message: UIMessage) {
    this.writeClipboardText(message.toContentText())
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun Context.saveMessageImage(image: String) = withContext(Dispatchers.IO) {
    when {
        image.startsWith("data:image") -> {
            val byteArray = Base64.decode(image.substringAfter("base64,").toByteArray())
            val bitmap = decodeBitmapWithBounds(byteArray, 2048, 2048)
            bitmap?.let { exportImage(this@saveMessageImage.getActivity()!!, it) }
        }

        image.startsWith("file:") -> {
            val file = image.toUri().toFile()
            exportImageFile(this@saveMessageImage.getActivity()!!, file)
        }

        image.startsWith("content:") -> {
            // Handle content:// URIs (used by FileProvider for Python sandbox files)
            // Copy bytes directly to gallery to preserve original format
            val uri = image.toUri()
            openOwnedUriInputStream(uri)?.use { inputStream ->
                val fileName = "LastChat_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ use MediaStore API
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    val destUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (destUri != null) {
                        contentResolver.openOutputStream(destUri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        } ?: throw IllegalStateException("Failed to open output stream for MediaStore")
                    } else {
                        throw IllegalStateException("Failed to create MediaStore entry")
                    }
                } else {
                    // Android 9 and below: write to Pictures directory
                    @Suppress("DEPRECATION")
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val destFile = java.io.File(imagesDir, fileName)
                    destFile.sink().buffer().outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    // Notify media scanner
                    @Suppress("DEPRECATION")
                    val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = android.net.Uri.fromFile(destFile)
                    sendBroadcast(mediaScanIntent)
                }
                Log.i(TAG, "saveMessageImage: Saved content:// image to gallery: $fileName")
            } ?: throw IllegalStateException("Failed to open input stream from content URI: $image")
        }

        image.startsWith("http") -> {
            kotlin.runCatching { // Use runCatching to handle potential network exceptions
                val client = GlobalContext.get().get<PlatformHttpClient>()
                val response = client.execute(
                    PlatformHttpRequest(
                        method = "GET",
                        url = image,
                    )
                )

                if (response.statusCode == 200) {
                    val bitmap = decodeBitmapWithBounds(response.body, 2048, 2048)
                    bitmap?.let { exportImage(this@saveMessageImage.getActivity()!!, it) }
                } else {
                    Log.e(
                        TAG,
                        "saveMessageImage: Failed to download image from $image, response code: ${response.statusCode}"
                    )
                    null // Return null on failure
                }
            }.getOrNull() // Return null if any exception occurs during download
        }

        else -> error("Invalid image format")
    }
}

fun Context.createChatFilesByContents(uris: List<Uri>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    uris.forEach { uri ->
        val fileName = buildUploadFileName(
            originalName = getFileNameFromUri(uri),
            mimeType = getFileMimeType(uri)
        )
        val file = dir.resolve(fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri()
        runCatching {
            openUriInputStream(uri)?.use { inputStream ->
                file.sink().buffer().outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: error("Unable to open input stream for $uri")
            newUris.add(newUri)
        }.onFailure {
            it.printStackTrace()
            Log.e(TAG, "createChatFilesByContents: Failed to save image from $uri", it)
        }
    }
    return newUris
}

fun Context.createChatFilesByByteArrays(byteArrays: List<ByteArray>): List<Uri> {
    val newUris = mutableListOf<Uri>()
    val dir = this.filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    byteArrays.forEach { byteArray ->
        val fileName = Uuid.random()
        val file = dir.resolve("$fileName")
        if (!file.exists()) {
            file.createNewFile()
        }
        val newUri = file.toUri()
        file.sink().buffer().outputStream().use { outputStream ->
            outputStream.write(byteArray)
        }
        newUris.add(newUri)
    }
    return newUris
}

fun Context.createChatTextFile(fileName: String, content: String): Uri {
    val dir = filesDir.resolve("upload")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val extension = fileName.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it != fileName }
        ?.lowercase()
        ?: "txt"
    val baseName = fileName.substringBeforeLast('.', fileName)
    val safeBaseName = sanitizeUploadBaseName(baseName)
    val targetFile = dir.resolve("${safeBaseName}-${Uuid.random()}.$extension")
    targetFile.sink().buffer().use { output ->
        output.writeUtf8(content)
    }
    return targetFile.toUri()
}

fun Context.getFileNameFromUri(uri: Uri): String? {
    if (uri.scheme == "file") {
        return runCatching { uri.toFile().name }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }
    var fileName: String? = null
    val projection = arrayOf(
        OpenableColumns.DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME // 优先尝试 DocumentProvider 标准列
    )
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        // 移动到第一行结果
        if (cursor.moveToFirst()) {
            // 尝试获取 DocumentsContract.Document.COLUMN_DISPLAY_NAME 的索引
            val documentDisplayNameIndex =
                cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (documentDisplayNameIndex != -1) {
                fileName = cursor.getString(documentDisplayNameIndex)
            } else {
                // 如果 DocumentProvider 标准列不存在，尝试 OpenableColumns.DISPLAY_NAME
                val openableDisplayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (openableDisplayNameIndex != -1) {
                    fileName = cursor.getString(openableDisplayNameIndex)
                }
            }
        }
    }
    // 如果查询失败或没有获取到名称，fileName 会保持 null
    return fileName ?: uri.lastPathSegment?.substringAfterLast('/')
}

fun Context.getFileMimeType(uri: Uri): String? {
    val contentType = when (uri.scheme) {
        "content" -> contentResolver.getType(uri)
        "file" -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            uri.toFile().extension.lowercase().takeIf { it.isNotBlank() }
        )

        else -> null
    }
    if (contentType != null) {
        return contentType
    }
    val extension = getFileNameFromUri(uri)
        ?.substringAfterLast('.', "")
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
    return extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun Context.convertBase64ImagePartToLocalFile(message: UIMessage): UIMessage =
    withContext(Dispatchers.IO) {
        message.copy(
            parts = message.parts.map { part ->
                when (part) {
                    is UIMessagePart.Image -> {
                        if (part.url.startsWith("data:image")) {
                            // base64 image
                            val sourceByteArray = Base64.decode(part.url.substringAfter("base64,").toByteArray())
                            val bitmap = decodeBitmapWithBounds(sourceByteArray, 2048, 2048)
                            val byteArray = bitmap?.compress()
                            if (byteArray == null) part else {
                                val urls = createChatFilesByByteArrays(listOf(byteArray))
                                Log.i(
                                    TAG,
                                    "convertBase64ImagePartToLocalFile: convert base64 img to ${urls.joinToString(", ")}"
                                )
                                part.copy(
                                    url = urls.first().toString(),
                                )
                            }
                        } else {
                            part
                        }
                    }

                    else -> part
                }
            }
        )
    }

fun Bitmap.compress(): ByteArray {
    val buffer = Buffer()
    buffer.outputStream().use {
        compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    return buffer.readByteArray()
}

suspend fun Context.deleteChatFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
    uris.filter { it.toString().startsWith("file:") }.forEach { uri ->
        val file = uri.toFile()
        if (file.exists()) {
            file.delete()
        }
    }
}

fun Context.deleteAllChatFiles() {
    val dir = this.filesDir.resolve("upload")
    if (dir.exists()) {
        dir.deleteRecursively()
    }
}

suspend fun Context.countChatFiles(): Pair<Int, Long> = withContext(Dispatchers.IO) {
    val dir = filesDir.resolve("upload")
    if (!dir.exists()) {
        return@withContext Pair(0, 0)
    }
    val files = dir.listFiles() ?: return@withContext Pair(0, 0)
    val count = files.size
    val size = files.sumOf { it.length() }
    Pair(count, size)
}

fun Context.getImagesDir(): File {
    val dir = this.filesDir.resolve("images")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

fun Context.createImageFileFromBase64(base64Data: String, filePath: String): File {
    val data = if (base64Data.startsWith("data:image")) {
        base64Data.substringAfter("base64,")
    } else {
        base64Data
    }

    val byteArray = Base64.decode(data.toByteArray())
    val file = File(filePath)
    file.parentFile?.mkdirs()
    file.sink().buffer().use { output ->
        output.write(byteArray)
    }
    return file
}

fun Context.listImageFiles(): List<File> {
    val imagesDir = getImagesDir()
    return imagesDir.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp") }?.toList()
        ?: emptyList()
}

private fun Context.openUriInputStream(uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file", "content" -> openOwnedUriInputStream(uri)
        else -> null
    }
}

private fun buildUploadFileName(originalName: String?, mimeType: String?): String {
    val extension = originalName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it != originalName }
        ?.lowercase()
        ?: mimeType
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
    val safeBaseName = sanitizeUploadBaseName(originalName?.substringBeforeLast('.', originalName))
    return buildString {
        append(safeBaseName)
        append('-')
        append(Uuid.random())
        extension?.let {
            append('.')
            append(it)
        }
    }
}

private fun sanitizeUploadBaseName(rawName: String?): String {
    val cleaned = rawName
        ?.replace(Regex("[^A-Za-z0-9._-]+"), "-")
        ?.trim('-', '.', '_')
        ?.take(48)
    return cleaned?.takeIf { it.isNotBlank() } ?: "upload"
}

private fun decodeBitmapWithBounds(data: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    options.inJustDecodeBounds = false
    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight)
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (srcHeight > reqHeight || srcWidth > reqWidth) {
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
