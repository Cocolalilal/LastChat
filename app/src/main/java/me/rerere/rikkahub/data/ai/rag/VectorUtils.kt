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
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val vectorSize = buffer.int
    val numChunks = (this.size - 4) / (vectorSize * 4)
    val list = mutableListOf<FloatArray>()
    for (i in 0 until numChunks) {
        val arr = FloatArray(vectorSize)
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.get(arr)
        buffer.position(buffer.position() + vectorSize * 4)
        list.add(arr)
    }
    return list
}
