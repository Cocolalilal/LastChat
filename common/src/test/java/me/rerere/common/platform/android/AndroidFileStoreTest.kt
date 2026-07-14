package me.rerere.common.platform.android

import java.io.File
import java.nio.file.Files
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
        } finally {
            parent.deleteRecursively()
        }
    }
}
