package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class ExampleUnitTest {
    @Test
    fun fileOperationsWorkInsideWorkspaceRoot() {
        val root = Files.createTempDirectory("workspace-test").toFile()
        val fileSystem = WorkspaceFileSystem()

        fileSystem.writeText(root, "src/main.txt", "hello\nworkspace")

        assertEquals("hello\nworkspace", fileSystem.readText(root, "src/main.txt"))
        assertEquals(listOf("src"), fileSystem.list(root).map { it.path })
        assertEquals(listOf("src/main.txt"), fileSystem.glob(root, "**/*.txt").map { it.path })
        assertEquals(
            listOf(WorkspaceSearchMatch(path = "src/main.txt", line = 2, text = "workspace")),
            fileSystem.grep(root, "workspace"),
        )
    }

    @Test
    fun pathEscapeIsRejected() {
        val root = Files.createTempDirectory("workspace-test").toFile()
        val fileSystem = WorkspaceFileSystem()

        var rejected = false
        try {
            fileSystem.writeText(root, "../escape.txt", "nope")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun rootfsRequiresShellEntryPoint() {
        val baseDir = Files.createTempDirectory("workspace-manager-test").toFile()
        val manager = WorkspaceManager(baseDir)
        val root = "test-workspace"
        manager.ensureWorkspace(root)

        assertFalse(manager.hasRootfs(root))

        File(manager.linuxDir(root), "etc").mkdirs()
        assertFalse(manager.hasRootfs(root))

        File(manager.linuxDir(root), "usr/bin").mkdirs()
        File(manager.linuxDir(root), "usr/bin/env").writeText("#!/bin/sh\n")
        assertFalse(manager.hasRootfs(root))

        File(manager.linuxDir(root), "bin").mkdirs()
        File(manager.linuxDir(root), "bin/bash").writeText("#!/bin/bash\n")
        assertTrue(manager.hasRootfs(root))
    }

    @Test
    fun rootfsInstallerDownloadsAndExtractsTarGz() {
        val baseDir = Files.createTempDirectory("workspace-manager-test").toFile()
        val manager = WorkspaceManager(baseDir)
        val installer = RootfsInstaller(manager)
        val archive = tarGz(
            TarTestEntry("bin/", type = '5'),
            TarTestEntry("bin/bash", content = "#!/bin/bash\n".toByteArray(), mode = 493),
            TarTestEntry("bin/hello", content = "echo hello\n".toByteArray(), mode = 493),
            TarTestEntry("usr/", type = '5'),
            TarTestEntry("usr/bin/", type = '5'),
            TarTestEntry("usr/bin/env", content = "#!/bin/sh\n".toByteArray(), mode = 493),
            TarTestEntry("usr/bin/hello-link", type = '2', linkName = "../../bin/hello"),
        )
        SingleResponseHttpServer(archive).use { server ->
            val root = "test-workspace"
            installer.install(root, "http://127.0.0.1:${server.port}/rootfs.tar.gz")

            val linuxDir = manager.linuxDir(root)
            assertEquals("echo hello\n", File(linuxDir, "bin/hello").readText())
            assertTrue(File(linuxDir, "bin/hello").canExecute())
            val link = File(linuxDir, "usr/bin/hello-link")
            assertTrue(Files.isSymbolicLink(link.toPath()) || link.readText() == "echo hello\n")
        }
    }

    @Test
    fun commandRunsInsideWorkspaceFilesDirectory() {
        val baseDir = Files.createTempDirectory("workspace-command-test").toFile()
        val manager = WorkspaceManager(baseDir, shellRunner = TestShellRunner())
        val root = "test-workspace"
        manager.ensureWorkspace(root)

        val result = manager.executeCommand(root, "printf hello > command.txt && cat command.txt")

        assertEquals(0, result.exitCode)
        assertEquals("hello", result.stdout)
        assertEquals("hello", File(manager.filesDir(root), "command.txt").readText())
    }

    @Test
    fun commandReceivesStdin() {
        val baseDir = Files.createTempDirectory("workspace-stdin-test").toFile()
        val manager = WorkspaceManager(baseDir, shellRunner = TestShellRunner())
        val root = "test-workspace"
        manager.ensureWorkspace(root)

        val result = manager.executeCommand(
            root = root,
            command = "cat > stdin.txt",
            stdin = "hello\nstdin".toByteArray(),
        )

        assertEquals(0, result.exitCode)
        assertEquals("hello\nstdin", File(manager.filesDir(root), "stdin.txt").readText())
    }

    @Test
    fun prootRunnerRequiresRootfs() {
        val baseDir = Files.createTempDirectory("workspace-proot-test").toFile()
        val manager = WorkspaceManager(
            baseDir = baseDir,
            shellRunner = ProotShellRunner(File(baseDir, "native"))
        )
        val root = "test-workspace"
        manager.ensureWorkspace(root)

        val result = manager.executeCommand(root, "cat /etc/os-release")

        assertEquals(127, result.exitCode)
        assertEquals("Rootfs is not installed", result.stderr)
    }

    @Test
    fun detectsRootfsArchitectureFromElfHeader() {
        val linuxDir = Files.createTempDirectory("rootfs-arch-test").toFile()
        val bash = File(linuxDir, "usr/bin/bash").apply {
            parentFile?.mkdirs()
            writeBytes(elfHeader(machine = 183))
        }

        assertTrue(bash.isFile)
        assertEquals(WorkspaceRootfsArchitecture.ARM64, linuxDir.detectRootfsArchitecture())
        assertEquals(
            WorkspaceRootfsArchitecture.AMD64,
            WorkspaceRootfsArchitecture.fromNativeLibraryDir(File("/data/app/pkg/lib/x86_64")),
        )
    }

    @Test
    fun prootRuntimesResolveAllAvailableLaunchers() {
        val nativeLibraryDir = Files.createTempDirectory("proot-runtimes-test").toFile()
        File(nativeLibraryDir, "libproot_exec.so").writeText("")
        File(nativeLibraryDir, "libproot_loader.so").writeText("")
        File(nativeLibraryDir, "libproot-userland.so").writeText("")
        File(nativeLibraryDir, "libproot.so").writeText("")
        File(nativeLibraryDir, "libproot-loader.so").writeText("")

        assertEquals(
            listOf("workspace", "termux-userland", "termux"),
            ProotRuntimes.resolve(nativeLibraryDir).map { it.name },
        )
    }

    @Test
    fun prootRuntimesResolveWorkspaceLauncherOnly() {
        val nativeLibraryDir = Files.createTempDirectory("proot-runtimes-test-workspace-only").toFile()
        File(nativeLibraryDir, "libproot_exec.so").writeText("")
        File(nativeLibraryDir, "libproot_loader.so").writeText("")

        assertEquals(
            listOf("workspace"),
            ProotRuntimes.resolve(nativeLibraryDir).map { it.name },
        )
    }

    @Test
    fun prootLaunchPreferenceRoundTrips() {
        val tempDir = Files.createTempDirectory("proot-launch-pref-test").toFile()
        val nativeLibraryDir = Files.createTempDirectory("proot-launch-pref-native").toFile()
        val runtime = ProotRuntime(
            name = "workspace",
            executable = File(nativeLibraryDir, "libproot_exec.so"),
            loader = File(nativeLibraryDir, "libproot_loader.so"),
            loader32 = null,
        )

        ProotLaunchPreferences.write(tempDir, runtime, ProotLaunchModes.noSeccomp)

        assertEquals(
            ProotLaunchSelection("workspace", "no-seccomp"),
            ProotLaunchPreferences.read(tempDir),
        )
    }

    @Test
    fun commandOutputIsTruncatedAtLimit() {
        val baseDir = Files.createTempDirectory("workspace-truncate-test").toFile()
        val manager = WorkspaceManager(baseDir, shellRunner = TestShellRunner())
        val root = "test-workspace"
        manager.ensureWorkspace(root)

        val result = manager.executeCommand(
            root,
            "awk 'BEGIN { for (i = 0; i < 300000; i++) printf \"a\" }'",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.truncated)
        assertEquals(MAX_OUTPUT_CHARS, result.stdout.length)
    }

    @Test
    fun rootfsPatcherAppliesAndroidProotDefaults() {
        val linuxDir = Files.createTempDirectory("rootfs-patch-test").toFile()
        File(linuxDir, "etc").mkdirs()
        File(linuxDir, "etc/resolv.conf").writeText("nameserver 127.0.0.53\n")
        File(linuxDir, "etc/group").writeText("root:x:0:\n")
        File(linuxDir, "usr/bin").mkdirs()
        File(linuxDir, "usr/bin/bash").writeText("#!/bin/bash\n")
        File(linuxDir, "usr/bin/env").writeText("#!/bin/sh\n")
        File(linuxDir, "usr/sbin").mkdirs()
        File(linuxDir, "usr/lib").mkdirs()

        RootfsPatcher().patch(
            linuxDir,
            RootfsPatchOptions(
                nameservers = listOf("9.9.9.9", "8.8.8.8"),
                hostname = "workspace-test",
                groupIds = listOf(3003, 9997),
            )
        )

        assertEquals(
            """
            # Generated by RikkaHub workspace.
            nameserver 9.9.9.9
            nameserver 8.8.8.8
            options edns0 trust-ad

            """.trimIndent(),
            File(linuxDir, "etc/resolv.conf").readText()
        )
        assertTrue(File(linuxDir, "etc/hosts").readText().contains("127.0.0.1 localhost workspace-test"))
        assertEquals("workspace-test\n", File(linuxDir, "etc/hostname").readText())
        assertEquals("LANG=C.UTF-8\n", File(linuxDir, "etc/default/locale").readText())
        assertTrue(File(linuxDir, "etc/group").readText().contains("android_gid_3003:x:3003:"))
        assertTrue(File(linuxDir, "etc/group").readText().contains("android_gid_9997:x:9997:"))
        assertTrue(File(linuxDir, "tmp").canWrite())
        assertTrue(File(linuxDir, "var/tmp").canWrite())
        assertTrue(File(linuxDir, "root").isDirectory)
        assertTrue(File(linuxDir, "bin").exists())
        assertTrue(File(linuxDir, "bin/sh").exists())
        assertTrue(File(linuxDir, "sbin").exists())
        assertTrue(File(linuxDir, "lib").exists())
    }

    private fun tarGz(vararg entries: TarTestEntry): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            entries.forEach { entry ->
                gzip.write(tarHeader(entry))
                if (entry.content.isNotEmpty()) {
                    gzip.write(entry.content)
                    gzip.write(ByteArray(entry.content.size.paddingSize()))
                }
            }
            gzip.write(ByteArray(1024))
        }
        return output.toByteArray()
    }

    private fun tarHeader(entry: TarTestEntry): ByteArray {
        val header = ByteArray(512)
        header.writeString(0, 100, entry.name)
        header.writeOctal(100, 8, entry.mode.toLong())
        header.writeOctal(108, 8, 0)
        header.writeOctal(116, 8, 0)
        header.writeOctal(124, 12, entry.content.size.toLong())
        header.writeOctal(136, 12, 0)
        header.fill(' '.code.toByte(), 148, 156)
        header[156] = entry.type.code.toByte()
        header.writeString(157, 100, entry.linkName)
        header.writeString(257, 6, "ustar")
        header.writeString(263, 2, "00")
        val checksum = header.sumOf { it.toUByte().toInt() }
        header.writeOctal(148, 8, checksum.toLong())
        return header
    }

    private fun elfHeader(machine: Int): ByteArray =
        ByteArray(20).also { header ->
            header[0] = 0x7f.toByte()
            header[1] = 'E'.code.toByte()
            header[2] = 'L'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = 2
            header[5] = 1
            header[18] = (machine and 0xff).toByte()
            header[19] = ((machine shr 8) and 0xff).toByte()
        }

    private fun ByteArray.writeString(offset: Int, length: Int, value: String) {
        val bytes = value.toByteArray()
        bytes.copyInto(this, offset, 0, minOf(bytes.size, length))
    }

    private fun ByteArray.writeOctal(offset: Int, length: Int, value: Long) {
        val text = value.toString(8).padStart(length - 1, '0')
        writeString(offset, length, text)
        this[offset + length - 1] = 0
    }

    private fun Int.paddingSize(): Int = (512 - (this % 512)).let {
        if (it == 512) 0 else it
    }

    private class SingleResponseHttpServer(
        private val body: ByteArray,
    ) : AutoCloseable {
        private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int = serverSocket.localPort
        private val thread = Thread(::serveOnce, "workspace-test-http").apply {
            isDaemon = true
            start()
        }

        private fun serveOnce() {
            runCatching {
                serverSocket.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) {
                        // Drain request headers before sending the response.
                    }
                    socket.getOutputStream().use { output ->
                        output.write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/gzip\r\n" +
                                    "Content-Length: ${body.size}\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                                ).toByteArray()
                        )
                        output.write(body)
                    }
                }
            }
        }

        override fun close() {
            serverSocket.close()
            thread.join(1000)
        }
    }

    private class TestShellRunner : WorkspaceShellRunner {
        override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
            return when {
                context.command == "printf hello > command.txt && cat command.txt" -> {
                    File(context.workingDir, "command.txt").writeText("hello")
                    WorkspaceCommandResult(exitCode = 0, stdout = "hello", stderr = "")
                }

                context.command == "cat > stdin.txt" -> {
                    File(context.workingDir, "stdin.txt").writeBytes(context.stdin ?: ByteArray(0))
                    WorkspaceCommandResult(exitCode = 0, stdout = "", stderr = "")
                }

                context.command.startsWith("awk 'BEGIN") -> {
                    WorkspaceCommandResult(
                        exitCode = 0,
                        stdout = "a".repeat(MAX_OUTPUT_CHARS),
                        stderr = "",
                        truncated = true,
                    )
                }

                else -> WorkspaceCommandResult(
                    exitCode = 127,
                    stdout = "",
                    stderr = "Unsupported test command: ${context.command}",
                )
            }
        }
    }

    private data class TarTestEntry(
        val name: String,
        val content: ByteArray = byteArrayOf(),
        val mode: Int = 420,
        val type: Char = '0',
        val linkName: String = "",
    )
}
