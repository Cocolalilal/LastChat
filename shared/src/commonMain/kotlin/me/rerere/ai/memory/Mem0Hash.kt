package me.rerere.ai.memory

/** Small common-code MD5 implementation used only for Mem0-compatible exact deduplication. */
object Mem0Hash {
    fun md5(text: String): String = digest(text.encodeToByteArray()).joinToString("") {
        it.toUByte().toString(16).padStart(2, '0')
    }

    fun digest(input: ByteArray): ByteArray {
        val originalLengthBits = input.size.toLong() * 8L
        val paddedLength = (((input.size + 8) / 64) + 1) * 64
        val data = ByteArray(paddedLength)
        input.copyInto(data)
        data[input.size] = 0x80.toByte()
        for (i in 0 until 8) data[paddedLength - 8 + i] = (originalLengthBits ushr (8 * i)).toByte()

        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val shifts = intArrayOf(
            7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
            5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
            4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
            6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
        )
        val constants = IntArray(64) { i ->
            (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
        }
        for (offset in data.indices step 64) {
            val words = IntArray(16)
            for (i in 0 until 16) {
                val p = offset + i * 4
                words[i] = (data[p].toInt() and 0xff) or
                    ((data[p + 1].toInt() and 0xff) shl 8) or
                    ((data[p + 2].toInt() and 0xff) shl 16) or
                    ((data[p + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                val f: Int
                val g: Int
                when (i) {
                    in 0..15 -> { f = (b and c) or (b.inv() and d); g = i }
                    in 16..31 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    in 32..47 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val previousD = d
                d = c
                c = b
                b += (a + f + constants[i] + words[g]).rotateLeft(shifts[i])
                a = previousD
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
        }
        val output = ByteArray(16)
        intArrayOf(a0, b0, c0, d0).forEachIndexed { index, value ->
            for (i in 0 until 4) output[index * 4 + i] = (value ushr (8 * i)).toByte()
        }
        return output
    }

    private fun Int.rotateLeft(bits: Int): Int = (this shl bits) or (this ushr (32 - bits))
}
