package me.rerere.common.archive

/**
 * Portable tar.bz2 reader matching Android Sherpa install layout:
 * only the required relative paths are extracted, archive names may have a
 * directory prefix, and `..` path segments are rejected.
 */
object PortableTarBz2 {
    fun extractRequiredFiles(
        archive: ByteArray,
        requiredPaths: Set<String>,
    ): Map<String, ByteArray> {
        val tarBytes = BZip2Decoder.decompress(archive)
        val entries = readTarFiles(tarBytes)
        val normalizedRequired = requiredPaths
            .map { it.replace('\\', '/').trimStart('/') }
            .filter { it.isNotBlank() }
            .associateBy { it }
        val found = linkedMapOf<String, ByteArray>()
        for (entry in entries) {
            if (!isSafeRelativePath(entry.name)) continue
            val archivePath = entry.name.replace('\\', '/').trimStart('/')
            val relative = if (normalizedRequired.isEmpty()) {
                archivePath
            } else {
                normalizedRequired.keys.firstOrNull { required ->
                    archivePath == required || archivePath.endsWith("/$required")
                }
            } ?: continue
            if (!isSafeRelativePath(relative)) {
                error("Unsafe archive path: ${entry.name}")
            }
            found[relative] = entry.bytes
            if (normalizedRequired.isNotEmpty() && found.size == normalizedRequired.size) break
        }
        if (normalizedRequired.isNotEmpty()) {
            val missing = normalizedRequired.keys - found.keys
            if (missing.isNotEmpty()) {
                error("Archive does not contain: ${missing.joinToString()}")
            }
        }
        return found
    }

    internal fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/').trimStart('/')
        if (normalized.isBlank()) return false
        return normalized.split('/').none { it.isEmpty() || it == ".." }
    }
}

internal data class TarFileEntry(
    val name: String,
    val bytes: ByteArray,
)

internal fun readTarFiles(bytes: ByteArray): List<TarFileEntry> {
    val entries = mutableListOf<TarFileEntry>()
    var offset = 0
    var pendingLongName: String? = null
    while (offset + 512 <= bytes.size) {
        val header = bytes.copyOfRange(offset, offset + 512)
        offset += 512
        if (header.all { it == 0.toByte() }) break
        val size = parseOctal(header, 124, 12)
        val typeFlag = header[156].toInt().toChar()
        val rawName = pendingLongName ?: headerName(header)
        pendingLongName = null
        val dataEnd = offset + size.toInt()
        val data = if (size > 0 && dataEnd <= bytes.size) {
            bytes.copyOfRange(offset, dataEnd)
        } else {
            ByteArray(0)
        }
        val padded = ((size + 511) / 512) * 512
        offset += padded.toInt()
        when (typeFlag) {
            'L' -> pendingLongName = data.decodeToString().trimEnd('\u0000')
            '5' -> Unit
            '0', '\u0000' -> {
                if (rawName.isNotBlank()) {
                    entries += TarFileEntry(rawName.trimStart('/'), data)
                }
            }
            else -> Unit
        }
    }
    return entries
}

private fun headerName(header: ByteArray): String {
    val name = cString(header, 0, 100)
    val magic = cString(header, 257, 6)
    if (magic.startsWith("ustar")) {
        val prefix = cString(header, 345, 155)
        if (prefix.isNotBlank()) return "$prefix/$name"
    }
    return name
}

private fun cString(bytes: ByteArray, start: Int, length: Int): String {
    val end = (start until start + length).firstOrNull { bytes[it] == 0.toByte() } ?: (start + length)
    return bytes.decodeToString(start, end)
}

private fun parseOctal(bytes: ByteArray, start: Int, length: Int): Long {
    var value = 0L
    for (i in start until start + length) {
        val ch = bytes[i].toInt().toChar()
        if (ch == '\u0000' || ch == ' ') continue
        if (ch !in '0'..'7') break
        value = (value shl 3) + (ch - '0')
    }
    return value
}

