import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

fun calculateRmsAmplitude(buffer: ByteArray, readBytes: Int): Float {
    val shorts = ByteBuffer.wrap(buffer, 0, readBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()
    var sum = 0.0
    val count = shorts.remaining()
    if (count == 0) return 0f
    for (i in 0 until count) {
        val sample = shorts[i].toDouble()
        sum += sample * sample
    }
    val rms = sqrt(sum / count)
    val linear = (rms / Short.MAX_VALUE).toFloat()
    if (linear < 1e-6f) return 0f
    val db = 20f * log10(linear)
    return ((db + 60f) / 60f).coerceIn(0f, 1f)
}

fun main() {
    val sampleRate = 16000
    val duration = 1.0
    val samples = (sampleRate * duration).toInt()
    val buffer = ByteArray(samples * 2)
    val shorts = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    for (i in 0 until samples) {
        val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 10000).toInt().toShort()
        shorts.put(i, sample)
    }
    
    val amp = calculateRmsAmplitude(buffer, buffer.size)
    println("High volume amp (10000 amplitude): $amp")
    
    val bufferLow = ByteArray(samples * 2)
    val shortsLow = ByteBuffer.wrap(bufferLow).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    for (i in 0 until samples) {
        val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 100).toInt().toShort()
        shortsLow.put(i, sample)
    }
    println("Low volume amp (100 amplitude): ${calculateRmsAmplitude(bufferLow, bufferLow.size)}")

    val bufferSilence = ByteArray(samples * 2)
    println("Silence: ${calculateRmsAmplitude(bufferSilence, bufferSilence.size)}")
}
