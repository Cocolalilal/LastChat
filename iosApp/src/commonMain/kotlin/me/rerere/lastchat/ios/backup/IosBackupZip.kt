package me.rerere.lastchat.ios.backup

/**
 * Minimal zip reader for LastChat Android backup archives. It parses the central
 * directory and extracts only the requested entries, so large database/file entries
 * are never inflated during a configuration-only restore.
 */
internal const val IOS_BACKUP_SETTINGS_ENTRY = "settings.json"
internal const val IOS_BACKUP_MANIFEST_ENTRY = "backup_manifest.json"

internal class IosZipEntry(
    val name: String,
    val size: Long,
    val crc32: Long,
    val method: Int,
    val data: ByteArray?,
)

internal expect fun inflateRawDeflate(data: ByteArray, maxBytes: Int): ByteArray?

internal object IosBackupZip {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val CEN_SIGNATURE = 0x02014b50L
    private const val LOC_SIGNATURE = 0x04034b50L
    private const val METHOD_STORE = 0
    private const val METHOD_DEFLATE = 8

    /** Returns null when the archive is not a readable zip file. */
    fun read(
        archive: ByteArray,
        wanted: Set<String>,
        maxEntryBytes: Int = 64 * 1024 * 1024,
    ): List<IosZipEntry>? {
        val eocd = findEndOfCentralDirectory(archive) ?: return null
        val entryCount = readU16(archive, eocd + 10)
        val directorySize = readU32(archive, eocd + 12)
        val directoryOffset = readU32(archive, eocd + 16)
        if (directoryOffset <= 0 || directoryOffset > Int.MAX_VALUE) return null
        if (directoryOffset + directorySize > archive.size) return null

        val entries = mutableListOf<IosZipEntry>()
        var offset = directoryOffset.toInt()
        var remaining = entryCount
        while (remaining > 0 && offset + 46 <= archive.size) {
            if (readU32(archive, offset) != CEN_SIGNATURE) return entries.takeIf { it.isNotEmpty() }
            val method = readU16(archive, offset + 10)
            val crc = readU32(archive, offset + 16)
            val compressedSize = readU32(archive, offset + 20)
            val uncompressedSize = readU32(archive, offset + 24)
            val nameLength = readU16(archive, offset + 28)
            val extraLength = readU16(archive, offset + 30)
            val commentLength = readU16(archive, offset + 32)
            val localOffset = readU32(archive, offset + 42)
            val name = archive
                .copyOfRange(offset + 46, offset + 46 + nameLength)
                .decodeToString()
                .replace('\\', '/')
                .trimStart('/')
            if (name in wanted && !name.endsWith("/") && uncompressedSize <= maxEntryBytes.toLong()) {
                val data = extractEntry(
                    archive = archive,
                    localOffset = localOffset,
                    method = method,
                    compressedSize = compressedSize,
                    maxEntryBytes = maxEntryBytes,
                )?.takeIf { it.size.toLong() == uncompressedSize && Crc32.of(it) == crc }
                entries += IosZipEntry(
                    name = name,
                    size = uncompressedSize,
                    crc32 = crc,
                    method = method,
                    data = data,
                )
            }
            offset += 46 + nameLength + extraLength + commentLength
            remaining--
        }
        return entries
    }

    private fun findEndOfCentralDirectory(archive: ByteArray): Int? {
        val lowestOffset = maxOf(0, archive.size - 22 - 65535)
        var offset = archive.size - 22
        while (offset >= lowestOffset) {
            if (readU32(archive, offset) == EOCD_SIGNATURE) return offset
            offset--
        }
        return null
    }

    private fun extractEntry(
        archive: ByteArray,
        localOffset: Long,
        method: Int,
        compressedSize: Long,
        maxEntryBytes: Int,
    ): ByteArray? {
        if (localOffset <= 0 || localOffset + 30 > archive.size) return null
        if (readU32(archive, localOffset.toInt()) != LOC_SIGNATURE) return null
        val nameLength = readU16(archive, localOffset.toInt() + 26)
        val extraLength = readU16(archive, localOffset.toInt() + 28)
        val dataStart = localOffset.toInt() + 30 + nameLength + extraLength
        if (compressedSize > maxEntryBytes || dataStart + compressedSize > archive.size) return null
        val compressed = archive.copyOfRange(dataStart, dataStart + compressedSize.toInt())
        return when (method) {
            METHOD_STORE -> compressed
            METHOD_DEFLATE -> inflateRawDeflate(compressed, maxEntryBytes)
            else -> null
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        if (offset + 2 > data.size) return 0
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0
        return (readU16(data, offset).toLong()) or (readU16(data, offset + 2).toLong() shl 16)
    }
}

internal object Crc32 {
    private val table = IntArray(256).also { table ->
        for (index in 0 until 256) {
            var value = index
            repeat(8) {
                value = if (value and 1 != 0) {
                    0xEDB88320.toInt() xor (value ushr 1)
                } else {
                    value ushr 1
                }
            }
            table[index] = value
        }
    }

    fun of(data: ByteArray): Long {
        var crc = -1
        for (byte in data) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }
        return crc.inv().toLong() and 0xFFFFFFFFL
    }
}
