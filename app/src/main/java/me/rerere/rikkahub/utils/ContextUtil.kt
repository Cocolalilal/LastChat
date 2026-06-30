package me.rerere.rikkahub.utils

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import me.rerere.common.http.urlDecode
import okio.buffer
import okio.sink
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R

private const val TAG = "ContextUtil"

/**
 * Read clipboard data as text
 */
fun Context.readClipboardText(): String {
    val clipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboardManager.primaryClip ?: return ""
    val item = clip.getItemAt(0) ?: return ""
    return item.text.toString()
}

/**
 * 发起添加群流程
 *
 * @param key 由官网生成的key
 * @return 返回true表示呼起手Q成功，返回false表示呼起失败
 */
fun Context.joinQQGroup(key: String?): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setData(("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D$key").toUri())
    // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
        return true
    } catch (e: java.lang.Exception) {
        // 未安装手Q或安装的版本不支持
        return false
    }
}

/**
 * Write text into clipboard
 */
fun Context.writeClipboardText(text: String) {
    val clipboardManager =
        getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    runCatching {
        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        Log.i(TAG, "writeClipboardText: $text")
    }.onFailure {
        Log.e(TAG, "writeClipboardText: $text", it)
        Toast.makeText(this, getString(R.string.clipboard_write_failed), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Open a url
 */
fun Context.openUrl(url: String) {
    Log.i(TAG, "openUrl: $url")
    runCatching {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        intent.launchUrl(this, url.toUri())
    }.onFailure {
        it.printStackTrace()
        Toast.makeText(this, getString(R.string.open_url_failed, url), Toast.LENGTH_SHORT).show()
    }
}

fun Context.openAttachmentUri(uri: Uri, mimeType: String? = null): Boolean {
    val normalizedUri = normalizeAttachmentUriForViewing(uri)
    val resolvedMimeType = mimeType?.takeIf { it.isNotBlank() } ?: getFileMimeType(uri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (normalizedUri.scheme in listOf("http", "https")) {
            data = normalizedUri
        } else if (resolvedMimeType != null) {
            setDataAndType(normalizedUri, resolvedMimeType)
        } else {
            data = normalizedUri
        }
    }

    return runCatching {
        startActivity(intent)
        true
    }.onFailure { error ->
        Log.e(TAG, "Failed to open attachment: $normalizedUri", error)
        Toast.makeText(this, getString(R.string.open_attachment_failed), Toast.LENGTH_SHORT).show()
    }.getOrDefault(false)
}

private fun Context.normalizeAttachmentUriForViewing(uri: Uri): Uri {
    if (uri.scheme != "file") return uri

    val file = runCatching { uri.toFile() }.getOrNull() ?: return uri
    return runCatching {
        FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
    }.getOrElse {
        uri
    }
}

fun Context.getActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun Context.getComponentActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) {
            return context
        }
        context = context.baseContext
    }
    return null
}

fun Context.exportImage(
    activity: Activity,
    bitmap: Bitmap,
    fileName: String = "LastChat_${System.currentTimeMillis()}.png"
) {
    // 检查存储权限（Android 9及以下需要）
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
            return
        }
    }

    // 保存到相册
    var outputStream: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                outputStream = contentResolver.openOutputStream(it)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream!!)
            }
        } else {
            // Android 9及以下直接写入文件
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, fileName)
            outputStream = image.sink().buffer().outputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // 通知图库更新
            @Suppress("DEPRECATION")
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(image)
            sendBroadcast(mediaScanIntent)
        }
        Log.i(TAG, "Image saved successfully: $fileName")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save image", e)
    } finally {
        outputStream?.close()
    }
}

