package me.rerere.document

/**
 * Store-or-deflate zip helper used by DOCX extraction, SKILL.md packages, and
 * iOS backup archives. Deflate uses the existing [inflateRawDeflate] inflater.
 */
object PortableZip {
    const val MAX_SKILL_FILES = 256
    const val MAX_SKILL_BYTES = 32 * 1024 * 1024
    const val MAX_SKILL_FILE_BYTES = 8 * 1024 * 1024

    fun isArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun extractEntry(archive: ByteArray, name: String, maxBytes: Int = MAX_SKILL_FILE_BYTES): ByteArray? {
        return walkCentralDirectory(archive) { entryName, method, compressed, uncompressedSize ->
            if (entryName == name) {
                inflateEntry(method, compressed, uncompressedSize.toInt().coerceAtLeast(compressed.size).coerceAtMost(maxBytes))
            } else {
                null
            }
        }
    }

    fun extractEntries(
        archive: ByteArray,
        maxFiles: Int = MAX_SKILL_FILES,
        maxTotalBytes: Int = MAX_SKILL_BYTES,
        maxSingleFileBytes: Int = MAX_SKILL_FILE_BYTES,
    ): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        walkCentralDirectory(archive) { rawName, method, compressed, uncompressedSize ->
            val trimmed = rawName.replace('\\', '/').trimStart('/')
            if (trimmed.isEmpty() || trimmed.endsWith("/")) return@walkCentralDirectory null
            val name = normalizeArchivePath(trimmed)
            require(entries.size < maxFiles) { "Skill package contains too many files" }
            require(uncompressedSize <= maxSingleFileBytes.toLong()) { "$name is too large" }
            val inflated = inflateEntry(method, compressed, uncompressedSize.toInt().coerceAtLeast(compressed.size))
                ?: error("Unable to inflate $name")
            require(inflated.size <= maxSingleFileBytes) { "$name is too large" }
            total += inflated.size
            require(total <= maxTotalBytes) { "Skill package is too large" }
            entries[name] = inflated
            null
        }
        return entries
    }

    fun writeStoreArchive(files: Map<String, ByteArray>): ByteArray {
        val locals = mutableListOf<ByteArray>()
        val centrals = mutableListOf<ByteArray>()
        var offset = 0
        files.forEach { (rawName, data) ->
            val name = rawName.replace('\\', '/').trimStart('/').encodeToByteArray()
            val crc = crc32(data)
            val local = ByteArray(30 + name.size + data.size)
            writeU32(local, 0, LOC_SIGNATURE)
            writeU16(local, 4, 20)
            writeU16(local, 6, 0)
            writeU16(local, 8, METHOD_STORE)
            writeU16(local, 10, 0)
            writeU16(local, 12, 0)
            writeU32(local, 14, crc)
            writeU32(local, 18, data.size.toLong())
            writeU32(local, 22, data.size.toLong())
            writeU16(local, 26, name.size)
            writeU16(local, 28, 0)
            name.copyInto(local, 30)
            data.copyInto(local, 30 + name.size)
            locals += local

            val central = ByteArray(46 + name.size)
            writeU32(central, 0, CEN_SIGNATURE)
            writeU16(central, 4, 20)
            writeU16(central, 6, 20)
            writeU16(central, 8, 0)
            writeU16(central, 10, METHOD_STORE)
            writeU16(central, 12, 0)
            writeU16(central, 14, 0)
            writeU32(central, 16, crc)
            writeU32(central, 20, data.size.toLong())
            writeU32(central, 24, data.size.toLong())
            writeU16(central, 28, name.size)
            writeU16(central, 30, 0)
            writeU16(central, 32, 0)
            writeU16(central, 34, 0)
            writeU16(central, 36, 0)
            writeU32(central, 38, 0)
            writeU32(central, 42, offset.toLong())
            name.copyInto(central, 46)
            centrals += central
            offset += local.size
        }
        val centralSize = centrals.sumOf { it.size }
        val eocd = ByteArray(22)
        writeU32(eocd, 0, EOCD_SIGNATURE)
        writeU16(eocd, 4, 0)
        writeU16(eocd, 6, 0)
        writeU16(eocd, 8, files.size)
        writeU16(eocd, 10, files.size)
        writeU32(eocd, 12, centralSize.toLong())
        writeU32(eocd, 16, offset.toLong())
        writeU16(eocd, 20, 0)
        val archive = ByteArray(offset + centralSize + eocd.size)
        var cursor = 0
        locals.forEach { chunk ->
            chunk.copyInto(archive, cursor)
            cursor += chunk.size
        }
        centrals.forEach { chunk ->
            chunk.copyInto(archive, cursor)
            cursor += chunk.size
        }
        eocd.copyInto(archive, cursor)
        return archive
    }

    fun normalizeArchivePath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized.split('/').none { it == ".." || it.isBlank() }) {
            "Unsafe archive path"
        }
        return normalized
    }

    private fun inflateEntry(method: Int, compressed: ByteArray, maxBytes: Int): ByteArray? {
        return when (method) {
            METHOD_STORE -> compressed
            METHOD_DEFLATE -> inflateRawDeflate(compressed, maxBytes)
            else -> null
        }
    }

    private inline fun walkCentralDirectory(
        archive: ByteArray,
        onEntry: (name: String, method: Int, compressed: ByteArray, uncompressedSize: Long) -> ByteArray?,
    ): ByteArray? {
        val eocd = findEndOfCentralDirectory(archive) ?: return null
        val entryCount = readU16(archive, eocd + 10)
        val directorySize = readU32(archive, eocd + 12)
        val directoryOffset = readU32(archive, eocd + 16)
        if (directoryOffset <= 0 || directoryOffset + directorySize > archive.size) return null
        var offset = directoryOffset.toInt()
        var remaining = entryCount
        while (remaining > 0 && offset + 46 <= archive.size) {
            if (readU32(archive, offset) != CEN_SIGNATURE) return null
            val method = readU16(archive, offset + 10)
            val compressedSize = readU32(archive, offset + 20)
            val uncompressedSize = readU32(archive, offset + 24)
            val nameLength = readU16(archive, offset + 28)
            val extraLength = readU16(archive, offset + 30)
            val commentLength = readU16(archive, offset + 32)
            val localHeaderOffset = readU32(archive, offset + 42).toInt()
            val entryName = archive.decodeToString(offset + 46, offset + 46 + nameLength)
            if (localHeaderOffset + 30 > archive.size) return null
            val localNameLength = readU16(archive, localHeaderOffset + 26)
            val localExtraLength = readU16(archive, localHeaderOffset + 28)
            val dataStart = localHeaderOffset + 30 + localNameLength + localExtraLength
            val dataEnd = dataStart + compressedSize.toInt()
            if (dataEnd > archive.size) return null
            val compressed = archive.copyOfRange(dataStart, dataEnd)
            onEntry(entryName.replace('\\', '/'), method, compressed, uncompressedSize)?.let { return it }
            offset += 46 + nameLength + extraLength + commentLength
            remaining--
        }
        return null
    }
}

