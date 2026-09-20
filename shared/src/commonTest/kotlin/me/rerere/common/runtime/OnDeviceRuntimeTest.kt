package me.rerere.common.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class OnDeviceRuntimeTest {
    @Test
    fun unavailableLlmShimIsHonest() = runBlocking {
        val runtime = UnavailableOnDeviceLlmRuntime()
        assertFalse(runtime.available)
        assertEquals(0, runtime.listModels().size)
        assertEquals(null, runtime.embed("model", "text"))
        val failure = assertFailsWith<IllegalStateException> {
            runtime.generateText("id", "hi")
        }
        assertTrue(failure.message.orEmpty().contains("LiteRT-LM"))
        val chatFailure = assertFailsWith<IllegalStateException> {
            runtime.generateChat("id", listOf(OnDeviceChatMessage("user", "hi")))
        }
        assertTrue(chatFailure.message.orEmpty().contains("LiteRT-LM"))
    }

    @Test
    fun recordingRuntimeFlattenAndStreamShareOneLoop() = runBlocking {
        val runtime = RecordingOnDeviceLlmRuntime()
        val chunk = runtime.generateChat(
            "local",
            listOf(
                OnDeviceChatMessage("system", "Be brief"),
                OnDeviceChatMessage("user", "Hello"),
            ),
        )
        assertEquals("system: Be brief\n\nuser: Hello", chunk.text)
        assertEquals("stop", chunk.finishReason)
        val streamed = runtime.streamChat("local", listOf(OnDeviceChatMessage("user", "Hi"))).toList()
        assertEquals(1, streamed.size)
        assertEquals("user: Hi", streamed.first().text)
    }

    @Test
    fun unavailableWorkspaceShimIsHonest() = runBlocking {
        val runtime = UnavailableOnDeviceWorkspaceRuntime()
        assertFalse(runtime.available)
        assertEquals(0, runtime.listFiles("/").size)
        assertFailsWith<IllegalStateException> { runtime.readFile("/tmp") }
        assertFailsWith<IllegalStateException> { runtime.writeFile("/tmp", "x") }
        val execFailure = assertFailsWith<IllegalStateException> { runtime.exec("ls") }
        assertTrue(execFailure.message.orEmpty().contains("PRoot"))
    }

    @Test
    fun memoryWorkspaceRuntimeHonorsOverwriteAndExec() = runBlocking {
        val runtime = MemoryOnDeviceWorkspaceRuntime()
        runtime.writeFile("/note.txt", "one")
        assertEquals("one", runtime.readFile("/note.txt"))
        val exists = runCatching { runtime.writeFile("/note.txt", "two", overwrite = false) }
        assertTrue(exists.isFailure)
        runtime.writeFile("/note.txt", "two", overwrite = true)
        assertEquals("two", runtime.readFile("/note.txt"))
        val result = runtime.exec("echo hi", timeoutSeconds = 5, cwd = "/workspace")
        assertEquals(0, result.exitCode)
        assertEquals("echo hi", result.stdout)
        assertEquals("/workspace", result.stderr)
    }
}

private class RecordingOnDeviceLlmRuntime : OnDeviceLlmRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null

    override suspend fun listModels(): List<OnDeviceLlmModel> =
        listOf(OnDeviceLlmModel(id = "local", displayName = "Local"))

    override suspend fun generateText(modelId: String, prompt: String): String = prompt

    override suspend fun embed(modelId: String, text: String): List<Float>? = listOf(0.25f)
}

internal class MemoryOnDeviceWorkspaceRuntime(
    private val files: MutableMap<String, String> = mutableMapOf(),
) : OnDeviceWorkspaceRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> =
        files.keys.filter { it.startsWith(path) }.map { key ->
            OnDeviceWorkspaceEntry(path = key, isDirectory = false, sizeBytes = files.getValue(key).length.toLong())
        }

    override suspend fun readFile(path: String): String =
        files[path] ?: error("missing $path")

    override suspend fun writeFile(path: String, text: String, overwrite: Boolean) {
        if (!overwrite && files.containsKey(path)) error("exists")
        files[path] = text
    }

    override suspend fun exec(
        command: String,
        timeoutSeconds: Long,
        cwd: String?,
        stdin: ByteArray?,
    ): OnDeviceWorkspaceCommandResult = OnDeviceWorkspaceCommandResult(
        exitCode = 0,
        stdout = command,
        stderr = cwd.orEmpty(),
    )
}
