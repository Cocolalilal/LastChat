package me.rerere.common.platform.android

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidFileStoreTest {
    @Test
    fun localUrlRejectsSiblingWithMatchingPathPrefix() {
        val parent = Files.createTempDirectory("lastchat-file-store").toFile()
        try {
            val root = File(parent, "store").apply { mkdirs() }
            File(parent, "store-sibling").mkdirs()
            val store = AndroidFileStore(root)

            try {
                store.localUrl("../store-sibling/secret.txt")
                fail("Expected sibling traversal to be rejected")
            } catch (_: SecurityException) {
                // Expected.
            }
            assertTrue(store.localUrl("attachments/image.png").startsWith("file:"))
            runBlocking {
                store.writeBytes("skills/guide/notes.md", "note".encodeToByteArray())
                store.writeBytes("skills/guide/SKILL.md", "skill".encodeToByteArray())
                val listed = store.listFiles("skills")
                assertTrue(listed.any { it.endsWith("notes.md") })
                assertTrue(listed.any { it.endsWith("SKILL.md") })
            }
        } finally {
            parent.deleteRecursively()
        }
    }
}
