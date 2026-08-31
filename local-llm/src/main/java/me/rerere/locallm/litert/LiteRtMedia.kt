package me.rerere.locallm.litert

import android.content.Context
import android.net.Uri
import android.util.Base64
import me.rerere.common.platform.PlatformLog
import java.io.File

/** Resolves LastChat attachment URLs (file / content / data / absolute path) into raw bytes for LiteRT. */
internal object LiteRtMedia {

    private const val TAG = "LiteRtMedia"

    fun readBytes(context: Context, url: String): ByteArray? {
        return runCatching {
            when {
                url.startsWith("data:") -> {
                    val base64 = url.substringAfter(',', missingDelimiterValue = "")
                    if (base64.isBlank()) null else Base64.decode(base64, Base64.DEFAULT)
                }

                url.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(url))?.use { it.readBytes() }
                }

                url.startsWith("file://") -> {
                    Uri.parse(url).path?.let { File(it).takeIf(File::exists)?.readBytes() }
                }

                else -> File(url).takeIf(File::exists)?.readBytes()
            }
        }.onFailure {
            PlatformLog.w(TAG, "Failed to read media bytes from $url: ${it.message}")
        }.getOrNull()
    }
}
