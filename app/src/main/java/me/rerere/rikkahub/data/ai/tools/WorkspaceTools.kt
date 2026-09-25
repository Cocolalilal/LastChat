package me.rerere.rikkahub.data.ai.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.core.ToolApprovalMode
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.ai.TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY
import me.rerere.rikkahub.data.ai.TOOL_RESULT_INJECT_USER_IMAGE_PROMPT_KEY
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceStorageArea
import okio.Buffer

private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val MAX_IMAGE_FILE_BYTES = 15L * 1024 * 1024

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
    "workspace_view_image" to false,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    model: Model? = null,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, approvalOverrides)
    val shellCwd = cwd?.removePrefix("/workspace/")?.removePrefix("/workspace")

    val tools = mutableListOf(
        createReadFileTool(workspaceId, ::needsApproval, workspaceRepository, model),
        createWriteFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createEditFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createShellTool(workspaceId, ::needsApproval, workspaceRepository, shellCwd),
    )
    if (model?.inputModalities?.contains(Modality.IMAGE) == true) {
        tools.add(createViewImageTool(workspaceId, ::needsApproval, workspaceRepository))
    }
    return tools
}

private fun createReadFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    model: Model? = null,
) = Tool(
    name = "workspace_read_file",
    description = "Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs. Use /workspace for workspace files or /skills/<skill>/ for a read-only Agent Skill package.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { putPathProperty(required = true) },
            required = listOf("path"),
        )
    },
    approvalMode = if (needsApproval("workspace_read_file")) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto,
    execute = {
        val path = it.jsonObject.absolutePath("path")
        val ext = path.substringAfterLast('.', "").lowercase()
        val isImage = ext in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "ico")
        if (isImage) {
            if (model?.inputModalities?.contains(Modality.IMAGE) == true) {
                val bytes = workspaceRepository.readImageBytesInRootfs(workspaceId, path)
                val dataUrl = prepareImageForModelInspection(path, bytes)
                buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("sizeBytes", JsonPrimitive(bytes.size))
                    put("note", JsonPrimitive("Binary image file '$path' detected. Attached below for visual inspection."))
                    put(
                        TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY,
                        JsonArray(listOf(JsonPrimitive(dataUrl))),
                    )
                    put(
                        TOOL_RESULT_INJECT_USER_IMAGE_PROMPT_KEY,
                        JsonPrimitive("Image from workspace ($path):"),
                    )
                }
            } else {
                buildJsonObject {
                    put("path", JsonPrimitive(path))
                    put("error", JsonPrimitive("Cannot read '$path' as UTF-8 text because it is a binary image file. The current model does not support image input."))
                }
            }
        } else {
            val text = workspaceRepository.readTextInRootfs(workspaceId, path)
            buildJsonObject {
                put("path", path)
                put("text", text)
            }
        }
    },
)

private fun createViewImageTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_view_image",
    description = "Inspect and visually view an image file (PNG, JPG, JPEG, WEBP, GIF, SVG, BMP) from the assistant's bound workspace Rootfs. Available when the active model supports image input. Use this tool to inspect charts, plots, diagrams, screenshots, or photos in the workspace to verify their visual contents before answering or showing them to the user. Paths must be absolute inside Rootfs (e.g. /workspace/chart.png).",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { putPathProperty(required = true) },
            required = listOf("path"),
        )
    },
    approvalMode = if (needsApproval("workspace_view_image")) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto,
    execute = {
        val path = it.jsonObject.absolutePath("path")
        val bytes = workspaceRepository.readImageBytesInRootfs(workspaceId, path)
        val dataUrl = prepareImageForModelInspection(path, bytes)
        buildJsonObject {
            put("path", JsonPrimitive(path))
            put("sizeBytes", JsonPrimitive(bytes.size))
            put("note", JsonPrimitive("Image '$path' is attached for visual inspection."))
            put(
                TOOL_RESULT_INJECT_USER_IMAGE_PARTS_KEY,
                JsonArray(listOf(JsonPrimitive(dataUrl))),
            )
            put(
                TOOL_RESULT_INJECT_USER_IMAGE_PROMPT_KEY,
                JsonPrimitive("Image from workspace ($path):"),
            )
        }
    },
)

private fun createWriteFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_write_file",
    description = "Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs. Use /workspace for the workspace files area.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
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
    approvalMode = if (needsApproval("workspace_write_file")) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto,
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.booleanOrNull ?: true
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, text, overwrite)
        entry.toJson()
    },
)

