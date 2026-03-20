package me.rerere.rikkahub.utils

import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.data.ai.tools.PythonSandbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.uuid.Uuid

class ContextUtilAndroidTest {
    @Test
    fun openOwnedUriInputStreamReadsPythonSandboxFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox = PythonSandbox(context)
        val conversationId = Uuid.random()
        val file = sandbox.getFile(conversationId, "generated/result.txt").apply {
            parentFile?.mkdirs()
            writeText("hello from sandbox")
        }

        val uri = sandbox.getFileUri(conversationId, "generated/result.txt")
        val content = context.openOwnedUriInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText()
        }

        assertNotNull(file)
        assertEquals("hello from sandbox", content)
    }
}
