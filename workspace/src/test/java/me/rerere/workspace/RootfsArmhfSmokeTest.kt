package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in integration check against the actual Alpine ARMv7 archive, without network in tests. */
class RootfsArmhfSmokeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `extract Alpine armhf rootfs and execute its dynamic busybox binary`() {
        val archivePath = System.getenv("LASTCHAT_ARMHF_ARCHIVE")
        val qemu = System.getenv("LASTCHAT_QEMU_ARM")
        assumeTrue("Set LASTCHAT_ARMHF_ARCHIVE and LASTCHAT_QEMU_ARM to run the ARMHF smoke test", archivePath != null && qemu != null)
        val root = tmp.newFolder("rootfs")
        RootfsInstaller(WorkspaceManager(tmp.newFolder("workspaces"))).extractTar(File(requireNotNull(archivePath)), root) {}
        val binary = File(root, "bin/busybox")
        val header = binary.inputStream().use { it.readNBytes(20) }
        assertEquals(1, header[4].toInt()) // ELFCLASS32
        assertEquals(40, header[18].toInt()) // EM_ARM
        assertTrue(binary.canExecute())
        val process = ProcessBuilder(requireNotNull(qemu), "-L", root.absolutePath, binary.absolutePath,
            "sh", "-c", "printf 'armhf-rootfs-ok\\n'").redirectErrorStream(true).start()
        try {
            assertTrue("ARMHF binary timed out", process.waitFor(20, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertEquals("armhf-rootfs-ok", output.trim())
        } finally {
            process.destroyForcibly()
        }
    }
}