private fun createEditFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit_file",
    description = "Edit a UTF-8 text file using an exact text replacement inside the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs. Use /workspace for the workspace files area.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
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
    approvalMode = if (needsApproval("workspace_edit_file")) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto,
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
        val current = workspaceRepository.readTextInRootfs(workspaceId, path)
        require(oldText.isNotEmpty()) { "old_text must not be empty" }
        require(current.contains(oldText)) { "old_text was not found in $path" }
        val updated = if (replaceAll) current.replace(oldText, newText) else current.replaceFirst(oldText, newText)
        workspaceRepository.writeTextInRootfs(workspaceId, path, updated, overwrite = true).toJson()
    },
)

private fun createShellTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    defaultCwd: String?,
) = Tool(
    name = "workspace_shell",
    description = "Execute a shell command inside the assistant's bound workspace Rootfs.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to execute. Examples: 'ls -la', 'python3 script.py'.")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional absolute working directory inside Rootfs.")
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put("description", "Command timeout in seconds. Defaults to 30.")
                })
            },
            required = listOf("command"),
        )
    },
    approvalMode = if (needsApproval("workspace_shell")) ToolApprovalMode.RequiresApproval else ToolApprovalMode.Auto,
    execute = {
        val params = it.jsonObject
        val command = params.string("command") ?: error("command is required")
        val cwd = (params.string("cwd") ?: defaultCwd.orEmpty())
            .removePrefix("/workspace/").removePrefix("/workspace")
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val result = workspaceRepository.executeCommand(workspaceId, command, cwd, timeoutMillis)
        buildJsonObject {
            put("exitCode", result.exitCode)
            put("stdout", result.stdout)
            put("stderr", result.stderr)
            put("timedOut", result.timedOut)
            if (result.truncated) put("truncated", true)
        }
    },
)

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String {
    // `/skills` is an external bind mount, not WorkspaceRepository-managed
    // storage. Read it through the active rootfs so packaged resources work.
    if (path == "/skills" || path.startsWith("/skills/")) {
        val result = executeCommand(
            id = workspaceId,
            command = "cat -- ${path.shellQuote()}",
            timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        )
        if (result.timedOut) error("Read file timed out")
        if (result.exitCode != 0) error(result.stderr.ifBlank { result.stdout }.trim().ifBlank { "Read file failed" })
        if (result.truncated || result.stdout.toByteArray().size > MAX_READ_FILE_BYTES) error("File is too large to read")
        return result.stdout
    }
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val size = fileSize(workspaceId, area, relativePath)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read"
    }
    val buffer = Buffer()
    exportFile(workspaceId, area, relativePath, buffer.outputStream())
    return buffer.readUtf8()
}

private fun rootfsPathToAreaAndRelative(path: String): Pair<WorkspaceStorageArea, String> {
    val trimmed = path.trimEnd('/')
    return if (trimmed == "/workspace" || trimmed.startsWith("/workspace/")) {
        WorkspaceStorageArea.FILES to trimmed.removePrefix("/workspace").trimStart('/')
    } else {
        WorkspaceStorageArea.LINUX to trimmed.trimStart('/')
    }
}

private suspend fun WorkspaceRepository.writeTextInRootfs(
    workspaceId: String,
    path: String,
    text: String,
    overwrite: Boolean,
): WorkspaceFileEntry {
    require(path != "/skills" && !path.startsWith("/skills/")) { "Skill packages are read-only" }
    val pathArg = path.shellQuote()
    val overwriteInt = if(overwrite) 1 else 0
    val result = runRootfsCommand(
        workspaceId = workspaceId,
        action = "Write file",
        command = """
            if [ -e $pathArg ] && [ $overwriteInt = 0 ]; then
              printf '%s\n' "File already exists" >&2
              exit 1
            fi
            parent=${'$'}(dirname -- $pathArg) || exit 1
            mkdir -p -- "${'$'}parent" || exit 1
            cat > $pathArg || exit 1
            ${statEntryCommand(path)}
        """.trimIndent(),
        stdin = text.toByteArray(Charsets.UTF_8),
    )
    return result.stdout.parseRootfsEntry()
}

private suspend fun WorkspaceRepository.runRootfsCommand(
    workspaceId: String,
    action: String,
    command: String,
    stdin: ByteArray? = null,
): WorkspaceCommandResult {
    val result = executeCommand(
        id = workspaceId,
        command = command,
        timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin = stdin,
    )
    if (result.timedOut) error("$action timed out")
    if (result.exitCode != 0) {
        val message = result.stderr.ifBlank { result.stdout }.trim()
        error(if (message.isBlank()) "$action failed" else message)
    }
    if (result.truncated) error("$action output is too large")
    return result
}

