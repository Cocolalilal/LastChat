package me.rerere.common.platform.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import me.rerere.common.platform.PlatformMediaEncoder
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AndroidPlatformMediaEncoder : PlatformMediaEncoder {
    override fun encodeImage(url: String, withPrefix: Boolean): Result<String> = runCatching {
        when {
            url.startsWith("file://") -> {
                val file = url.toFile()
                if (!file.isSupportedType()) {
                    convertToJpeg(file)
                    println("File converted to JPEG format: ${file.absolutePath}")
                }
                if (file.guessMimeType().getOrNull() != "image/webp") {
                    convertToJpeg(file)
                    println("File converted to JPEG format: ${file.absolutePath}")
                }
                val encoded = base64Encode(file.readByteArray())
                if (withPrefix) "data:${file.guessMimeType().getOrThrow()};base64,$encoded" else encoded
            }

            url.startsWith("data:") -> url
            url.startsWith("http:") -> url
            else -> throw IllegalArgumentException("Unsupported URL format: $url")
        }
    }

    override fun encodeVideo(url: String, withPrefix: Boolean): Result<String> = runCatching {
        when {
            url.startsWith("file://") -> {
                val file = url.toFile()
                val encoded = base64Encode(file.readByteArray())
                if (withPrefix) "data:video/mp4;base64,$encoded" else encoded
            }
            url.startsWith("data:") -> {
                if (withPrefix) url else url.substringAfter("base64,")
            }
            url.startsWith("http:") -> url
            else -> throw IllegalArgumentException("Unsupported URL format: $url")
        }
    }

    override fun encodeAudio(url: String, withPrefix: Boolean): Result<String> = runCatching {
        when {
            url.startsWith("file://") -> {
                val file = url.toFile()
                val encoded = base64Encode(file.readByteArray())
                if (withPrefix) "data:audio/mp3;base64,$encoded" else encoded
            }
            url.startsWith("data:") -> {
                if (withPrefix) url else url.substringAfter("base64,")
            }
            url.startsWith("http:") -> url
            else -> throw IllegalArgumentException("Unsupported URL format: $url")
        }
    }

    private fun String.toFile(): File {
        if (!startsWith("file://")) {
            throw IllegalArgumentException("Unsupported URL format: $this")
        }
        val filePath = toUri().path ?: throw IllegalArgumentException("Invalid file URI: $this")
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $this")
        }
        return file
    }

    private fun convertToJpeg(file: File) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val maxDimension = 4096
        var inSampleSize = 1
        if (bounds.outWidth > maxDimension || bounds.outHeight > maxDimension) {
            val halfW = bounds.outWidth / 2
            val halfH = bounds.outHeight / 2
            while ((halfW / inSampleSize) >= maxDimension && (halfH / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }

        val opts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalStateException("Failed to decode image: ${file.absolutePath}")
        file.sink().buffer().outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        }
        bitmap.recycle()
    }

    private fun File.isSupportedType(): Boolean {
        val mimeType = guessMimeType().getOrNull() ?: return false
        return mimeType in supportedTypes
    }

    private fun File.guessMimeType(): Result<String> = runCatching {
        inputStream().use { input ->
            val bytes = ByteArray(16)
            val read = input.read(bytes)
            if (read < 12) error("File too short to determine MIME type")

            println("guessMimeType bytes = ${bytes.joinToString(",")}")

            if (bytes.copyOfRange(4, 12).toString(Charsets.US_ASCII) == "ftypheic") {
                return@runCatching "image/heic"
            }

            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                return@runCatching "image/jpeg"
            }

            if (bytes.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                )
            ) {
                return@runCatching "image/png"
            }

            if (bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12)
                    .toString(Charsets.US_ASCII) == "WEBP"
            ) {
                return@runCatching "image/webp"
            }

            val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
            if (header == "GIF89a" || header == "GIF87a") {
                return@runCatching "image/gif"
            }

            error(
                "Failed to guess MIME type: $header, ${
                    bytes.joinToString(",") {
                        it.toUByte().toString()
                    }
                }"
            )
        }
    }

    private fun File.readByteArray(): ByteArray {
        return source().buffer().use { source ->
            source.readByteArray()
        }
    }

    private companion object {
        val supportedTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)
