package me.rerere.document

import java.util.zip.DataFormatException
import java.util.zip.Inflater

internal actual fun inflateDeflate(data: ByteArray, maxBytes: Int, raw: Boolean): ByteArray? {
    val inflater = Inflater(raw)
    return try {
        inflater.setInput(data)
        val chunks = mutableListOf<ByteArray>()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (!inflater.finished()) {
            val produced = inflater.inflate(buffer)
            if (produced == 0) {
                if (inflater.needsInput()) return null
                break
            }
            if (total + produced > maxBytes) return null
            chunks += buffer.copyOf(produced)
            total += produced
        }
        when (chunks.size) {
            0 -> ByteArray(0)
            1 -> chunks[0]
            else -> chunks.reduce { acc, part -> acc + part }
        }
    } catch (_: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}
