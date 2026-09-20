package me.rerere.ai.workspace

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.common.runtime.OnDeviceWorkspaceCommandResult
import me.rerere.common.runtime.OnDeviceWorkspaceEntry
import me.rerere.common.runtime.OnDeviceWorkspaceRuntime
import me.rerere.common.runtime.UnavailableOnDeviceWorkspaceRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableWorkspaceToolsTest {
    @Test
    fun readWriteEditAndShellUseTheSharedRuntime() = runBlocking {
        val runtime = MemoryRuntime()
        val tools = createPortableWorkspaceTools(runtime)
        assertEquals(
            listOf(WORKSPACE_TOOL_READ, WORKSPACE_TOOL_WRITE, WORKSPACE_TOOL_EDIT, WORKSPACE_TOOL_SHELL),
            tools.map { it.name },
        )

        tools.first { it.name == WORKSPACE_TOOL_WRITE }.execute(
            buildJsonObject {
                put("path", "/note.txt")
                put("text", "hello world")
            },
        )
        val read = tools.first { it.name == WORKSPACE_TOOL_READ }.execute(
            buildJsonObject { put("path", "/note.txt") },
        ) as JsonObject
        assertEquals("hello world", read.string("text"))

        tools.first { it.name == WORKSPACE_TOOL_EDIT }.execute(
            buildJsonObject {
                put("path", "/note.txt")
                put("old_text", "world")
                put("new_text", "lastchat")
            },
        )
        assertEquals("hello lastchat", runtime.readFile("/note.txt"))

        val shell = tools.first { it.name == WORKSPACE_TOOL_SHELL }.execute(
            buildJsonObject { put("command", "ls") },
        ) as JsonObject
        assertEquals(0, (shell["exitCode"] as JsonPrimitive).content.toInt())
        assertEquals("ls", shell.string("stdout"))
    }

    @Test
    fun unavailableRuntimeReturnsExplicitToolError() = runBlocking {
        val tools = createPortableWorkspaceTools(UnavailableOnDeviceWorkspaceRuntime())
        val result = tools.first { it.name == WORKSPACE_TOOL_READ }.execute(
            buildJsonObject { put("path", "/note.txt") },
        ) as JsonObject
        assertFalse(result["available"]?.let { (it as JsonPrimitive).content.toBoolean() } ?: true)
        assertTrue(result.string("error").orEmpty().contains("PRoot"))
    }
}

private fun JsonObject.string(name: String): String? = this[name]?.let { (it as JsonPrimitive).contentOrNull }

private class MemoryRuntime : OnDeviceWorkspaceRuntime {
    private val files = mutableMapOf<String, String>()
    override val available: Boolean = true
    override val unavailableReason: String? = null

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> =
        files.keys.map { OnDeviceWorkspaceEntry(it, isDirectory = false, sizeBytes = files.getValue(it).length.toLong()) }

    override suspend fun readFile(path: String): String = files.getValue(path)

    override suspend fun writeFile(path: String, text: String, overwrite: Boolean) {
        files[path] = text
    }

    override suspend fun exec(
        command: String,
        timeoutSeconds: Long,
        cwd: String?,
        stdin: ByteArray?,
    ): OnDeviceWorkspaceCommandResult = OnDeviceWorkspaceCommandResult(exitCode = 0, stdout = command)
}
