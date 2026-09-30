package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceToolsTest {

    @Test
    fun prepareImageForModelInspection_emptyBytesReturnsNull() {
        val result = prepareImageForModelInspection("test.jpg", ByteArray(0))
        assertNull("Empty bytes must return null", result)
    }

    @Test
    fun prepareImageForModelInspection_corruptOrHtmlBytesReturnsNull() {
        val htmlBytes = "<html><head><title>403 Forbidden</title></head><body>Cloudflare error</body></html>".toByteArray()
        val result = prepareImageForModelInspection("error.jpg", htmlBytes)
        assertNull("Corrupt/HTML bytes must return null instead of corrupt data:image/jpeg string", result)
    }

    @Test
    fun prepareImageForModelInspection_svgBytesReturnsNull() {
        val svgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"><circle cx=\"50\" cy=\"50\" r=\"40\"/></svg>".toByteArray()
        val result = prepareImageForModelInspection("vector.svg", svgBytes)
        assertNull("SVG bytes must return null because BitmapFactory cannot raster decode SVG", result)
    }
}