private fun statEntryCommand(path: String): String {
    val pathArg = path.shellQuote()
    return """
        if [ -d $pathArg ]; then entry_type=d; else entry_type=f; fi
        entry_size=${'$'}(stat -c '%s' -- $pathArg) || exit 1
        entry_mtime=${'$'}(stat -c '%Y' -- $pathArg) || exit 1
        printf '%s\0%s\0%s\0%s\0' "${'$'}entry_type" "${'$'}entry_size" "${'$'}entry_mtime" $pathArg
    """.trimIndent()
}

private fun String.parseRootfsEntry(): WorkspaceFileEntry =
    parseRootfsEntries().singleOrNull() ?: error("Invalid metadata")

private fun String.parseRootfsEntries(): List<WorkspaceFileEntry> {
    val fields = split('\u0000').dropLastWhile { it.isEmpty() }
    require(fields.size % 4 == 0)
    return fields.chunked(4).map { chunk ->
        val type = chunk[0]
        val size = chunk[1].toLongOrNull() ?: 0L
        val updatedAt = (chunk[2].toLongOrNull() ?: 0L) * 1_000L
        val path = chunk[3]
        WorkspaceFileEntry(
            path = path,
            name = path.trimEnd('/').substringAfterLast('/').ifBlank { "/" },
            isDirectory = type == "d",
            sizeBytes = size,
            updatedAt = updatedAt,
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/') ?: error("$name required")
    require(path.startsWith("/"))
    return path
}

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private fun JsonObjectBuilder.putPathProperty(required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put("description", "Absolute path inside Rootfs.")
    })
}

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}

internal suspend fun WorkspaceRepository.readImageBytesInRootfs(
    workspaceId: String,
    path: String,
): ByteArray {
    if (path == "/skills" || path.startsWith("/skills/")) {
        val result = executeCommand(
            id = workspaceId,
            command = "base64 -w 0 -- ${path.shellQuote()}",
            timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        )
        if (result.timedOut) error("Read image timed out")
        if (result.exitCode != 0) error(result.stderr.ifBlank { result.stdout }.trim().ifBlank { "Read image failed" })
        val clean = result.stdout.filterNot { it.isWhitespace() }
        val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        require(bytes.size <= MAX_IMAGE_FILE_BYTES) { "Image file is too large" }
        return bytes
    }
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val size = fileSize(workspaceId, area, relativePath)
    require(size <= MAX_IMAGE_FILE_BYTES) {
        "Image file is too large (${size / 1024} KB > ${MAX_IMAGE_FILE_BYTES / 1024} KB)"
    }
    val buffer = Buffer()
    exportFile(workspaceId, area, relativePath, buffer.outputStream())
    return buffer.readByteArray()
}

internal fun guessImageMime(path: String): String {
    return when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        else -> "image/jpeg"
    }
}

internal fun prepareImageForModelInspection(
    path: String,
    bytes: ByteArray,
    maxDimension: Int = 1280,
): String {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
    val origWidth = boundsOptions.outWidth
    val origHeight = boundsOptions.outHeight

    if (origWidth <= 0 || origHeight <= 0) {
        val mime = guessImageMime(path)
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return "data:$mime;base64,$base64"
    }

    var sampleSize = 1
    while (origWidth / (sampleSize * 2) >= maxDimension || origHeight / (sampleSize * 2) >= maxDimension) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        ?: run {
            val mime = guessImageMime(path)
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            return "data:$mime;base64,$base64"
        }

    val finalBitmap = try {
        val width = decodedBitmap.width
        val height = decodedBitmap.height
        if (width > maxDimension || height > maxDimension) {
            val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
            val targetWidth = (width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
        } else {
            decodedBitmap
        }
    } catch (e: Throwable) {
        decodedBitmap
    }

    val hasAlpha = finalBitmap.hasAlpha()
    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val mime = if (hasAlpha) "image/png" else "image/jpeg"
    val quality = if (hasAlpha) 100 else 85

    val out = java.io.ByteArrayOutputStream()
    finalBitmap.compress(format, quality, out)
    if (finalBitmap !== decodedBitmap) {
        finalBitmap.recycle()
    }
    decodedBitmap.recycle()

    val compressedBytes = out.toByteArray()
    val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
    return "data:$mime;base64,$base64"
}

