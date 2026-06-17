package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalToolsHelperTest {
    @Test
    fun `buildSandboxAttachmentFilename preserves extension and adds stable suffix`() {
        val filename = buildSandboxAttachmentFilename(
            originalName = "question de cours compilation.pdf",
            sourceUrl = "file:///tmp/question.pdf"
        )

        assertTrue(filename.endsWith(".pdf"))
        assertTrue(filename.startsWith("question-de-cours-compilation-"))
        assertEquals(filename, buildSandboxAttachmentFilename("question de cours compilation.pdf", "file:///tmp/question.pdf"))
    }

    @Test
    fun `buildSandboxAttachmentFilename can use platform supplied stable hash`() {
        val filename = buildSandboxAttachmentFilename(
            originalName = "photo.png",
            sourceUrl = "content://images/photo",
            stableHash = { "abcdef12" },
        )

        assertEquals("photo-abcdef12.png", filename)
    }

    @Test
    fun `buildPreloadedPythonDescription lists sandbox filenames without eval_python attachments hint`() {
        val description = buildPreloadedPythonDescription(
            listOf(
                PreloadedSandboxAttachment(
                    sandboxName = "question-de-cours-1234abcd.pdf",
                    originalFileName = "question.pdf",
                    mimeType = "application/pdf",
                    sourceUrl = "file:///tmp/question.pdf",
                    promptVisible = true,
                ),
                PreloadedSandboxAttachment(
                    sandboxName = "benchmark-5678efgh.png",
                    originalFileName = "benchmark.png",
                    mimeType = "image/png",
                    sourceUrl = "file:///tmp/benchmark.png",
                    promptVisible = true,
                )
            )
        )

        assertTrue(description.contains("'question-de-cours-1234abcd.pdf'"))
        assertTrue(description.contains("'benchmark-5678efgh.png'"))
        assertFalse(description.contains("eval_python.attachments"))
    }

    @Test
    fun `buildPreloadedPythonDescription hides silently preloaded files`() {
        val description = buildPreloadedPythonDescription(
            listOf(
                PreloadedSandboxAttachment(
                    sandboxName = "visible.pdf",
                    originalFileName = "visible.pdf",
                    mimeType = "application/pdf",
                    sourceUrl = "file:///tmp/visible.pdf",
                    promptVisible = true,
                ),
                PreloadedSandboxAttachment(
                    sandboxName = "hidden.png",
                    originalFileName = "hidden.png",
                    mimeType = "image/png",
                    sourceUrl = "file:///tmp/hidden.png",
                    promptVisible = false,
                )
            )
        )

        assertTrue(description.contains("'visible.pdf'"))
        assertFalse(description.contains("'hidden.png'"))
    }

    @Test
    fun `detectSandboxPseudoImportFilename recognizes sandbox-like pseudo urls`() {
        assertEquals(
            "attachment_0.png",
            detectSandboxPseudoImportFilename(
                url = "file:///attachment_0.png",
                sandboxFileNames = setOf("attachment_0.png"),
                pathExists = { false }
            )
        )
        assertNull(
            detectSandboxPseudoImportFilename(
                url = "file:///storage/emulated/0/Download/attachment_0.png",
                sandboxFileNames = setOf("attachment_0.png"),
                pathExists = { false }
            )
        )
    }

    @Test
    fun `scheduledMessageToolJson preserves success response shape`() {
        val json = scheduledMessageToolJson(
            ScheduledLocalToolMessage(
                status = "success",
                scheduledAt = "2026-06-18T10:15:30Z",
                workName = "scheduled-message-work",
            )
        )

        assertEquals("success", json["status"]?.jsonPrimitive?.content)
        assertEquals("2026-06-18T10:15:30Z", json["scheduled_at"]?.jsonPrimitive?.content)
        assertEquals("scheduled-message-work", json["work_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `notificationsToolJson preserves notification list response shape`() {
        val json = notificationsToolJson(
            listOf(
                LocalToolNotificationSnapshot(
                    packageName = "com.example",
                    title = "Title",
                    content = "Content",
                    postTime = 123L,
                )
            )
        )
        val first = json["notifications"]?.jsonArray?.first()

        assertEquals("com.example", first?.jsonObject?.get("package")?.jsonPrimitive?.content)
        assertEquals("Title", first?.jsonObject?.get("title")?.jsonPrimitive?.content)
        assertEquals("Content", first?.jsonObject?.get("content")?.jsonPrimitive?.content)
        assertEquals("123", first?.jsonObject?.get("time")?.jsonPrimitive?.content)
    }
}
