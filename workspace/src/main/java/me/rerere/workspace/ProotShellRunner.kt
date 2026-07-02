package me.rerere.workspace

import android.os.Build
import java.io.File
import java.io.IOException
import java.nio.file.Files

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
        for (attempt in orderedAttempts(context, runtimes)) {
            val result = runProot(context, attempt.runtime, attempt.mode)
            if (!result.isProotLaunchFailure()) {
                ProotLaunchPreferences.write(context.tempDir, attempt.runtime, attempt.mode)
                return result
            }
            failures += ProotAttemptFailure(attempt, result)
        }

        val verboseDiag = runVerboseDiagnostic(context, runtimes.first())
        return failures.lastOrNull()
            ?.result
            ?.withLaunchDiagnostics(failures, verboseDiag)
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
            val process = ProcessBuilder(buildCommand(context, runtime.executable, mode, runtime))
                .directory(context.filesDir)
                .redirectErrorStream(false)
                .apply {
                    environment()["PROOT_LOADER"] = runtime.loader.absolutePath
                    runtime.loader32?.let { environment()["PROOT_LOADER_32"] = it.absolutePath }
                    environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                    environment()["PROOT_TMPDIR"] = context.tempDir.absolutePath
                    environment()["TMPDIR"] = "/tmp"
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
        mode: ProotLaunchMode,
        runtime: ProotRuntime,
    ): List<String> {
        val command = mutableListOf(proot.absolutePath)

        if (mode.verbose) {
            command += "--verbose=1"
        }
        if (mode.useAshmemMemfd && runtime.supportsAshmemMemfd) {
            command += "--ashmem-memfd"
        }
        if (!mode.minimalFlags) {
            command += "--root-id"
            command += "--link2symlink"
            command += "--kill-on-exit"
        }

        command += listOf(
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

    private fun WorkspaceCommandResult.isProotLaunchFailure(): Boolean {
        val output = "$stderr\n$stdout"
        return output.contains("proot error:", ignoreCase = true) ||
            output.contains("proot launch failed with", ignoreCase = true) ||
            output.contains("proot info:", ignoreCase = true) ||
            output.contains("fatal error: see", ignoreCase = true) ||
            output.contains("ptrace(TRACEME)", ignoreCase = true) ||
            output.contains("Function not implemented", ignoreCase = true) ||
            (exitCode == 127 && output.isBlank())
    }

    private fun runVerboseDiagnostic(
        context: WorkspaceShellContext,
        runtime: ProotRuntime,
    ): String {
        val sanityCheck = runProotSanityCheck(context, runtime)
        val rootfsDiag = diagnoseRootfs(context.linuxDir)
        val shellCheck = runProotShellOnly(context, runtime)
        val verboseResult = runProot(
            context,
            runtime,
            ProotLaunchModes.verboseDiagnostic,
        )
        val verboseOutput = verboseResult.stderr.ifBlank { verboseResult.stdout }
        val trimmed = verboseOutput.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(MAX_VERBOSE_LINES)
        return buildString {
            appendLine("=== proot sanity check ===")
            appendLine(sanityCheck)
            appendLine("=== rootfs diagnostic ===")
            appendLine(rootfsDiag)
            appendLine("=== proot shell-only test ===")
            appendLine(shellCheck)
            appendLine("=== verbose proot output (first $MAX_VERBOSE_LINES lines) ===")
            appendLine(trimmed)
        }
    }

    private fun diagnoseRootfs(linuxDir: File): String {
        return buildString {
            val bashPath = listOf("usr/bin/bash", "bin/bash").firstOrNull { File(linuxDir, it).isFile }
            appendLine("bash: $bashPath (${if (bashPath != null) File(linuxDir, bashPath).length() else "missing"} bytes)")
            val interpreter = bashPath?.let { parseElfInterpreter(File(linuxDir, it)) }
            appendLine("bash ELF interpreter: $interpreter")
            listOf("lib", "lib64", "usr/lib", "usr/lib64").forEach { path ->
                val f = File(linuxDir, path)
                val type = when {
                    Files.isSymbolicLink(f.toPath()) -> "symlink -> ${runCatching { Files.readSymbolicLink(f.toPath()) }.getOrDefault("?")}"
                    f.isDirectory -> "dir (${f.list()?.size ?: 0} entries)"
                    f.isFile -> "file (${f.length()} bytes)"
                    else -> "missing"
                }
                appendLine("  /$path: $type")
            }
            val linkerPaths = listOf(
                "lib/ld-linux-aarch64.so.1",
                "usr/lib/ld-linux-aarch64.so.1",
                "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
                "lib64/ld-linux-x86-64.so.2",
                "usr/lib64/ld-linux-x86-64.so.2",
                "lib/ld-linux-armhf.so.3",
                "usr/lib/ld-linux-armhf.so.3",
                "lib/arm-linux-gnueabihf/ld-linux-armhf.so.3",
                "usr/lib/arm-linux-gnueabihf/ld-linux-armhf.so.3",
            )
            linkerPaths.forEach { path ->
                val f = File(linuxDir, path)
                if (f.exists() || Files.isSymbolicLink(f.toPath())) {
                    val type = if (Files.isSymbolicLink(f.toPath())) "symlink" else "file (${f.length()} bytes)"
                    appendLine("  /$path: $type")
                }
            }
        }
    }

    private fun parseElfInterpreter(file: File): String? {
        return runCatching {
            val bytes = file.readBytes()
            if (bytes.size < 64 || bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte()) return null
            val is64Bit = bytes[4] == 2.toByte()
            val phdrOffset = if (is64Bit) {
                bytes.toInt32(32).toLong() // e_phoff
            } else {
                bytes.toInt32(28).toLong()
            }
            val phdrSize = if (is64Bit) 56 else 32
            val phdrCount = bytes.toInt16(if (is64Bit) 56 else 44) // e_phnum
            for (i in 0 until phdrCount) {
                val offset = (phdrOffset + i * phdrSize).toInt()
                val pType = bytes.toInt32(offset)
                if (pType == 3) { // PT_INTERP
                    val interpOffset = if (is64Bit) bytes.toInt32(offset + 8).toLong() else bytes.toInt32(offset + 8).toLong()
                    val interpSize = if (is64Bit) bytes.toInt32(offset + 32).toInt() else bytes.toInt32(offset + 16).toInt()
                    val interp = bytes.copyOfRange(interpOffset.toInt(), interpOffset.toInt() + interpSize)
                    return String(interp).trimEnd('\u0000')
                }
            }
            null
        }.getOrNull()
    }

    private fun ByteArray.toInt32(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.toInt16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun runProotSanityCheck(
        context: WorkspaceShellContext,
        runtime: ProotRuntime,
    ): String {
        return try {
            val process = ProcessBuilder(
                runtime.executable.absolutePath,
                "--version",
            ).apply {
                environment()["LD_LIBRARY_PATH"] = runtime.executable.parentFile?.absolutePath.orEmpty()
                environment()["PROOT_LOADER"] = runtime.loader.absolutePath
                environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
            }.start()
            val result = process.readResult(5000L, null)
            "exit ${result.exitCode}, output: ${result.stdout.trim().take(200)}"
        } catch (e: Exception) {
            "failed to start: ${e.message}"
        }
    }

    private fun runProotShellOnly(
        context: WorkspaceShellContext,
        runtime: ProotRuntime,
    ): String {
        return try {
            val mode = ProotLaunchModes.noMemfd
            val process = ProcessBuilder(buildShellOnlyCommand(context, runtime.executable, mode, runtime))
                .directory(context.filesDir)
                .redirectErrorStream(false)
                .apply {
                    environment()["PROOT_LOADER"] = runtime.loader.absolutePath
                    environment()["PROOT_TMP_DIR"] = context.tempDir.absolutePath
                    environment()["PROOT_TMPDIR"] = context.tempDir.absolutePath
                    environment()["TMPDIR"] = "/tmp"
                    environment()["LD_LIBRARY_PATH"] = runtime.executable.parentFile?.absolutePath.orEmpty()
                    environment().putAll(mode.environment)
                }
                .start()
            val result = process.readResult(10000L, "echo SHELL_OK\n".toByteArray())
            "exit ${result.exitCode}, stdout: ${result.stdout.trim().take(200)}, stderr: ${result.stderr.trim().take(200)}"
        } catch (e: Exception) {
            "failed to start: ${e.message}"
        }
    }

    private fun buildShellOnlyCommand(
        context: WorkspaceShellContext,
        proot: File,
        mode: ProotLaunchMode,
        runtime: ProotRuntime,
    ): List<String> {
        val command = mutableListOf(proot.absolutePath)
        if (mode.useAshmemMemfd && runtime.supportsAshmemMemfd) {
            command += "--ashmem-memfd"
        }
        if (!mode.minimalFlags) {
            command += "--root-id"
            command += "--link2symlink"
            command += "--kill-on-exit"
        }
        command += listOf(
            "-r",
            context.linuxDir.absolutePath,
            "-w",
            "/",
            "-b",
            "${context.filesDir.absolutePath}:$WORKSPACE_DIR",
        )
        listOf("/dev", "/proc", "/sys").forEach { path ->
            if (File(path).exists()) {
                command += "-b"
                command += path
            }
        }
        val shell = ROOTFS_SHELLS.firstOrNull { File(context.linuxDir, it.removePrefix("/")).isFile }
            ?: "/bin/sh"
        command += shell
        return command
    }

    private fun WorkspaceCommandResult.withLaunchDiagnostics(
        failures: List<ProotAttemptFailure>,
        verboseDiagnostic: String,
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
        val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        val diagnostic = "Tried proot launch modes:\n$details\n" +
            "$deviceInfo\n" +
            "Android still failed while proot was entering the rootfs.\n\n" +
            verboseDiagnostic
        return copy(
            stderr = if (stderr.isBlank()) diagnostic else "${stderr.trimEnd()}\n\n$diagnostic",
        )
    }

    private companion object {
        private const val WORKSPACE_DIR = "/workspace"
        private const val ROOTFS_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        private const val MAX_VERBOSE_LINES = 200
        private val ROOTFS_SHELLS = listOf(
            "/bin/bash",
            "/usr/bin/bash",
            "/bin/sh",
            "/usr/bin/sh",
        )
    }
}
