package me.rerere.workspace

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class WorkspaceManager(
    private val baseDir: File,
    private val config: WorkspaceConfig = WorkspaceConfig(),
    private val shellRunner: WorkspaceShellRunner = HostShellRunner(),
) {
    private val fileSystem = WorkspaceFileSystem(config)

    init {
        baseDir.mkdirs()
    }

    fun ensureWorkspace(root: String): File {
        val dir = workspaceDir(root)
        filesDir(root).mkdirs()
        linuxDir(root).mkdirs()
        tempDir(root).mkdirs()
        return dir
    }

    fun workspaceDir(root: String): File {
        requireValidRoot(root)
        return File(baseDir, root)
    }

    fun filesDir(root: String): File = File(workspaceDir(root), FILES_DIR)

    fun linuxDir(root: String): File = File(workspaceDir(root), LINUX_DIR)

    fun tempDir(root: String): File = File(workspaceDir(root), TEMP_DIR)

    fun hasRootfs(root: String): Boolean = linuxDir(root).hasUsableRootfs()

    fun deleteWorkspace(root: String): Boolean = workspaceDir(root).deleteRecursively()

    fun listFiles(
        root: String,
        path: String = "",
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): List<WorkspaceFileEntry> =
        fileSystem.list(areaDir(root, area), path)

    fun readText(
        root: String,
        path: String,
        charset: Charset = StandardCharsets.UTF_8,
    ): String = fileSystem.readText(filesDir(root), path, charset)

    fun writeText(
        root: String,
        path: String,
        text: String,
        overwrite: Boolean = true,
        charset: Charset = StandardCharsets.UTF_8,
    ): WorkspaceFileEntry = fileSystem.writeText(filesDir(root), path, text, overwrite, charset)

    fun importFile(
        root: String,
        destinationPath: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry {
        val areaRoot = areaDir(root, area)
        val targetPath = if (destinationPath.isBlank()) fileName else "$destinationPath/$fileName"
        return fileSystem.importBytes(areaRoot, targetPath, inputStream)
    }

    fun fileSize(
        root: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): Long {
        val file = fileSystem.resolve(areaDir(root, area), path)
        require(file.exists()) { "File does not exist: $path" }
        require(file.isFile) { "Path is not a file: $path" }
        return file.length()
    }

    fun exportFile(
        root: String,
        path: String,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
        outputStream: OutputStream,
    ) {
        val file = fileSystem.resolve(areaDir(root, area), path)
        require(file.exists()) { "File does not exist: $path" }
        require(file.isFile) { "Path is not a file: $path" }
        outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    fun deleteFile(
        root: String,
        path: String,
        recursive: Boolean = false,
        area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    ): Boolean =
        fileSystem.delete(areaDir(root, area), path, recursive)

    fun moveFile(root: String, source: String, target: String, overwrite: Boolean = false): WorkspaceFileEntry =
        fileSystem.move(filesDir(root), source, target, overwrite)

    fun glob(root: String, pattern: String, path: String = ""): List<WorkspaceFileEntry> =
        fileSystem.glob(filesDir(root), pattern, path)

    fun grep(
        root: String,
        query: String,
        path: String = "",
        regex: Boolean = false,
        ignoreCase: Boolean = true,
        includeGlob: String? = null,
    ): List<WorkspaceSearchMatch> =
        fileSystem.grep(filesDir(root), query, path, regex, ignoreCase, includeGlob)

    fun executeCommand(
        root: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
    ): WorkspaceCommandResult {
        require(command.isNotBlank()) { "Command is required" }
        val workingDir = fileSystem.resolve(filesDir(root), cwd)
        require(workingDir.exists()) { "Working directory does not exist: $cwd" }
        require(workingDir.isDirectory) { "Working path is not a directory: $cwd" }

        return shellRunner.execute(
            WorkspaceShellContext(
                root = root,
                command = command,
                cwd = cwd,
                filesDir = filesDir(root),
                linuxDir = linuxDir(root),
                tempDir = tempDir(root),
                workingDir = workingDir,
                timeoutMillis = timeoutMillis,
                stdin = stdin,
            )
        )
    }

    private fun requireValidRoot(root: String) {
        require(root.matches(ROOT_NAME_REGEX)) {
            "Invalid workspace root name: $root"
        }
    }

    private fun areaDir(root: String, area: WorkspaceStorageArea): File = when (area) {
        WorkspaceStorageArea.FILES -> filesDir(root)
        WorkspaceStorageArea.LINUX -> linuxDir(root)
    }

    fun cleanupAllTempDirs() {
        val roots = baseDir.listFiles()?.filter { it.isDirectory } ?: return
        for (dir in roots) {
            val root = dir.name
            if (!root.matches(ROOT_NAME_REGEX)) continue
            // PRoot temp files
            tempDir(root).let { if (it.exists()) it.deleteRecursively() }
            // Rootfs /tmp and /var/tmp
            File(linuxDir(root), "tmp").let { if (it.exists()) it.deleteRecursively() }
            File(linuxDir(root), "var/tmp").let { if (it.exists()) it.deleteRecursively() }
        }
    }

    companion object {
        private const val FILES_DIR = "files"
        private const val LINUX_DIR = "linux"
        private const val TEMP_DIR = "tmp"
        const val DEFAULT_COMMAND_TIMEOUT_MS = 30_000L
        private val ROOT_NAME_REGEX = Regex("[A-Za-z0-9._-]+")
    }
}

fun File.hasUsableRootfs(): Boolean {
    if (!isDirectory) return false
    val hasShell = listOf(
        "bin/bash",
        "usr/bin/bash",
        "bin/sh",
        "usr/bin/sh",
    ).any { File(this, it).isFile }
    return hasShell
}

fun File.rootfsInterpreter(): String? {
    val candidate = listOf(
        "bin/bash",
        "usr/bin/bash",
        "bin/sh",
        "usr/bin/sh",
    ).map { File(this, it) }.firstOrNull { it.isFile } ?: return null

    return runCatching { candidate.elfInterpreter() }.getOrNull()
}

private fun File.elfInterpreter(): String? {
    val bytes = readBytes()
    if (bytes.size < 64) return null
    if (bytes[0] != 0x7f.toByte() || bytes[1] != 'E'.code.toByte() ||
        bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
    ) {
        return null
    }
    val is64Bit = bytes[4].toInt() == 2
    val littleEndian = bytes[5].toInt() == 1
    val phoff = if (is64Bit) {
        readLong(bytes, 32, littleEndian)
    } else {
        readInt(bytes, 28, littleEndian).toLong()
    }
    val phentsize = readShort(bytes, if (is64Bit) 54 else 42, littleEndian)
    val phnum = readShort(bytes, if (is64Bit) 56 else 44, littleEndian)

    if (phoff <= 0 || phnum <= 0 || phentsize <= 0) return null
    if (phoff + phnum.toLong() * phentsize > bytes.size) return null

    for (i in 0 until phnum) {
        val base = (phoff + i.toLong() * phentsize).toInt()
        val ptype = readInt(bytes, base, littleEndian)
        if (ptype == 3) {
            val offset = if (is64Bit) readLong(bytes, base + 8, littleEndian) else readInt(bytes, base + 4, littleEndian).toLong()
            val filesz = if (is64Bit) readLong(bytes, base + 32, littleEndian) else readInt(bytes, base + 16, littleEndian).toLong()
            if (offset <= 0 || filesz <= 0 || filesz > 4096) return null
            if (offset + filesz > bytes.size) return null
            return String(bytes, offset.toInt(), filesz.toInt(), Charsets.UTF_8).trimEnd('\u0000')
        }
    }
    return null
}

private fun readShort(data: ByteArray, offset: Int, littleEndian: Boolean): Int {
    return if (littleEndian) {
        (data[offset].toInt() and 0xff) or ((data[offset + 1].toInt() and 0xff) shl 8)
    } else {
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
    }
}

private fun readInt(data: ByteArray, offset: Int, littleEndian: Boolean): Int {
    return if (littleEndian) {
        (data[offset].toInt() and 0xff) or
            ((data[offset + 1].toInt() and 0xff) shl 8) or
            ((data[offset + 2].toInt() and 0xff) shl 16) or
            ((data[offset + 3].toInt() and 0xff) shl 24)
    } else {
        ((data[offset].toInt() and 0xff) shl 24) or
            ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or
            (data[offset + 3].toInt() and 0xff)
    }
}

private fun readLong(data: ByteArray, offset: Int, littleEndian: Boolean): Long {
    return if (littleEndian) {
        (data[offset].toLong() and 0xff) or
            ((data[offset + 1].toLong() and 0xff) shl 8) or
            ((data[offset + 2].toLong() and 0xff) shl 16) or
            ((data[offset + 3].toLong() and 0xff) shl 24) or
            ((data[offset + 4].toLong() and 0xff) shl 32) or
            ((data[offset + 5].toLong() and 0xff) shl 40) or
            ((data[offset + 6].toLong() and 0xff) shl 48) or
            ((data[offset + 7].toLong() and 0xff) shl 56)
    } else {
        ((data[offset].toLong() and 0xff) shl 56) or
            ((data[offset + 1].toLong() and 0xff) shl 48) or
            ((data[offset + 2].toLong() and 0xff) shl 40) or
            ((data[offset + 3].toLong() and 0xff) shl 32) or
            ((data[offset + 4].toLong() and 0xff) shl 24) or
            ((data[offset + 5].toLong() and 0xff) shl 16) or
            ((data[offset + 6].toLong() and 0xff) shl 8) or
            (data[offset + 7].toLong() and 0xff)
    }
}

enum class WorkspaceRootfsArchitecture(
    val displayName: String,
    val ubuntuArch: String,
    private val elfMachine: Int,
) {
    ARM64("arm64", "arm64", 183),
    AMD64("amd64", "amd64", 62),
    ARMHF("armhf", "armhf", 40);

    companion object {
        fun fromElfMachine(machine: Int): WorkspaceRootfsArchitecture? =
            entries.firstOrNull { it.elfMachine == machine }

        fun fromNativeLibraryDir(nativeLibraryDir: File): WorkspaceRootfsArchitecture? {
            val path = nativeLibraryDir.absolutePath.lowercase()
            return when {
                "x86_64" in path -> AMD64
                "arm64" in path || "aarch64" in path -> ARM64
                "armeabi" in path || "/arm" in path || "\\arm" in path -> ARMHF
                else -> null
            }
        }
    }
}

fun File.detectRootfsArchitecture(): WorkspaceRootfsArchitecture? {
    val candidate = listOf(
        "bin/bash",
        "usr/bin/bash",
        "bin/sh",
        "usr/bin/sh",
        "usr/bin/env",
        "bin/env",
    ).map { File(this, it) }.firstOrNull { it.isFile } ?: return null

    candidate.inputStream().use { input ->
        val header = ByteArray(20)
        if (input.read(header) != header.size) return null
        if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val littleEndian = header[5].toInt() == 1
        val machine = if (littleEndian) {
            (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        } else {
            ((header[18].toInt() and 0xff) shl 8) or (header[19].toInt() and 0xff)
        }
        return WorkspaceRootfsArchitecture.fromElfMachine(machine)
    }
}
