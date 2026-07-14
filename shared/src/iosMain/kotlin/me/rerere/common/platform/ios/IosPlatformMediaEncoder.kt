package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.common.platform.PlatformMediaEncoder
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class IosPlatformMediaEncoder : PlatformMediaEncoder {
    override fun encodeImage(url: String, withPrefix: Boolean): Result<String> = runCatching {
        passthrough(url, withPrefix)?.let { return@runCatching it }
        val path = filePath(url)
        val image = UIImage.imageWithContentsOfFile(path)
            ?: throw IllegalArgumentException("Unable to decode image: $url")
        val jpeg = UIImageJPEGRepresentation(image, compressionQuality = 0.8)
            ?: error("Unable to encode image as JPEG")
        jpeg.encoded(mimeType = "image/jpeg", withPrefix = withPrefix)
    }

    override fun encodeVideo(url: String, withPrefix: Boolean): Result<String> = runCatching {
        passthrough(url, withPrefix)?.let { return@runCatching it }
        readFile(url).encoded(mimeType = videoMimeType(filePath(url)), withPrefix = withPrefix)
    }

    override fun encodeAudio(url: String, withPrefix: Boolean): Result<String> = runCatching {
        passthrough(url, withPrefix)?.let { return@runCatching it }
        readFile(url).encoded(mimeType = audioMimeType(filePath(url)), withPrefix = withPrefix)
    }

    private fun passthrough(url: String, withPrefix: Boolean): String? = when {
        url.startsWith("data:") -> if (withPrefix) url else url.substringAfter("base64,")
        url.startsWith("http:") || url.startsWith("https:") -> url
        else -> null
    }

    private fun readFile(url: String): NSData = NSData.dataWithContentsOfFile(filePath(url))
        ?: throw IllegalArgumentException("File does not exist or cannot be read: $url")

    private fun filePath(url: String): String {
        require(url.startsWith("file://")) { "Unsupported URL format: $url" }
        return NSURL(string = url).path ?: throw IllegalArgumentException("Invalid file URI: $url")
    }

    private fun videoMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }

    private fun audioMimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "m4a", "mp4" -> "audio/mp4"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        else -> "audio/mpeg"
    }

    private fun NSData.encoded(mimeType: String, withPrefix: Boolean): String {
        val bytes = ByteArray(length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
        }
        val encoded = Base64.encode(bytes)
        return if (withPrefix) "data:$mimeType;base64,$encoded" else encoded
    }
}
