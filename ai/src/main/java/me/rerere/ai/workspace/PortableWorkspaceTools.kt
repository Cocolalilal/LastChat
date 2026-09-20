package me.rerere.ai.workspace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.common.runtime.OnDeviceWorkspaceCommandResult
import me.rerere.common.runtime.OnDeviceWorkspaceEntry
import me.rerere.common.runtime.OnDeviceWorkspaceRuntime

const val WORKSPACE_TOOL_READ = "workspace_read_file"
const val WORKSPACE_TOOL_WRITE = "workspace_write_file"
const val WORKSPACE_TOOL_EDIT = "workspace_edit_file"
const val WORKSPACE_TOOL_SHELL = "workspace_shell"

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    WORKSPACE_TOOL_READ to false,
    WORKSPACE_TOOL_WRITE to false,
    WORKSPACE_TOOL_EDIT to false,
    WORKSPACE_TOOL_SHELL to true,
)

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

/**
 * Shared workspace tool loop used by Android ChatService and iOS
 * IosAppController. Execution goes through [OnDeviceWorkspaceRuntime] so
 * PRoot stays an adapter, not a second engine.
 */
fun createPortableWorkspaceTools(
    runtime: OnDeviceWorkspaceRuntime,
    approvalOverrides: Map<String, Boolean> = emptyMap(),
    cwd: String? = null,
): List<Tool> {
    val needsApproval: (String) -> Boolean = { name ->
        resolveWorkspaceToolApproval(name, approvalOverrides)
    }
    val shellCwd = cwd?.removePrefix("/workspace/")?.removePrefix("/workspace")
    return listOf(
        createReadFileTool(runtime, needsApproval),
        createWriteFileTool(runtime, needsApproval),
        createEditFileTool(runtime, needsApproval),
        createShellTool(runtime, needsApproval, shellCwd),
    )
}

private fun createReadFileTool(
    runtime: OnDeviceWorkspaceRuntime,
    needsApproval: (String) -> Boolean,
) = Tool(
    name = WORKSPACE_TOOL_READ,
    description = "Read a file using the assistant's bound workspace sandbox. Paths must be absolute inside the sandbox.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { putPathProperty() },
            required = listOf("path"),
        )
    },
    approvalMode = approvalMode(needsApproval(WORKSPACE_TOOL_READ)),
    execute = { arguments ->
        runtime.unavailableOr {
            val path = arguments.jsonObject.absolutePath("path")
            buildJsonObject {
                put("path", path)
                put("text", runtime.readFile(path))
            }
        }
    },
)

private fun createWriteFileTool(
    runtime: OnDeviceWorkspaceRuntime,
    needsApproval: (String) -> Boolean,
) = Tool(
    name = WORKSPACE_TOOL_WRITE,
    description = "Write a UTF-8 text file using the assistant's bound workspace sandbox. Paths must be absolute.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty()
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    approvalMode = approvalMode(needsApproval(WORKSPACE_TOOL_WRITE)),
    execute = { arguments ->
        runtime.unavailableOr {
            val params = arguments.jsonObject
            val path = params.absolutePath("path")
            val text = params.string("text") ?: error("text is required")
            val overwrite = params["overwrite"]?.jsonPrimitive?.booleanOrNull ?: true
            runtime.writeFile(path, text, overwrite)
            buildJsonObject {
                put("path", path)
                put("ok", true)
            }
        }
    },
)

private fun createEditFileTool(
    runtime: OnDeviceWorkspaceRuntime,
    needsApproval: (String) -> Boolean,
) = Tool(
    name = WORKSPACE_TOOL_EDIT,
    description = "Edit a UTF-8 text file using an exact text replacement inside the workspace sandbox.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty()
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    approvalMode = approvalMode(needsApproval(WORKSPACE_TOOL_EDIT)),
    execute = { arguments ->
        runtime.unavailableOr {
            val params = arguments.jsonObject
            val path = params.absolutePath("path")
            val oldText = params.string("old_text") ?: error("old_text is required")
            val newText = params.string("new_text") ?: error("new_text is required")
            val replaceAll = params["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
            require(oldText.isNotEmpty()) { "old_text must not be empty" }
            val current = runtime.readFile(path)
            require(current.contains(oldText)) { "old_text was not found in $path" }
            val updated = if (replaceAll) current.replace(oldText, newText) else current.replaceFirst(oldText, newText)
            runtime.writeFile(path, updated, overwrite = true)
            buildJsonObject {
                put("path", path)
                put("ok", true)
            }
        }
    },
)

private fun createShellTool(
    runtime: OnDeviceWorkspaceRuntime,
    needsApproval: (String) -> Boolean,
    defaultCwd: String?,
) = Tool(
    name = WORKSPACE_TOOL_SHELL,
    description = "Execute a shell command inside the assistant's bound workspace sandbox.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to execute. Examples: 'ls -la', 'python3 script.py'.")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional absolute working directory inside the sandbox.")
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put("description", "Command timeout in seconds. Defaults to 30.")
                })
            },
            required = listOf("command"),
        )
    },
    approvalMode = approvalMode(needsApproval(WORKSPACE_TOOL_SHELL)),
    execute = { arguments ->
        runtime.unavailableOr {
            val params = arguments.jsonObject
            val command = params.string("command") ?: error("command is required")
            val cwd = (params.string("cwd") ?: defaultCwd.orEmpty())
                .removePrefix("/workspace/").removePrefix("/workspace")
                .ifBlank { null }
            val timeoutSeconds = params["timeout"]?.jsonPrimitive?.intOrNull?.toLong()
                ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
                ?: 30L
            runtime.exec(command, timeoutSeconds, cwd).toJson()
        }
    },
)

private fun approvalMode(requiresApproval: Boolean): ToolApprovalMode =
    if (requiresApproval) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto

private suspend fun OnDeviceWorkspaceRuntime.unavailableOr(
    block: suspend () -> JsonElement,
): JsonElement {
    if (!available) {
        return buildJsonObject {
            put("available", false)
            put("error", unavailableReason ?: "Workspace sandbox is unavailable")
        }
    }
    return block()
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putPathProperty() {
    put("path", buildJsonObject {
        put("type", "string")
        put("description", "Absolute path inside the sandbox.")
    })
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/') ?: error("$name required")
    require(path.startsWith("/"))
    return path
}

private fun OnDeviceWorkspaceCommandResult.toJson() = buildJsonObject {
    put("exitCode", exitCode)
    put("stdout", stdout)
    put("stderr", stderr)
    put("timedOut", timedOut)
    if (truncated) put("truncated", true)
}

fun OnDeviceWorkspaceEntry.toToolJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAtEpochMs)
}

fun List<OnDeviceWorkspaceEntry>.toToolJsonArray(): JsonArray =
    JsonArray(map { it.toToolJson() })
