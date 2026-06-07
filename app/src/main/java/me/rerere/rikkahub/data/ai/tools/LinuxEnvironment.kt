package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

private const val MAX_LINUX_OUTPUT_CHARS = 12_000
private val NETWORK_COMMAND_PATTERNS = listOf(
    Regex("""(^|\s)apk\s+add(\s|$)"""),
    Regex("""(^|\s)(python3?\s+-m\s+)?pip\s+install(\s|$)"""),
    Regex("""(^|\s)npm\s+(install|i|add)(\s|$)"""),
    Regex("""(^|\s)(curl|wget|ssh|scp|rsync)(\s|$)"""),
    Regex("""(^|\s)git\s+(clone|fetch|pull|push)(\s|$)"""),
)

@Serializable
data class LinuxEnvironmentStatus(
    val ready: Boolean,
    val rootfsInstalled: Boolean,
    val runnerInstalled: Boolean,
    val runnerAbi: String,
    val rootfsPath: String,
    val runnerPath: String,
    val missing: List<String> = emptyList(),
)

class LinuxEnvironmentManager(private val context: Context) {
    private val baseDir = File(context.filesDir, "linux_env")
    val rootfsDir: File = File(baseDir, "rootfs")
    private val binDir: File = File(baseDir, "bin")
    val runnerFile: File = File(binDir, "proot")

    fun getStatus(): LinuxEnvironmentStatus {
        val rootfsInstalled = File(rootfsDir, "bin/sh").isFile && File(rootfsDir, "etc/alpine-release").isFile
        val runnerInstalled = ensureRunnerInstalled()
        val missing = buildList {
            if (!rootfsInstalled) add("Alpine rootfs")
            if (!runnerInstalled) add("PRoot runner asset")
        }
        return LinuxEnvironmentStatus(
            ready = rootfsInstalled && runnerInstalled,
            rootfsInstalled = rootfsInstalled,
            runnerInstalled = runnerInstalled,
            runnerAbi = currentAbi(),
            rootfsPath = rootfsDir.absolutePath,
            runnerPath = runnerFile.absolutePath,
            missing = missing,
        )
    }

    private fun ensureRunnerInstalled(): Boolean {
        if (runnerFile.isFile && runnerFile.canExecute()) {
            return true
        }

        return runCatching {
            binDir.mkdirs()
            val assetPath = "linux/proot/${currentAbi()}/proot"
            context.assets.open(assetPath).use { input ->
                runnerFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            runnerFile.setExecutable(true, true)
            runnerFile.isFile && runnerFile.canExecute()
        }.getOrDefault(false)
    }

    private fun currentAbi(): String {
        return Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { Build.CPU_ABI ?: "unknown" }
    }
}

data class LinuxCommandResult(
    val ready: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val error: String?,
)

class LinuxCommandRunner(
    private val environmentManager: LinuxEnvironmentManager,
) {
    suspend fun runCommand(
        command: String,
        workspaceDir: File,
        timeoutSeconds: Long,
        networkAccess: Boolean,
    ): LinuxCommandResult = withContext(Dispatchers.IO) {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isBlank()) {
            return@withContext LinuxCommandResult(
                ready = true,
                exitCode = 2,
                stdout = "",
                stderr = "",
                timedOut = false,
                error = "Command is required",
            )
        }
        if (!networkAccess && NETWORK_COMMAND_PATTERNS.any { pattern -> pattern.containsMatchIn(trimmedCommand) }) {
            return@withContext LinuxCommandResult(
                ready = true,
                exitCode = 126,
                stdout = "",
                stderr = "",
                timedOut = false,
                error = "Network/package access is disabled for this character.",
            )
        }

        val status = environmentManager.getStatus()
        if (!status.ready) {
            return@withContext LinuxCommandResult(
                ready = false,
                exitCode = null,
                stdout = "",
                stderr = "",
                timedOut = false,
                error = "Linux environment is not ready. Missing: ${status.missing.joinToString()}",
            )
        }

        workspaceDir.mkdirs()
        val process = ProcessBuilder(
            environmentManager.runnerFile.absolutePath,
            "-R",
            environmentManager.rootfsDir.absolutePath,
            "-b",
            "${workspaceDir.absolutePath}:/workspace",
            "-w",
            "/workspace",
            "/bin/sh",
            "-lc",
            trimmedCommand,
        )
            .redirectErrorStream(false)
            .start()

        val stdoutThread = StreamCollector(process.inputStream)
        val stderrThread = StreamCollector(process.errorStream)
        stdoutThread.start()
        stderrThread.start()

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        stdoutThread.join(1000L)
        stderrThread.join(1000L)

        LinuxCommandResult(
            ready = true,
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdoutThread.text.truncateLinuxOutput(),
            stderr = stderrThread.text.truncateLinuxOutput(),
            timedOut = !finished,
            error = if (finished) null else "Command timed out after ${timeoutSeconds}s",
        )
    }

    private class StreamCollector(
        private val input: java.io.InputStream,
    ) : Thread("linux-stream-collector") {
        var text: String = ""
            private set

        override fun run() {
            text = input.bufferedReader().use { reader -> reader.readText() }
        }
    }
}

internal fun LinuxEnvironmentStatus.toJsonElement(): JsonElement {
    return buildJsonObject {
        put("ready", ready)
        put("rootfs_installed", rootfsInstalled)
        put("runner_installed", runnerInstalled)
        put("runner_abi", runnerAbi)
        put("rootfs_path", rootfsPath)
        put("runner_path", runnerPath)
        put("missing", JsonArray(missing.map(::JsonPrimitive)))
        if (!runnerInstalled) {
            put("runner_asset_hint", "Bundle assets/linux/proot/$runnerAbi/proot for this ABI.")
        }
        if (!rootfsInstalled) {
            put("rootfs_hint", "Install Alpine minirootfs into linux_env/rootfs before running commands.")
        }
    }
}

private fun String.truncateLinuxOutput(): String {
    return if (length > MAX_LINUX_OUTPUT_CHARS) {
        take(MAX_LINUX_OUTPUT_CHARS) + "... (truncated ${length - MAX_LINUX_OUTPUT_CHARS} chars)"
    } else {
        this
    }
}
