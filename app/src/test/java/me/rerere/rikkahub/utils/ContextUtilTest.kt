package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ContextUtilTest {
    @Test
    fun `resolveAppOwnedFileProviderFile maps known roots`() {
        val workspace = Files.createTempDirectory("file-provider-test").toFile()
        val cacheDir = workspace.resolve("cache").apply { mkdirs() }
        val filesDir = workspace.resolve("files").apply { mkdirs() }
        val externalFilesDir = workspace.resolve("external").apply { mkdirs() }

        val resolved = resolveAppOwnedFileProviderFile(
            authority = "lastchat.rikkafork.cocolal.fileprovider",
            encodedPath = "/upload/workspaces%2Fconversation%2Ffigure.png",
            expectedAuthority = "lastchat.rikkafork.cocolal.fileprovider",
            cacheDir = cacheDir,
            filesDir = filesDir,
            externalFilesDir = externalFilesDir,
        )

        assertEquals(
            filesDir.resolve("workspaces/conversation/figure.png").canonicalFile,
            resolved,
        )
    }

    @Test
    fun `resolveAppOwnedFileProviderFile rejects traversal and unknown authorities`() {
        val workspace = Files.createTempDirectory("file-provider-test").toFile()
        val cacheDir = workspace.resolve("cache").apply { mkdirs() }
        val filesDir = workspace.resolve("files").apply { mkdirs() }

        assertNull(
            resolveAppOwnedFileProviderFile(
                authority = "other.package.fileprovider",
                encodedPath = "/upload/figure.png",
                expectedAuthority = "lastchat.rikkafork.cocolal.fileprovider",
                cacheDir = cacheDir,
                filesDir = filesDir,
                externalFilesDir = null,
            )
        )
        assertNull(
            resolveAppOwnedFileProviderFile(
                authority = "lastchat.rikkafork.cocolal.fileprovider",
                encodedPath = "/upload/..%2Fsecret.txt",
                expectedAuthority = "lastchat.rikkafork.cocolal.fileprovider",
                cacheDir = cacheDir,
                filesDir = filesDir,
                externalFilesDir = null,
            )
        )
    }
}
