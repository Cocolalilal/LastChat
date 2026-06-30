package me.rerere.rikkahub.ui.components.message

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

internal val SANDBOX_FILE_TOOLS = setOf(
    "write_sandbox_file",
    "read_sandbox_file",
    "list_sandbox_files",
    "delete_sandbox_file"
)

internal val WORKSPACE_TOOLS = setOf(
    "workspace_read_file",
    "workspace_write_file",
    "workspace_edit_file",
    "workspace_shell"
)

internal data class PythonToolSummary(
    val code: String,
    val result: String?,
    val stdout: String?,
    val stderr: String?,
    val error: String?
) {
    val previewText: String?
        get() = error ?: result.takeIf { !it.isNullOrBlank() && it != "null" } ?: stdout
}

internal data class SandboxFileToolSummary(
    val path: String?,
    val uri: String?,
    val fileCount: Int?,
    val success: Boolean?,
    val error: String?
) {
    val previewText: String?
        get() = when {
            !error.isNullOrBlank() -> error
            fileCount != null -> null
            !uri.isNullOrBlank() -> uri.substringAfterLast("/")
            !path.isNullOrBlank() -> path.substringAfterLast("/")
            success != null -> if (success) "ok" else "failed"
            else -> null
    }
}

internal data class WorkspaceToolSummary(
    val path: String?,
    val command: String?,
    val cwd: String?,
    val timeout: String?,
    val text: String?,
    val name: String?,
    val isDirectory: Boolean?,
    val sizeBytes: Long?,
    val updatedAt: Long?,
    val exitCode: Int?,
    val stdout: String?,
    val stderr: String?,
    val timedOut: Boolean?,
    val truncated: Boolean?,
    val error: String?
) {
    val previewText: String?
        get() = when {
            !error.isNullOrBlank() -> error
            !stderr.isNullOrBlank() && exitCode != null && exitCode != 0 -> stderr
            !stdout.isNullOrBlank() -> stdout
            !text.isNullOrBlank() -> text
            !name.isNullOrBlank() -> name
            !path.isNullOrBlank() -> path.substringAfterLast("/")
            exitCode != null -> "exit $exitCode"
            timedOut == true -> "timed out"
            truncated == true -> "output truncated"
            else -> null
        }
}

internal fun buildPythonToolSummary(
    arguments: JsonElement?,
    content: JsonElement?
): PythonToolSummary? {
    val argsObj = arguments as? JsonObject
    val contentObj = content as? JsonObject ?: return null
    return PythonToolSummary(
        code = argsObj.stringValue("code").orEmpty(),
        result = contentObj.stringValue("result"),
        stdout = contentObj.stringValue("stdout"),
        stderr = contentObj.stringValue("stderr"),
        error = contentObj.stringValue("error")
    )
}

internal fun buildSandboxFileToolSummary(
    toolName: String,
    arguments: JsonElement?,
    content: JsonElement?
): SandboxFileToolSummary? {
    if (toolName !in SANDBOX_FILE_TOOLS) return null

    val argsObj = arguments as? JsonObject
    val contentObj = content as? JsonObject
    return SandboxFileToolSummary(
        path = argsObj.stringValue("path"),
        uri = contentObj?.stringValue("uri"),
        fileCount = (contentObj?.get("files") as? JsonArray)?.size,
        success = contentObj.booleanValue("success"),
        error = contentObj?.stringValue("error")
    )
}

internal fun buildWorkspaceToolSummary(
    toolName: String,
    arguments: JsonElement?,
    content: JsonElement?
): WorkspaceToolSummary? {
    if (toolName !in WORKSPACE_TOOLS) return null

    val argsObj = arguments as? JsonObject
    val contentObj = content as? JsonObject
    return WorkspaceToolSummary(
        path = argsObj.stringValue("path") ?: contentObj?.stringValue("path"),
        command = argsObj.stringValue("command"),
        cwd = argsObj.stringValue("cwd"),
        timeout = argsObj.rawValue("timeout"),
        text = contentObj?.stringValue("text"),
        name = contentObj?.stringValue("name"),
        isDirectory = contentObj.booleanValue("isDirectory"),
        sizeBytes = contentObj.longValue("sizeBytes"),
        updatedAt = contentObj.longValue("updatedAt"),
        exitCode = contentObj.intValue("exitCode"),
        stdout = contentObj?.stringValue("stdout"),
        stderr = contentObj?.stringValue("stderr"),
        timedOut = contentObj.booleanValue("timedOut"),
        truncated = contentObj.booleanValue("truncated"),
        error = contentObj?.stringValue("error")
    )
}

private fun JsonObject?.stringValue(key: String): String? {
    return this?.get(key)?.jsonPrimitiveOrNull?.contentOrNull
}

private fun JsonObject?.rawValue(key: String): String? {
    return this?.get(key)?.jsonPrimitiveOrNull?.contentOrNull
}

private fun JsonObject?.booleanValue(key: String): Boolean? {
    val raw = this?.get(key)?.jsonPrimitiveOrNull?.contentOrNull ?: return null
    return when (raw.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private fun JsonObject?.intValue(key: String): Int? {
    return this?.get(key)?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull()
}

private fun JsonObject?.longValue(key: String): Long? {
    return this?.get(key)?.jsonPrimitiveOrNull?.contentOrNull?.toLongOrNull()
}
