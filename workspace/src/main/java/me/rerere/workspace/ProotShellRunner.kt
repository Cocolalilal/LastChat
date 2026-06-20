package me.rerere.workspace

import java.io.File
import java.io.IOException

data class WorkspaceBindMount(
    val source: File,
    val target: String,
) {
    init {
        require(target.startsWith("/")) { "Bind mount target must be absolute: $target" }
    }
}

class ProotShellRunner(
    private val nativeLibraryDir: File,
    private val extraBindMounts: List<WorkspaceBindMount> = emptyList(),
    private val patcher: RootfsPatcher = RootfsPatcher(),
) : WorkspaceShellRunner {
    override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
        if (!context.linuxDir.hasUsableRootfs()) {
            return WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "Rootfs is not installed",
            )
        }
        val expectedArchitecture = WorkspaceRootfsArchitecture.fromNativeLibraryDir(nativeLibraryDir)
        val actualArchitecture = context.linuxDir.detectRootfsArchitecture()
        if (expectedArchitecture != null && actualArchitecture != null && expectedArchitecture != actualArchitecture) {
            return WorkspaceCommandResult(
                exitCode = 126,
                stdout = "",
                stderr = "Rootfs architecture mismatch: installed ${actualArchitecture.displayName}, " +
                    "but this app is running ${expectedArchitecture.displayName} native proot. " +
                    "Reinstall rootfs using the ${expectedArchitecture.ubuntuArch} Ubuntu base archive.",
            )
        }

        val runtimes = ProotRuntimes.resolve(nativeLibraryDir)
        if (runtimes.isEmpty()) {
            return WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "proot runtime not found in ${nativeLibraryDir.absolutePath}. " +
                    "Linux workspaces require the bundled libproot_exec.so/libproot_loader.so runtime.",
            )
        }

        context.tempDir.mkdirs()
        context.tempDir.setReadable(true, true)
        context.tempDir.setWritable(true, true)
        context.tempDir.setExecutable(true, true)
        patcher.patch(context.linuxDir)

        val failures = mutableListOf<ProotAttemptFailure>()
        orderedAttempts(context, runtimes).forEach { attempt ->
            val result = runProot(context, attempt.runtime, attempt.mode)
            if (!result.shouldTryNextProotAttempt()) {
                ProotLaunchPreferences.write(context.tempDir, attempt.runtime, attempt.mode)
                return result
            }
            failures += ProotAttemptFailure(attempt, result)
        }

        return failures.lastOrNull()
            ?.result
            ?.withLaunchDiagnostics(failures)
            ?: WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "proot failed before launch",
            )
    }

    private fun runProot(
        context: WorkspaceShellContext,
        runtime: ProotRuntime,
        mode: ProotLaunchMode,
    ): WorkspaceCommandResult {
        return try {
            val process = ProcessBuilder(buildCommand(context, runtime.executable))
                .directory(context.filesDir)
                .redirectErrorStream(false)
                .apply {
                    environment()["PROOT_LOADER"] = runtime.loader.absolutePath
                    runtime.loader32?.let { environment()["PROOT_LOADER_32"] = it.absolutePath }
                    environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                    environment()["PROOT_TMPDIR"] = context.tempDir.absolutePath
                    environment()["TMPDIR"] = context.tempDir.absolutePath
                    environment()["LD_LIBRARY_PATH"] = runtime.executable.parentFile?.absolutePath.orEmpty()
                    environment()["HOME"] = "/root"
                    environment()["PATH"] = ROOTFS_PATH
                    environment()["TERM"] = "xterm-256color"
                    environment()["LANG"] = "C.UTF-8"
                    environment()["LC_ALL"] = "C.UTF-8"
                    environment().putAll(mode.environment)
                }
                .start()

            process.readResult(context.timeoutMillis, context.stdin)
        } catch (e: IOException) {
            WorkspaceCommandResult(
                exitCode = 127,
                stdout = "",
                stderr = "proot launch failed with ${runtime.name}/${mode.name}: ${e.message.orEmpty()}",
            )
        }
    }

    private fun buildCommand(
        context: WorkspaceShellContext,
        proot: File,
    ): List<String> {
        val command = mutableListOf(
            proot.absolutePath,
            "--root-id",
            "--link2symlink",
            "--kill-on-exit",
            "-r",
            context.linuxDir.absolutePath,
            "-w",
            context.prootCwd(),
            "-b",
            "${context.filesDir.absolutePath}:$WORKSPACE_DIR",
        )

        extraBindMounts.forEach { mount ->
            if (mount.source.exists()) {
                command += "-b"
                command += "${mount.source.absolutePath}:${mount.target.trimEnd('/')}"
            }
        }

        listOf("/dev", "/proc", "/sys").forEach { path ->
            if (File(path).exists()) {
                command += "-b"
                command += path
            }
        }

        command += context.linuxDir.rootfsShellCommand() + listOf(
            "-c",
            "cd -- \"\$1\" && eval \"\$2\"",
            "rikkahub",
            context.prootCwd(),
            context.command,
        )
        return command
    }

    private fun WorkspaceShellContext.prootCwd(): String {
        val normalized = cwd.trim().trim('/')
        return if (normalized.isBlank()) {
            WORKSPACE_DIR
        } else {
            "$WORKSPACE_DIR/$normalized"
        }
    }

    private fun File.rootfsShellCommand(): List<String> {
        val shell = ROOTFS_SHELLS.firstOrNull { File(this, it.removePrefix("/")).isFile }
            ?: "/bin/sh"
        return if (shell.endsWith("bash")) {
            listOf(shell, "-l")
        } else {
            listOf(shell)
        }
    }

    private data class ProotAttempt(
        val runtime: ProotRuntime,
        val mode: ProotLaunchMode,
    )

    private data class ProotAttemptFailure(
        val attempt: ProotAttempt,
        val result: WorkspaceCommandResult,
    )

    private fun orderedAttempts(
        context: WorkspaceShellContext,
        runtimes: List<ProotRuntime>,
    ): List<ProotAttempt> {
        val attempts = runtimes.flatMap { runtime ->
            ProotLaunchModes.all.map { mode -> ProotAttempt(runtime, mode) }
        }
        val preferred = ProotLaunchPreferences.read(context.tempDir) ?: return attempts
        return attempts.sortedBy { attempt ->
            if (
                attempt.runtime.name == preferred.runtimeName &&
                attempt.mode.name == preferred.launchModeName
            ) {
                0
            } else {
                1
            }
        }
    }

    private fun WorkspaceCommandResult.shouldTryNextProotAttempt(): Boolean =
        stderr.contains("proot launch failed with", ignoreCase = true) ||
            isProotPreExecFailure()

    private fun WorkspaceCommandResult.isProotPreExecFailure(): Boolean {
        val output = "$stderr\n$stdout"
        return output.contains("proot error:", ignoreCase = true) &&
            (
                output.contains("Function not implemented", ignoreCase = true) ||
                    output.contains("No such file or directory", ignoreCase = true) ||
                    output.contains("can't chmod", ignoreCase = true) ||
                    output.contains("can't chdir", ignoreCase = true) ||
                    output.contains("execve(", ignoreCase = true)
                )
    }

    private fun WorkspaceCommandResult.withLaunchDiagnostics(
        failures: List<ProotAttemptFailure>,
    ): WorkspaceCommandResult {
        val details = failures.joinToString(separator = "\n") { failure ->
            val output = failure.result.stderr.ifBlank { failure.result.stdout }
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            "${failure.attempt.runtime.name}/${failure.attempt.mode.name}: " +
                "exit ${failure.result.exitCode}" +
                if (output.isBlank()) "" else " - $output"
        }
        val diagnostic = "Tried proot launch modes:\n$details\n" +
            "Android still failed while proot was entering the rootfs."
        return copy(
            stderr = if (stderr.isBlank()) diagnostic else "${stderr.trimEnd()}\n\n$diagnostic",
        )
    }

    private companion object {
        private const val WORKSPACE_DIR = "/workspace"
        private const val ROOTFS_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        private val ROOTFS_SHELLS = listOf(
            "/bin/bash",
            "/usr/bin/bash",
            "/bin/sh",
            "/usr/bin/sh",
        )
    }
}
