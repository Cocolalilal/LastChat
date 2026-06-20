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
                    "Expected libproot_exec.so/libproot_loader.so or libproot.so/libproot-loader.so.",
            )
        }

        context.tempDir.mkdirs()
        context.tempDir.setReadable(true, true)
        context.tempDir.setWritable(true, true)
        context.tempDir.setExecutable(true, true)
        patcher.patch(context.linuxDir)

        var lastFailure: WorkspaceCommandResult? = null
        val attemptedModes = mutableListOf<String>()
        orderedAttempts(context, runtimes).forEach { attempt ->
            attemptedModes += "${attempt.runtime.name}/${attempt.mode.name}"
            val result = runProot(context, attempt.runtime, attempt.mode)
            if (!result.shouldTryNextProotAttempt()) {
                ProotLaunchPreferences.write(context.tempDir, attempt.runtime, attempt.mode)
                return result
            }
            lastFailure = result
        }

        return lastFailure
            ?.withLaunchDiagnostics(attemptedModes)
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
                    environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                    environment()["PROOT_TMPDIR"] = context.tempDir.absolutePath
                    environment()["TMPDIR"] = context.tempDir.absolutePath
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

        command += listOf(
            "/usr/bin/env",
            "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            "/bin/bash",
            "-l",
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

    private data class ProotAttempt(
        val runtime: ProotRuntime,
        val mode: ProotLaunchMode,
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
            isProotFunctionNotImplemented()

    private fun WorkspaceCommandResult.isProotFunctionNotImplemented(): Boolean {
        val output = "$stderr\n$stdout"
        return output.contains("proot error:", ignoreCase = true) &&
            output.contains("Function not implemented", ignoreCase = true)
    }

    private fun WorkspaceCommandResult.withLaunchDiagnostics(attemptedModes: List<String>): WorkspaceCommandResult {
        val details = attemptedModes.joinToString()
        val diagnostic = "Tried proot launch modes: $details. " +
            "Android still returned Function not implemented while proot was entering the rootfs."
        return copy(
            stderr = if (stderr.isBlank()) diagnostic else "${stderr.trimEnd()}\n\n$diagnostic",
        )
    }

    private companion object {
        private const val WORKSPACE_DIR = "/workspace"
    }
}
