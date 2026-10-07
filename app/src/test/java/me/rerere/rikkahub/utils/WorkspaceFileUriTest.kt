package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WorkspaceFileUriTest {
    @Test
    fun `looksLikeLocalFileTarget detects workspace and content links`() {
        assertTrue(looksLikeLocalFileTarget("/workspace/LastChat_backup_merged.zip"))
        assertTrue(looksLikeLocalFileTarget("content://me.rerere.rikkahub.documents/document/ws/abc/file.zip"))
        assertTrue(looksLikeLocalFileTarget("file:///data/user/0/app/files/workspaces/x/files/a.zip"))
        assertFalse(looksLikeLocalFileTarget("https://example.com/a.zip"))
        assertFalse(looksLikeLocalFileTarget("mailto:user@example.com"))
        assertFalse(looksLikeLocalFileTarget(""))
    }

    @Test
    fun `resolveWorkspaceAreaFile maps workspace path and rejects traversal`() {
        val appFiles = Files.createTempDirectory("ws-uri-test").toFile()
        val workspaceId = "11111111-1111-1111-1111-111111111111"
        val target = appFiles.resolve("workspaces/$workspaceId/files/reports/out.zip")
        target.parentFile!!.mkdirs()
        target.writeText("zip-bytes")

        val resolved = resolveWorkspaceAreaFile(
            appFilesDir = appFiles,
            workspaceId = workspaceId,
            relativeOrWorkspacePath = "/workspace/reports/out.zip",
        )
        assertNotNull(resolved)
        assertEquals(target.canonicalFile, resolved!!.canonicalFile)

        assertNull(
            resolveWorkspaceAreaFile(
                appFilesDir = appFiles,
                workspaceId = workspaceId,
                relativeOrWorkspacePath = "/workspace/../secret.txt",
            )
        )
        assertNull(
            resolveWorkspaceAreaFile(
                appFilesDir = appFiles,
                workspaceId = "../escape",
                relativeOrWorkspacePath = "/workspace/a.zip",
            )
        )
        assertNull(
            resolveWorkspaceAreaFile(
                appFilesDir = appFiles,
                workspaceId = workspaceId,
                relativeOrWorkspacePath = "/workspace/missing.zip",
            )
        )
    }

    @Test
    fun `image helper recognizes common extensions`() {
        assertTrue(isImageFileName("photo.PNG"))
        assertTrue(isImageFileName("diagram.webp"))
        assertFalse(isImageFileName("notes.md"))
        assertFalse(isImageFileName("archive.zip"))
    }
}
