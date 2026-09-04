package me.rerere.lastchat.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object IosZipArchive {

    actual fun decompressRawDeflate(compressed: ByteArray, expectedUncompressedSize: Int): ByteArray? {
        if (compressed.isEmpty()) return ByteArray(0)
        val outputCapacity = if (expectedUncompressedSize > 0) expectedUncompressedSize else compressed.size * 4
        val output = ByteArray(outputCapacity)

        memScoped {
            val stream = alloc<z_stream>()
            // -15 enables raw DEFLATE stream decoding without zlib/gzip headers
            if (inflateInit2(stream.ptr, -15) != Z_OK) return null

            try {
                compressed.usePinned { inPinned ->
                    output.usePinned { outPinned ->
                        stream.next_in = inPinned.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_in = compressed.size.toUInt()
                        stream.next_out = outPinned.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_out = output.size.toUInt()

                        val ret = inflate(stream.ptr, Z_FINISH)
                        if (ret != Z_STREAM_END && ret != Z_OK) {
                            return null
                        }
                        val produced = (output.size.toUInt() - stream.avail_out).toInt()
                        return output.copyOf(produced)
                    }
                }
            } finally {
                inflateEnd(stream.ptr)
            }
        }
    }

    /**
     * Extracts files from a ZIP archive byte array.
     */
    actual fun extractEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var offset = 0
        val size = zipBytes.size

        while (offset + 30 <= size) {
            // Local file header signature = 0x04034b50 (PK\x03\x04)
            val sig = readInt32LE(zipBytes, offset)
            if (sig != 0x04034b50) break

            val compressionMethod = readInt16LE(zipBytes, offset + 8)
            val compressedSize = readInt32LE(zipBytes, offset + 18)
            val uncompressedSize = readInt32LE(zipBytes, offset + 22)
            val nameLength = readInt16LE(zipBytes, offset + 26)
            val extraLength = readInt16LE(zipBytes, offset + 28)

            val nameOffset = offset + 30
            val nameEnd = nameOffset + nameLength
            if (nameEnd > size) break

            val name = zipBytes.decodeToString(nameOffset, nameEnd).replace('\\', '/').trimStart('/')
            val dataOffset = nameEnd + extraLength
            val dataEnd = dataOffset + compressedSize

            if (dataEnd <= size && !name.endsWith('/')) {
                val compressedData = zipBytes.copyOfRange(dataOffset, dataEnd)
                val decompressed = when (compressionMethod) {
                    0 -> compressedData // Stored
                    8 -> decompressRawDeflate(compressedData, uncompressedSize) ?: compressedData // Deflated
                    else -> compressedData
                }
                result[name] = decompressed
            }

            offset = dataEnd
        }
        return result
    }

    /**
     * Creates a standard ZIP archive (using STORED method 0 for 100% reliable cross-platform compatibility).
     */
    actual fun createStoredZip(entries: Map<String, ByteArray>): ByteArray {
        val output = mutableListOf<Byte>()
        val centralDir = mutableListOf<Byte>()
        var localHeaderOffset = 0

        entries.forEach { (name, data) ->
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val size = data.size

            // Local Header
            val localHeaderStart = output.size
            writeLE32(output, 0x04034b50) // signature
            writeLE16(output, 20)         // min version
            writeLE16(output, 0)          // flags
            writeLE16(output, 0)          // compression: 0 = stored
            writeLE16(output, 0)          // mod time
            writeLE16(output, 0)          // mod date
            writeLE32(output, crc.toInt())// crc-32
            writeLE32(output, size)       // compressed size
            writeLE32(output, size)       // uncompressed size
            writeLE16(output, nameBytes.size) // name length
            writeLE16(output, 0)          // extra length
            output.addAll(nameBytes.toList())
            output.addAll(data.toList())

            // Central Directory Header
            writeLE32(centralDir, 0x02014b50) // signature
            writeLE16(centralDir, 20)         // version made by
            writeLE16(centralDir, 20)         // min version
            writeLE16(centralDir, 0)          // flags
            writeLE16(centralDir, 0)          // compression: 0 = stored
            writeLE16(centralDir, 0)          // mod time
            writeLE16(centralDir, 0)          // mod date
            writeLE32(centralDir, crc.toInt())// crc-32
            writeLE32(centralDir, size)       // compressed size
            writeLE32(centralDir, size)       // uncompressed size
            writeLE16(centralDir, nameBytes.size) // name length
            writeLE16(centralDir, 0)          // extra length
            writeLE16(centralDir, 0)          // comment length
            writeLE16(centralDir, 0)          // disk start
            writeLE16(centralDir, 0)          // internal attr
            writeLE32(centralDir, 0)          // external attr
            writeLE32(centralDir, localHeaderStart) // relative offset of local header
            centralDir.addAll(nameBytes.toList())
        }

        val centralDirOffset = output.size
        val centralDirSize = centralDir.size
        output.addAll(centralDir)

        // End of Central Directory Record
        writeLE32(output, 0x06054b50) // signature
        writeLE16(output, 0)          // disk number
        writeLE16(output, 0)          // start disk
        writeLE16(output, entries.size) // total entries on disk
        writeLE16(output, entries.size) // total entries
        writeLE32(output, centralDirSize) // size of central dir
        writeLE32(output, centralDirOffset) // offset of central dir
        writeLE16(output, 0)          // comment length

        return output.toByteArray()
    }

    private fun readInt16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLE16(out: MutableList<Byte>, value: Int) {
        out.add((value and 0xFF).toByte())
        out.add(((value ushr 8) and 0xFF).toByte())
    }

    private fun writeLE32(out: MutableList<Byte>, value: Int) {
        out.add((value and 0xFF).toByte())
        out.add(((value ushr 8) and 0xFF).toByte())
        out.add(((value ushr 16) and 0xFF).toByte())
        out.add(((value ushr 24) and 0xFF).toByte())
    }

    private fun crc32(data: ByteArray): Long {
        var crc = 0xFFFFFFFFL
        for (b in data) {
            val byteVal = (b.toInt() and 0xFF).toLong()
            crc = crc xor byteVal
            for (i in 0 until 8) {
                crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
            }
        }
        return (crc xor 0xFFFFFFFFL) and 0xFFFFFFFFL
    }
}
