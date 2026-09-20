package me.rerere.rikkahub.data.ai.tools

import me.rerere.common.runtime.OnDeviceWorkspaceCommandResult
import me.rerere.common.runtime.OnDeviceWorkspaceEntry
import me.rerere.common.runtime.OnDeviceWorkspaceRuntime
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceStorageArea
import okio.Buffer
import java.nio.charset.StandardCharsets

private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024

/**
 * Per-workspace PRoot adapter used by the shared [me.rerere.ai.workspace.createPortableWorkspaceTools]
 * loop. ChatService no longer calls [WorkspaceManager] from the tool layer.
 */
class AndroidBoundWorkspaceRuntime(
    private val workspaceId: String,
    private val workspaceRepository: WorkspaceRepository,
) : OnDeviceWorkspaceRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> {
        val (area, relativePath) = rootfsPathToAreaAndRelative(path)
        return workspaceRepository.listFiles(workspaceId, area, relativePath).map { entry ->
            OnDeviceWorkspaceEntry(
                path = entry.path,
                isDirectory = entry.isDirectory,
                sizeBytes = entry.sizeBytes,
                name = entry.name,
                updatedAtEpochMs = entry.updatedAt,
            )
        }
    }

    override suspend fun readFile(path: String): String {
        if (path == "/skills" || path.startsWith("/skills/")) {
            val result = workspaceRepository.executeCommand(
                id = workspaceId,
                command = "cat -- ${path.shellQuote()}",
                timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
            )
            if (result.timedOut) error("Read file timed out")
            if (result.exitCode != 0) {
                error(result.stderr.ifBlank { result.stdout }.trim().ifBlank { "Read file failed" })
            }
            if (result.truncated || result.stdout.toByteArray().size > MAX_READ_FILE_BYTES) {
                error("File is too large to read")
            }
            return result.stdout
        }
        val (area, relativePath) = rootfsPathToAreaAndRelative(path)
        val size = workspaceRepository.fileSize(workspaceId, area, relativePath)
        require(size <= MAX_READ_FILE_BYTES) { "File is too large to read" }
        val buffer = Buffer()
        workspaceRepository.exportFile(workspaceId, area, relativePath, buffer.outputStream())
        return buffer.readUtf8()
    }

    override suspend fun writeFile(path: String, text: String, overwrite: Boolean) {
        require(path != "/skills" && !path.startsWith("/skills/")) { "Skill packages are read-only" }
        val pathArg = path.shellQuote()
        val overwriteInt = if (overwrite) 1 else 0
        runRootfsCommand(
            action = "Write file",
            command = """
                if [ -e $pathArg ] && [ $overwriteInt = 0 ]; then
                  printf '%s\n' "File already exists" >&2
                  exit 1
                fi
                parent=${'$'}(dirname -- $pathArg) || exit 1
                mkdir -p -- "${'$'}parent" || exit 1
                cat > $pathArg || exit 1
            """.trimIndent(),
            stdin = text.toByteArray(StandardCharsets.UTF_8),
        )
    }

    override suspend fun exec(
        command: String,
        timeoutSeconds: Long,
        cwd: String?,
        stdin: ByteArray?,
    ): OnDeviceWorkspaceCommandResult {
        val result = workspaceRepository.executeCommand(
            id = workspaceId,
            command = command,
            cwd = cwd.orEmpty(),
            timeoutMillis = (timeoutSeconds * 1000L).coerceAtLeast(1_000L),
            stdin = stdin,
        )
        return OnDeviceWorkspaceCommandResult(
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            timedOut = result.timedOut,
            truncated = result.truncated,
        )
    }

    private suspend fun runRootfsCommand(
        action: String,
        command: String,
        stdin: ByteArray? = null,
    ): WorkspaceCommandResult {
        val result = workspaceRepository.executeCommand(
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
}

private fun rootfsPathToAreaAndRelative(path: String): Pair<WorkspaceStorageArea, String> {
    val trimmed = path.trimEnd('/')
    return if (trimmed == "/workspace" || trimmed.startsWith("/workspace/")) {
        WorkspaceStorageArea.FILES to trimmed.removePrefix("/workspace").trimStart('/')
    } else {
        WorkspaceStorageArea.LINUX to trimmed.trimStart('/')
    }
}

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"
