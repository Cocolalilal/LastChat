package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

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

data class LinuxInstallResult(
    val success: Boolean,
    val status: LinuxEnvironmentStatus,
    val message: String,
)

class LinuxEnvironmentManager(private val context: Context) {
    private val baseDir = File(context.filesDir, "linux_env")
    val rootfsDir: File = File(baseDir, "rootfs")
    private val downloadsDir: File = File(baseDir, "downloads")
    private val logsDir: File = File(baseDir, "logs")
    private val binDir: File = File(baseDir, "bin")
    val runnerFile: File = File(binDir, "proot")

    suspend fun installOrRepair(fullToolchain: Boolean): LinuxInstallResult = withContext(Dispatchers.IO) {
        runCatching {
            baseDir.mkdirs()
            downloadsDir.mkdirs()
            logsDir.mkdirs()
            if (!isRootfsInstalled()) {
                installRootfs()
            }
            val runnerInstalled = ensureRunnerInstalled()
            if (!runnerInstalled) {
                val status = getStatus()
                return@withContext LinuxInstallResult(
                    success = false,
                    status = status,
                    message = "Alpine rootfs installed, but PRoot runner asset is missing for ${status.runnerAbi}.",
                )
            }
            configureRootfs()
            bootstrapPackages(fullToolchain)
            val status = getStatus()
            LinuxInstallResult(
                success = status.ready,
                status = status,
                message = if (status.ready) "Linux environment is ready." else "Linux setup did not reach ready state.",
            )
        }.getOrElse { error ->
            val status = getStatus()
            LinuxInstallResult(
                success = false,
                status = status,
                message = error.message ?: "Linux setup failed",
            )
        }
    }

    fun getStatus(): LinuxEnvironmentStatus {
        val rootfsInstalled = isRootfsInstalled()
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

    private fun isRootfsInstalled(): Boolean {
        return File(rootfsDir, "bin/sh").isFile && File(rootfsDir, "etc/alpine-release").isFile
    }

    private fun installRootfs() {
        val alpineArch = alpineArchForDevice()
        val release = resolveLatestMiniRootfs(alpineArch)
        val archive = File(downloadsDir, release.fileName)
        downloadFile(release.url, archive)
        verifySha256(archive, release.sha256)

        val tempRootfs = File(baseDir, "rootfs.tmp").apply {
            deleteRecursively()
            mkdirs()
        }
        GZIPInputStream(archive.inputStream().buffered()).use { input ->
            extractTar(input, tempRootfs)
        }
        require(File(tempRootfs, "bin/sh").isFile) { "Downloaded Alpine rootfs did not contain /bin/sh" }
        require(File(tempRootfs, "etc/alpine-release").isFile) { "Downloaded Alpine rootfs did not contain /etc/alpine-release" }

        rootfsDir.deleteRecursively()
        require(tempRootfs.renameTo(rootfsDir)) { "Failed to move Alpine rootfs into place" }
    }

    private fun resolveLatestMiniRootfs(alpineArch: String): AlpineMiniRootfsRelease {
        val baseUrl = "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$alpineArch/"
        val indexHtml = URL(baseUrl).readText()
        val regex = Regex("""alpine-minirootfs-([0-9][^"]*)-$alpineArch\.tar\.gz""")
        val fileName = regex.findAll(indexHtml)
            .map { it.value }
            .distinct()
            .maxByOrNull(::versionSortScore)
            ?: error("No Alpine minirootfs found for $alpineArch")
        val shaText = URL("$baseUrl$fileName.sha256").readText()
        val sha256 = shaText.trim().substringBefore(' ').trim()
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid SHA-256 for $fileName" }
        return AlpineMiniRootfsRelease(
            fileName = fileName,
            url = "$baseUrl$fileName",
            sha256 = sha256.lowercase(),
        )
    }

    private fun versionSortScore(fileName: String): Long {
        return Regex("""alpine-minirootfs-([0-9.]+)-""")
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.split('.')
            ?.map { it.toIntOrNull() ?: 0 }
            .orEmpty()
            .fold(0L) { acc, part -> (acc * 1000L) + part }
    }

    private fun downloadFile(url: String, destination: File) {
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        tempFile.delete()
        URL(url).openStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        destination.delete()
        require(tempFile.renameTo(destination)) { "Failed to save ${destination.name}" }
    }

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        require(actual == expected) { "Checksum mismatch for ${file.name}" }
    }

    private fun configureRootfs() {
        File(rootfsDir, "etc/apk").mkdirs()
        File(rootfsDir, "etc/apk/repositories").writeText(
            """
            https://dl-cdn.alpinelinux.org/alpine/latest-stable/main
            https://dl-cdn.alpinelinux.org/alpine/latest-stable/community
            """.trimIndent() + "\n"
        )
        File(rootfsDir, "etc/resolv.conf").writeText(
            """
            nameserver 1.1.1.1
            nameserver 8.8.8.8
            """.trimIndent() + "\n"
        )
    }

