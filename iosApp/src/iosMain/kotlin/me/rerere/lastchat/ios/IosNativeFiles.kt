package me.rerere.lastchat.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual object IosNativeFiles {
    private val webRoot: String? by lazy {
        val bundle = NSBundle.mainBundle.resourcePath ?: return@lazy null
        val candidate = "$bundle/webui"
        val index = "$candidate/index.html"
        if (platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(index)) candidate else null
    }

    actual val webUiBundled: Boolean get() = webRoot != null

    actual fun loadWebAsset(relativePath: String): ByteArray? {
        val root = webRoot ?: return null
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        if (normalized.split('/').any { it == ".." }) return null
        return readPath("$root/$normalized")
    }

    actual fun readUrl(url: String): ByteArray? {
        if (url.isBlank()) return null
        val nsUrl = NSURL.URLWithString(url) ?: return readPath(url.removePrefix("file://"))
        return nsDataToBytes(NSData.dataWithContentsOfURL(nsUrl))
            ?: nsUrl.path?.let(::readPath)
    }

    private fun readPath(path: String): ByteArray? {
        if (path.isBlank()) return null
        return nsDataToBytes(NSData.dataWithContentsOfFile(path))
    }

    private fun nsDataToBytes(data: NSData?): ByteArray? {
        if (data == null) return null
        if (data.length == 0uL) return ByteArray(0)
        return ByteArray(data.length.toInt()).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    }
}
