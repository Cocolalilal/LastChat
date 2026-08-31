package me.rerere.rikkahub.data.ai.rag

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * 4)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val floatBuffer = buffer.asFloatBuffer()
    val floatArray = FloatArray(floatBuffer.limit())
    floatBuffer.get(floatArray)
    return floatArray
}

fun List<FloatArray>.toByteArray(): ByteArray {
    if (this.isEmpty()) return ByteArray(0)
    val vectorSize = this.first().size
    require(vectorSize > 0) { "Embedding vectors must not be empty" }
    require(all { it.size == vectorSize && it.all(Float::isFinite) }) {
        "Embedding vectors must have a consistent size and finite values"
    }
    val buffer = ByteBuffer.allocate(4 + this.size * vectorSize * 4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(vectorSize)
    for (arr in this) {
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(arr)
        buffer.position(buffer.position() + vectorSize * 4)
    }
    return buffer.array()
}

fun ByteArray.toListOfFloatArrays(): List<FloatArray> {
    if (this.isEmpty()) return emptyList()
    require(size >= 4) { "Embedding blob is missing its dimension header" }
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val vectorSize = buffer.int
    require(vectorSize > 0) { "Embedding blob has an invalid vector dimension" }
    val vectorBytes = vectorSize.toLong() * 4L
    val payloadBytes = size - 4L
    require(payloadBytes > 0L && payloadBytes % vectorBytes == 0L) {
        "Embedding blob has an invalid payload length"
    }
    val numChunks = (payloadBytes / vectorBytes).toInt()
    val list = mutableListOf<FloatArray>()
    for (i in 0 until numChunks) {
        val arr = FloatArray(vectorSize)
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.get(arr)
        require(arr.all(Float::isFinite)) { "Embedding blob contains a non-finite value" }
        buffer.position(buffer.position() + vectorSize * 4)
        list.add(arr)
    }
    return list
}

/** Reads the blob-first storage format while retaining legacy JSON compatibility. */
fun decodeStoredEmbedding(
    embedding: String?,
    embeddingBlob: ByteArray?,
    decodeLegacyJson: (String) -> FloatArray,
): List<FloatArray>? {
    val vectors = when {
        embeddingBlob != null -> embeddingBlob.toListOfFloatArrays()
        !embedding.isNullOrBlank() -> listOf(decodeLegacyJson(embedding))
        else -> return null
    }
    return vectors.takeIf { stored ->
        stored.isNotEmpty() && stored.all { it.isNotEmpty() && it.all(Float::isFinite) }
    }
}