    private fun bootstrapPackages(fullToolchain: Boolean) {
        val basePackages = listOf(
            "bash",
            "ca-certificates",
            "curl",
            "git",
            "python3",
            "py3-pip",
            "nodejs",
            "npm",
            "openjdk17",
        )
        val fullPackages = listOf(
            "build-base",
            "clang",
            "cmake",
            "make",
            "pkgconf",
            "rust",
            "tar",
            "gzip",
            "zip",
            "unzip",
        )
        val packages = basePackages + if (fullToolchain) fullPackages else emptyList()
        val result = runRootfsCommand("apk update && apk add --no-cache ${packages.joinToString(" ")}", timeoutSeconds = 300)
        File(logsDir, "bootstrap.log").writeText(
            buildString {
                appendLine("exit=${result.exitCode}")
                appendLine("timed_out=${result.timedOut}")
                appendLine("--- stdout ---")
                appendLine(result.stdout)
                appendLine("--- stderr ---")
                appendLine(result.stderr)
                result.error?.let { appendLine(it) }
            }
        )
        require(result.exitCode == 0 && !result.timedOut) { result.error ?: result.stderr.ifBlank { "Package bootstrap failed" } }
    }

    internal fun runRootfsCommand(command: String, timeoutSeconds: Long): LinuxCommandResult {
        val workspace = File(baseDir, "setup-workspace").apply { mkdirs() }
        val process = buildRootfsProcess(command = command, workspaceDir = workspace).start()
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
        return LinuxCommandResult(
            ready = true,
            exitCode = if (finished) process.exitValue() else null,
            stdout = stdoutThread.text.truncateLinuxOutput(),
            stderr = stderrThread.text.truncateLinuxOutput(),
            timedOut = !finished,
            error = if (finished) null else "Command timed out after ${timeoutSeconds}s",
        )
    }

    internal fun buildRootfsProcess(command: String, workspaceDir: File): ProcessBuilder {
        workspaceDir.mkdirs()
        return ProcessBuilder(
            runnerFile.absolutePath,
            "-0",
            "-R",
            rootfsDir.absolutePath,
            "-b",
            "${workspaceDir.absolutePath}:/workspace",
            "-b",
            "/proc:/proc",
            "-b",
            "/dev:/dev",
            "-w",
            "/workspace",
            "/bin/sh",
            "-lc",
            command,
        ).redirectErrorStream(false)
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

    private fun alpineArchForDevice(): String {
        return when (currentAbi()) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "armv7"
            "x86_64" -> "x86_64"
            else -> error("Unsupported Linux environment ABI: ${currentAbi()}")
        }
    }

    private fun extractTar(input: InputStream, destination: File) {
        val header = ByteArray(TAR_BLOCK_SIZE)
        while (true) {
            val read = input.readFullyOrEnd(header)
            if (!read) break
            if (header.all { it == 0.toByte() }) break

            val name = tarString(header, 0, 100)
            val mode = tarOctal(header, 100, 8).toInt()
            val size = tarOctal(header, 124, 12)
            val type = header[156].toInt().toChar()
            val linkName = tarString(header, 157, 100)
            val prefix = tarString(header, 345, 155)
            val fullName = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
            val target = safeTarDestination(destination, fullName)

            when (type) {
                '5' -> target.mkdirs()
                '2' -> {
                    target.parentFile?.mkdirs()
                    runCatching {
                        Os.symlink(linkName, target.absolutePath)
                    }
                }
                '0', '\u0000' -> {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        input.copyExactly(output, size)
                    }
                    target.setExecutable(mode and 0b001001001 != 0, false)
                    target.setReadable(true, false)
                    target.setWritable(mode and 0b010010010 != 0, false)
                }
                else -> input.skipExactly(size)
            }
            input.skipExactly(paddingForTar(size))
        }
    }

    private fun safeTarDestination(root: File, entryName: String): File {
        require(entryName.isNotBlank()) { "Blank tar entry" }
        val target = File(root, entryName.removePrefix("./")).canonicalFile
        val canonicalRoot = root.canonicalFile
        val insideRoot = target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)
        require(insideRoot) { "Invalid tar entry path: $entryName" }
        return target
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { index -> header[index] == 0.toByte() } ?: (offset + length)
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(header: ByteArray, offset: Int, length: Int): Long {
        return tarString(header, offset, length).trim().ifBlank { "0" }.toLong(8)
    }

    private fun paddingForTar(size: Long): Long {
        val remainder = size % TAR_BLOCK_SIZE
        return if (remainder == 0L) 0L else TAR_BLOCK_SIZE - remainder
    }

    private data class AlpineMiniRootfsRelease(
        val fileName: String,
        val url: String,
        val sha256: String,
    )

    private companion object {
        const val TAR_BLOCK_SIZE = 512
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
        val process = environmentManager.buildRootfsProcess(trimmedCommand, workspaceDir).start()

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

private fun InputStream.readFullyOrEnd(buffer: ByteArray): Boolean {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) {
            return offset != 0
        }
        offset += read
    }
    return true
}

private fun InputStream.copyExactly(output: java.io.OutputStream, byteCount: Long) {
    var remaining = byteCount
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (remaining > 0L) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        require(read >= 0) { "Unexpected end of tar archive" }
        output.write(buffer, 0, read)
        remaining -= read
    }
}

private fun InputStream.skipExactly(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() < 0) {
                throw java.io.EOFException("Unexpected end of stream")
            }
            remaining -= 1L
        } else {
            remaining -= skipped
        }
    }
}
