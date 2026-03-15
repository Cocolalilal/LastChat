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

private fun JsonObject?.stringValue(key: String): String? {
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