private const val EOCD_SIGNATURE = 0x06054b50L
private const val CEN_SIGNATURE = 0x02014b50L
private const val LOC_SIGNATURE = 0x04034b50L
private const val METHOD_STORE = 0
private const val METHOD_DEFLATE = 8

private fun findEndOfCentralDirectory(archive: ByteArray): Int? {
    val min = (archive.size - 22 - 65_535).coerceAtLeast(0)
    for (offset in archive.size - 22 downTo min) {
        if (readU32(archive, offset) == EOCD_SIGNATURE) return offset
    }
    return null
}

private fun readU16(data: ByteArray, offset: Int): Int {
    if (offset + 1 >= data.size) return 0
    return (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
}

private fun readU32(data: ByteArray, offset: Int): Long {
    if (offset + 3 >= data.size) return 0
    return (data[offset].toLong() and 0xff) or
        ((data[offset + 1].toLong() and 0xff) shl 8) or
        ((data[offset + 2].toLong() and 0xff) shl 16) or
        ((data[offset + 3].toLong() and 0xff) shl 24)
}

private fun writeU16(data: ByteArray, offset: Int, value: Int) {
    data[offset] = (value and 0xFF).toByte()
    data[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

private fun writeU32(data: ByteArray, offset: Int, value: Long) {
    writeU16(data, offset, (value and 0xFFFF).toInt())
    writeU16(data, offset + 2, ((value ushr 16) and 0xFFFF).toInt())
}

private fun crc32(data: ByteArray): Long {
    var crc = -1
    for (byte in data) {
        crc = (crc ushr 8) xor CRC32_TABLE[(crc xor byte.toInt()) and 0xFF]
    }
    return crc.inv().toLong() and 0xFFFFFFFFL
}

private val CRC32_TABLE: IntArray = IntArray(256) { index ->
    var value = index
    repeat(8) {
        value = if (value and 1 != 0) 0xEDB88320.toInt() xor (value ushr 1) else value ushr 1
    }
    value
}
