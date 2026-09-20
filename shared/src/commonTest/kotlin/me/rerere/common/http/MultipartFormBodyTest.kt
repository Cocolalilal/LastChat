package me.rerere.common.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipartFormBodyTest {
    @Test
    fun encodesTextAndFilePartsWithAStableBoundary() {
        val encoded = MultipartFormBody.encode(
            parts = listOf(
                MultipartFormPart.text("model", "whisper-1"),
                MultipartFormPart.file(
                    name = "file",
                    filename = "speech.wav",
                    bytes = byteArrayOf(1, 2, 3),
                    contentType = "audio/wav",
                ),
            ),
            boundary = "test-boundary",
        )
        val body = encoded.body.decodeToString()
        assertEquals("multipart/form-data; boundary=test-boundary", encoded.contentType)
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("whisper-1"))
        assertTrue(body.contains("filename=\"speech.wav\""))
        assertTrue(body.contains("Content-Type: audio/wav"))
        assertTrue(body.endsWith("--test-boundary--\r\n"))
    }
}
