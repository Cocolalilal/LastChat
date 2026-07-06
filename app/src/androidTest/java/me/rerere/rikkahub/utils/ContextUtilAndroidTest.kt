package me.rerere.rikkahub.utils

import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

class ContextUtilAndroidTest {
    @Test
    fun openOwnedUriInputStreamReadsOwnedFile() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // PythonSandbox has been removed; produce the app-owned file + URI through the
        // current OwnedFileStorage import path instead. The behavior under test is the
        // same: openOwnedUriInputStream must read back a file we own.
        val source = File(context.cacheDir, "owned-source-${Uuid.random()}.txt").apply {
            writeText("hello from owned storage")
        }
        val ownedUri = context.importOwnedFile(
            sourceUri = source.toUri(),
            directory = OwnedFileDirectory.LOREBOOK_ATTACHMENT,
            fileNameHint = "result.txt",
            mimeHint = "text/plain",
        )
        assertNotNull(ownedUri)

        val content = context.openOwnedUriInputStream(requireNotNull(ownedUri))
            ?.bufferedReader()
            ?.use { reader -> reader.readText() }

        assertEquals("hello from owned storage", content)
        source.delete()
    }
}
