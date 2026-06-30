package me.rerere.tts.controller

internal fun pcmToWavBytes(
    pcm: ByteArray,
    sampleRate: Int,
    channels: Int = 1,
    bitsPerSample: Int = 16,
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val wav = ByteArray(44 + pcm.size)
    var offset = 0

    fun writeAscii(value: String) {
        value.encodeToByteArray().copyInto(wav, destinationOffset = offset)
        offset += value.length
    }

    fun writeIntLe(value: Int) {
        wav[offset++] = (value and 0xFF).toByte()
        wav[offset++] = ((value shr 8) and 0xFF).toByte()
        wav[offset++] = ((value shr 16) and 0xFF).toByte()
        wav[offset++] = ((value shr 24) and 0xFF).toByte()
    }

    fun writeShortLe(value: Int) {
        wav[offset++] = (value and 0xFF).toByte()
        wav[offset++] = ((value shr 8) and 0xFF).toByte()
    }

    writeAscii("RIFF")
    writeIntLe(36 + pcm.size)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeIntLe(16)
    writeShortLe(1)
    writeShortLe(channels)
    writeIntLe(sampleRate)
    writeIntLe(byteRate)
    writeShortLe(blockAlign)
    writeShortLe(bitsPerSample)
    writeAscii("data")
    writeIntLe(pcm.size)
    pcm.copyInto(wav, destinationOffset = offset)

    return wav
}