internal object BZip2Decoder {
    fun decompress(input: ByteArray): ByteArray {
        val reader = BitReader(input)
        if (reader.readByte() != 'B'.code || reader.readByte() != 'Z'.code || reader.readByte() != 'h'.code) {
            error("Not a bzip2 archive")
        }
        val level = reader.readByte()
        if (level !in '1'.code..'9'.code) error("Invalid bzip2 block size")
        val blockSize100k = level - '0'.code
        val output = ArrayList<Byte>(blockSize100k * 100_000)
        while (true) {
            val magic = reader.readBits(48)
            when (magic) {
                BLOCK_MAGIC -> decodeBlock(reader, blockSize100k, output)
                STREAM_MAGIC -> {
                    reader.readBits(32) // combined CRC
                    return output.toByteArray()
                }
                else -> error("Invalid bzip2 magic: ${magic.toString(16)}")
            }
        }
    }

    private fun decodeBlock(reader: BitReader, blockSize100k: Int, output: ArrayList<Byte>) {
        reader.readBits(32) // block CRC
        val randomized = reader.readBits(1) != 0L
        if (randomized) error("Randomized bzip2 blocks are not supported")
        val origPtr = reader.readBits(24).toInt()
        val inUse = BooleanArray(256)
        val inUse16 = reader.readBits(16)
        for (i in 0 until 16) {
            if ((inUse16 ushr (15 - i)) and 1L != 0L) {
                val bits = reader.readBits(16)
                for (j in 0 until 16) {
                    if ((bits ushr (15 - j)) and 1L != 0L) {
                        inUse[i * 16 + j] = true
                    }
                }
            }
        }
        val seqToUnseq = IntArray(256)
        var nInUse = 0
        for (i in 0 until 256) {
            if (inUse[i]) seqToUnseq[nInUse++] = i
        }
        if (nInUse == 0) error("Empty bzip2 alphabet")
        val alphaSize = nInUse + 2
        val nGroups = reader.readBits(3).toInt()
        if (nGroups !in 2..6) error("Invalid Huffman group count")
        val nSelectors = reader.readBits(15).toInt()
        val selectorsMtf = IntArray(nSelectors)
        for (i in 0 until nSelectors) {
            var j = 0
            while (reader.readBits(1) == 1L) {
                j++
                if (j >= nGroups) error("Invalid selector MTF")
            }
            selectorsMtf[i] = j
        }
        val pos = IntArray(nGroups) { it }
        val selectors = IntArray(nSelectors)
        for (i in 0 until nSelectors) {
            var v = selectorsMtf[i]
            val tmp = pos[v]
            while (v > 0) {
                pos[v] = pos[v - 1]
                v--
            }
            pos[0] = tmp
            selectors[i] = tmp
        }
        val len = Array(nGroups) { IntArray(alphaSize) }
        val limit = Array(nGroups) { IntArray(MAX_CODE_LEN + 1) }
        val base = Array(nGroups) { IntArray(MAX_CODE_LEN + 1) }
        val perm = Array(nGroups) { IntArray(alphaSize) }
        val minLens = IntArray(nGroups)
        for (t in 0 until nGroups) {
            var curr = reader.readBits(5).toInt()
            for (i in 0 until alphaSize) {
                while (true) {
                    if (curr !in 1..20) error("Invalid Huffman length")
                    if (reader.readBits(1) == 0L) break
                    curr += if (reader.readBits(1) == 0L) 1 else -1
                }
                len[t][i] = curr
            }
            createHuffmanTable(len[t], alphaSize, limit[t], base[t], perm[t], minLens, t)
        }

        val unzftab = IntArray(256)
        val ll8 = ByteArray(blockSize100k * 100_000)
        val mtf = IntArray(nInUse) { it }
        var last = 0
        var groupNo = -1
        var groupPos = 0
        var nextSym: Int
        fun nextSymbol(): Int {
            if (groupPos == 0) {
                groupNo++
                if (groupNo >= nSelectors) error("Selector overflow")
                groupPos = 50
            }
            groupPos--
            val t = selectors[groupNo]
            var zn = minLens[t]
            var zvec = reader.readBits(zn).toInt()
            while (zvec > limit[t][zn]) {
                zn++
                zvec = (zvec shl 1) or reader.readBits(1).toInt()
            }
            val index = zvec - base[t][zn]
            if (index < 0 || index >= alphaSize) error("Invalid Huffman symbol")
            return perm[t][index]
        }

        nextSym = nextSymbol()
        val eob = nInUse + 1
        while (nextSym != eob) {
            if (nextSym == RUNA || nextSym == RUNB) {
                var n = -1
                var s = nextSym
                var es = 1
                while (s == RUNA || s == RUNB) {
                    n += es * if (s == RUNA) 1 else 2
                    es = es shl 1
                    s = nextSymbol()
                }
                nextSym = s
                val uc = seqToUnseq[mtf[0]]
                val copies = n + 1
                unzftab[uc] += copies
                repeat(copies) {
                    ll8[last++] = uc.toByte()
                }
            } else {
                val nn = nextSym - 1
                val ucp = mtf[nn]
                val uc = seqToUnseq[ucp]
                unzftab[uc]++
                ll8[last++] = uc.toByte()
                for (j in nn downTo 1) mtf[j] = mtf[j - 1]
                mtf[0] = ucp
                nextSym = nextSymbol()
            }
        }
        if (origPtr < 0 || origPtr >= last) error("Invalid BWT pointer")
        val cftab = IntArray(257)
        for (i in 0 until 256) cftab[i + 1] = unzftab[i]
        for (i in 1..256) cftab[i] += cftab[i - 1]
        val tt = IntArray(last)
        for (i in 0 until last) {
            val uc = ll8[i].toInt() and 0xff
            tt[cftab[uc]] = i
            cftab[uc]++
        }
        val rle = ByteArray(last)
        var tPos = tt[origPtr]
        for (i in 0 until last) {
            rle[i] = ll8[tPos]
            tPos = tt[tPos]
        }
        expandRleI(rle, output)
    }

