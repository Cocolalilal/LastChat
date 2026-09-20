package me.rerere.workspace

import me.rerere.common.runtime.OnDeviceWorkspaceCommandResult
import me.rerere.common.runtime.OnDeviceWorkspaceEntry
import me.rerere.common.runtime.OnDeviceWorkspaceRuntime

/**
 * Android PRoot workspace binding for the shared sandbox contract.
 * Tool names and the generation loop stay shared; this class only executes
 * list/read/write/shell against [WorkspaceManager].
 */
class AndroidOnDeviceWorkspaceRuntime(
    private val manager: WorkspaceManager,
    private val root: String = DEFAULT_ROOT,
) : OnDeviceWorkspaceRuntime {
    override val available: Boolean = true
    override val unavailableReason: String? = null

    init {
        manager.ensureWorkspace(root)
    }

    override suspend fun listFiles(path: String): List<OnDeviceWorkspaceEntry> =
        manager.listFiles(root, path).map { entry ->
            OnDeviceWorkspaceEntry(
                path = entry.path,
                isDirectory = entry.isDirectory,
                sizeBytes = entry.sizeBytes,
            )
        }

    override suspend fun readFile(path: String): String = manager.readText(root, path)

    override suspend fun writeFile(path: String, text: String) {
        manager.writeText(root, path, text)
    }

    override suspend fun exec(command: String, timeoutSeconds: Long): OnDeviceWorkspaceCommandResult {
        val result = manager.executeCommand(
            root = root,
            command = command,
            timeoutMillis = (timeoutSeconds * 1000L).coerceAtLeast(1_000L),
        )
        return OnDeviceWorkspaceCommandResult(
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
        )
    }

    companion object {
        const val DEFAULT_ROOT = "default"
    }
}