fun Context.exportImageFile(
    activity: Activity,
    file: File,
    fileName: String = "LastChat_${System.currentTimeMillis()}.png"
) {
    // 检查存储权限（Android 9及以下需要）
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
            return
        }
    }

    // 保存到相册
    var outputStream: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore API
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                outputStream = contentResolver.openOutputStream(it)
                file.inputStream().copyTo(outputStream!!)
            }
        } else {
            // Android 9及以下直接写入文件
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, fileName)
            file.copyTo(image, overwrite = true)

            // 通知图库更新
            @Suppress("DEPRECATION")
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(image)
            sendBroadcast(mediaScanIntent)
        }
        Log.i(TAG, "Image file saved successfully: $fileName")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to save image file", e)
    } finally {
        outputStream?.close()
    }
}

fun shareTextFile(context: Context, fileName: String, content: String) {
    try {
        val dir = context.cacheDir
        val file = File(dir, fileName)
        file.writeText(content)
        // Ensure authority matches what is defined in AndroidManifest.xml
        // Usually it is ${applicationId}.fileprovider
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_text_file_chooser_title))
        )
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(
            context,
            context.getString(R.string.share_text_file_export_failed, e.message ?: ""),
            Toast.LENGTH_SHORT
        ).show()
    }
}

internal fun resolveAppOwnedFileProviderFile(
    authority: String?,
    encodedPath: String?,
    expectedAuthority: String,
    cacheDir: File,
    filesDir: File,
    externalFilesDir: File?,
): File? {
    if (authority != expectedAuthority || encodedPath.isNullOrBlank()) return null

    val normalizedPath = encodedPath.removePrefix("/")
    val separatorIndex = normalizedPath.indexOf('/')
    if (separatorIndex <= 0) return null

    val rootName = normalizedPath.substring(0, separatorIndex)
    val encodedRelativePath = normalizedPath.substring(separatorIndex + 1)
    val rootDir = when (rootName) {
        "cache" -> cacheDir
        "upload" -> filesDir
        "external_files" -> externalFilesDir ?: return null
        else -> return null
    }
    val relativePath = encodedRelativePath.urlDecode(plusAsSpace = true)
    val candidate = if (relativePath.isBlank()) rootDir else File(rootDir, relativePath)
    val canonicalRoot = rootDir.canonicalFile
    val canonicalCandidate = candidate.canonicalFile

    return canonicalCandidate.takeIf { file ->
        file.path == canonicalRoot.path || file.path.startsWith(canonicalRoot.path + File.separator)
    }
}

fun Context.openOwnedUriInputStream(uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> runCatching { uri.toFile().inputStream() }.getOrNull()
        "content" -> {
            runCatching { contentResolver.openInputStream(uri) }.getOrNull()
                ?: resolveAppOwnedFileProviderFile(
                    authority = uri.authority,
                    encodedPath = uri.encodedPath,
                    expectedAuthority = "${packageName}.fileprovider",
                    cacheDir = cacheDir,
                    filesDir = filesDir,
                    externalFilesDir = getExternalFilesDir(null),
                )?.inputStream()
        }

        else -> null
    }
}

suspend fun Context.saveToDownloads(uri: Uri, fileName: String) {
    // Check permissions for legacy Android
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val activity = this.getActivity()
            if (activity != null) {
                withContext(Dispatchers.Main) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        112 // Request code for downloads
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@saveToDownloads,
                        getString(R.string.downloads_permission_required),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }
    }

    withContext(Dispatchers.IO) {
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null
        try {
            inputStream = openOwnedUriInputStream(uri)
            if (inputStream == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@saveToDownloads,
                        getString(R.string.downloads_read_source_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@withContext
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val dstUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (dstUri != null) {
                    outputStream = contentResolver.openOutputStream(dstUri)
                    inputStream.copyTo(outputStream!!)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@saveToDownloads,
                            getString(R.string.downloads_saved, fileName),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@saveToDownloads,
                            getString(R.string.downloads_create_entry_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, fileName)
                outputStream = destFile.sink().buffer().outputStream()
                inputStream.copyTo(outputStream!!)
                
                // Notify media scanner
                @Suppress("DEPRECATION")
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(destFile)
                sendBroadcast(mediaScanIntent)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@saveToDownloads,
                        getString(R.string.downloads_saved, fileName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to downloads", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@saveToDownloads,
                    getString(R.string.downloads_save_failed, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