    private fun expandRleI(encoded: ByteArray, output: ArrayList<Byte>) {
        var i = 0
        while (i < encoded.size) {
            val b = encoded[i]
            var run = 0
            while (i < encoded.size && encoded[i] == b && run < 4) {
                output.add(encoded[i])
                i++
                run++
            }
            if (run == 4) {
                if (i >= encoded.size) error("Truncated bzip2 RLE")
                val extra = encoded[i].toInt() and 0xff
                i++
                repeat(extra) { output.add(b) }
            }
        }
    }

    private fun createHuffmanTable(
        len: IntArray,
        alphaSize: Int,
        limit: IntArray,
        base: IntArray,
        perm: IntArray,
        minLens: IntArray,
        t: Int,
    ) {
        var minLen = 32
        var maxLen = 0
        for (i in 0 until alphaSize) {
            if (len[i] > maxLen) maxLen = len[i]
            if (len[i] < minLen) minLen = len[i]
        }
        minLens[t] = minLen
        var pp = 0
        for (i in minLen..maxLen) {
            for (j in 0 until alphaSize) {
                if (len[j] == i) perm[pp++] = j
            }
        }
        for (i in 0..MAX_CODE_LEN) {
            base[i] = 0
            limit[i] = 0
        }
        for (i in 0 until alphaSize) {
            base[len[i] + 1]++
        }
        for (i in 1..MAX_CODE_LEN) {
            base[i] += base[i - 1]
        }
        var vec = 0
        for (i in minLen..maxLen) {
            vec += base[i + 1] - base[i]
            limit[i] = vec - 1
            vec = vec shl 1
        }
        for (i in minLen + 1..maxLen) {
            base[i] = ((limit[i - 1] + 1) shl 1) - base[i]
        }
    }

    private const val BLOCK_MAGIC = 0x314159265359L
    private const val STREAM_MAGIC = 0x177245385090L
    private const val RUNA = 0
    private const val RUNB = 1
    private const val MAX_CODE_LEN = 23
}

internal class BitReader(private val data: ByteArray) {
    private var offset = 0
    private var liveBits = 0
    private var buffer = 0

    fun readByte(): Int = readBits(8).toInt()

    fun readBits(n: Int): Long {
        var bits = n
        var result = 0L
        while (bits > 0) {
            if (liveBits == 0) {
                if (offset >= data.size) error("Unexpected end of bzip2 stream")
                buffer = data[offset++].toInt() and 0xff
                liveBits = 8
            }
            val take = minOf(bits, liveBits)
            liveBits -= take
            bits -= take
            result = (result shl take) or ((buffer ushr liveBits) and ((1 shl take) - 1)).toLong()
        }
        return result
    }
}
