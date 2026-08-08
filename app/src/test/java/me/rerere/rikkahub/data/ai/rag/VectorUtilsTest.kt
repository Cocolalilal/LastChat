package me.rerere.rikkahub.data.ai.rag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VectorUtilsTest {
    @Test
    fun `chunked embedding blob round trips all vectors`() {
        val vectors = listOf(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(4f, 5f, 6f),
        )

        val decoded = vectors.toByteArray().toListOfFloatArrays()

        assertEquals(2, decoded.size)
        assertArrayEquals(vectors[0], decoded[0], 0f)
        assertArrayEquals(vectors[1], decoded[1], 0f)
    }

    @Test
    fun `blob backed cache entry ignores its intentionally empty legacy json column`() {
        val expected = listOf(floatArrayOf(0.25f, 0.75f))

        val decoded = decodeStoredEmbedding("", expected.toByteArray()) {
            error("legacy JSON must not be used when a blob exists")
        }

        assertArrayEquals(expected.single(), decoded?.single(), 0f)
    }

    @Test
    fun `malformed blob is rejected instead of becoming a bogus successful embedding`() {
        val malformed = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(3)
            .putFloat(1f)
            .array()

        assertThrows(IllegalArgumentException::class.java) {
            malformed.toListOfFloatArrays()
        }
    }

    @Test
    fun `inconsistent or non finite vectors cannot be persisted`() {
        assertThrows(IllegalArgumentException::class.java) {
            listOf(floatArrayOf(1f), floatArrayOf(1f, 2f)).toByteArray()
        }
        assertThrows(IllegalArgumentException::class.java) {
            listOf(floatArrayOf(Float.NaN)).toByteArray()
        }
    }
}
