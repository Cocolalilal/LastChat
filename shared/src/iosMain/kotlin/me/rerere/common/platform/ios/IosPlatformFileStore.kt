package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.common.platform.PlatformFileStore
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSURL
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosPlatformFileStore(
    private val directoryName: String = "LastChat",
) : PlatformFileStore {
    private val fileManager = NSFileManager.defaultManager
    private val rootPath: String by lazy {
        val applicationSupport = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: error("Application Support directory is unavailable")
        "$applicationSupport/$directoryName".also { path ->
            fileManager.createDirectoryAtPath(
                path = path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
    }

    override suspend fun readBytes(path: String): ByteArray? {
        val data = NSData.dataWithContentsOfFile(resolve(path)) ?: return null
        if (data.length == 0uL) return ByteArray(0)
        return ByteArray(data.length.toInt()).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
    }

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
        val destination = resolve(path)
        destination.substringBeforeLast('/', missingDelimiterValue = rootPath).let { parent ->
            fileManager.createDirectoryAtPath(
                path = parent,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        check(data.writeToFile(destination, atomically = true)) {
            "Unable to write app file: $path"
        }
    }

    override suspend fun delete(path: String): Boolean {
        val destination = resolve(path)
        if (!fileManager.fileExistsAtPath(destination)) return false
        return fileManager.removeItemAtPath(destination, error = null)
    }

    override suspend fun exists(path: String): Boolean = fileManager.fileExistsAtPath(resolve(path))

    override suspend fun lastModified(path: String): Long? {
        val attributes = fileManager.attributesOfItemAtPath(resolve(path), error = null) ?: return null
        val date = attributes[NSFileModificationDate] as? NSDate ?: return null
        return (date.timeIntervalSince1970 * 1_000.0).toLong()
    }

    override fun localUrl(path: String): String? = NSURL.fileURLWithPath(resolve(path)).absoluteString

    private fun resolve(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.split('/').none { it == ".." }) { "Path traversal is not allowed" }
        return "$rootPath/$normalized"
    }
}
