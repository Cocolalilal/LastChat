package me.rerere.tts.provider.providers

import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat

/** Decode the returned format, never play container headers/trailers as raw samples. */
internal fun decodeGeminiAudio(bytes: ByteArray, mimeType: String): AudioChunk {
    require(bytes.isNotEmpty()) { "Gemini returned empty audio" }
    val fields = mimeType.split(';').map(String::trim)
    val mime = fields.first().lowercase()
    val parameters = fields.drop(1).mapNotNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2) pair[0].lowercase() to pair[1].trim('"') else null
    }.toMap()
    val wave = bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WAVE"
    if (wave) return decodeGeminiWave(bytes)
    if (mime in setOf("audio/wav", "audio/wave", "audio/x-wav")) {
        error("Gemini returned an invalid WAV file")
    }
    val format = when (mime) {
        "audio/pcm", "audio/l16", "audio/raw", "audio/x-raw" -> AudioFormat.PCM
        "audio/mpeg", "audio/mp3" -> AudioFormat.MP3
        "audio/ogg" -> AudioFormat.OGG
        "audio/aac" -> AudioFormat.AAC
        else -> error("Unsupported Gemini audio format: $mimeType")
    }
    if (format != AudioFormat.PCM) return AudioChunk(bytes, format)
    require(parameters["codec"]?.lowercase() in listOf(null, "pcm", "pcm_s16le")) {
        "Unsupported Gemini PCM codec: ${parameters["codec"]}"
    }
    fun number(key: String, default: Int): Int = parameters[key]?.let {
        it.toIntOrNull() ?: error("Invalid Gemini PCM $key")
    } ?: default
    val rate = number("rate", 24_000)
    val channels = number("channels", 1)
    require(number("bitdepth", 16) == 16) { "Unsupported Gemini PCM bit depth" }
    require(rate in 8_000..192_000 && channels == 1) { "Unsupported Gemini PCM sample format" }
    require(bytes.size % 2 == 0) { "Gemini returned a truncated 16-bit PCM sample" }
    // Gemini's audio/L16;codec=pcm payload uses signed 16-bit little-endian samples,
    // matching Google's wave.setsampwidth(2) example (not network-order generic L16).
    return AudioChunk(bytes, AudioFormat.PCM, rate,
        metadata = mapOf("channels" to "1", "bitDepth" to "16"))
}

private fun decodeGeminiWave(bytes: ByteArray): AudioChunk {
    fun uint(offset: Int): Long = (0 until 4).fold(0L) { value, index ->
        value or ((bytes[offset + index].toLong() and 255) shl (index * 8))
    }
    fun ushort(offset: Int) = (bytes[offset].toInt() and 255) or
        ((bytes[offset + 1].toInt() and 255) shl 8)
    val end = uint(4) + 8
    require(end in 12..bytes.size.toLong()) { "Gemini returned a truncated WAV container" }
    var offset = 12
    var rate: Int? = null
    var data: ByteArray? = null
    while (offset.toLong() + 8 <= end) {
        val name = bytes.copyOfRange(offset, offset + 4).decodeToString()
        val length = uint(offset + 4)
        val start = offset + 8
        require(start.toLong() + length <= end) { "Gemini returned a truncated WAV chunk" }
        when (name) {
            "fmt " -> {
                require(length >= 16 && ushort(start) == 1 && ushort(start + 2) == 1 &&
                    ushort(start + 14) == 16) { "Gemini WAV must contain 16-bit mono PCM" }
                val sampleRate = uint(start + 4)
                require(sampleRate in 8_000..192_000 && ushort(start + 12) == 2) {
                    "Invalid Gemini WAV sample rate or alignment"
                }
                rate = sampleRate.toInt()
            }
            "data" -> {
                require(data == null) { "Gemini WAV contains multiple data chunks" }
                data = bytes.copyOfRange(start, start + length.toInt())
            }
        }
        val next = start.toLong() + length + (length and 1)
        require(next <= Int.MAX_VALUE) { "Gemini WAV is too large" }
        offset = next.toInt()
    }
    val samples = requireNotNull(data) { "Gemini WAV has no sample data" }
    require(samples.isNotEmpty() && samples.size % 2 == 0) { "Invalid Gemini WAV samples" }
    return AudioChunk(samples, AudioFormat.PCM, requireNotNull(rate) { "Gemini WAV has no format" },
        metadata = mapOf("channels" to "1", "bitDepth" to "16"))
}
