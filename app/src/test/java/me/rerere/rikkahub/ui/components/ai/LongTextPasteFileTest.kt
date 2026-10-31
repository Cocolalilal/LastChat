package me.rerere.rikkahub.ui.components.ai

import android.app.Application
import android.net.Uri
import androidx.core.net.toFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.repository.ImportedChatFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LongTextPasteFileTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun fullUnicodeAndLineEndingsReachTheManagedImporterAsUtf8() = runBlocking {
        val original = "😀 中文 café\r\nsecond line\n".repeat(100_000)
        val managed = ImportedChatFile(Uri.parse("content://managed/paste.txt"), "Pasted text.txt", "text/plain")
        val imported = importPastedTextFile(temporary.root, original) { uri ->
            val file = uri.toFile()
            assertTrue(file.name.endsWith(".txt"))
            assertArrayEquals(original.toByteArray(Charsets.UTF_8), file.readBytes())
            managed
        }
        assertEquals(managed, imported)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun failedImportLeavesNoStagingFile() = runBlocking {
        val failure = runCatching { importPastedTextFile(temporary.root, "complete text") { null } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancelledImportPropagatesCancellationAndRemovesStagingFile() = runBlocking {
        val failure = runCatching {
            importPastedTextFile(temporary.root, "complete text") { throw CancellationException("cancelled") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }
}
