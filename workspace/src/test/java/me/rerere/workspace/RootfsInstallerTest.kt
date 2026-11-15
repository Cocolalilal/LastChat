package me.rerere.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream

class RootfsInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `extract skips OTHER entry data exactly once`() {
        // OTHER 条目 (如 GNU sparse) 带 size>0 数据区, 双重 skip 会让后续 header 错位
        val archive = tmp.newFile("rootfs.tar.gz")
        GZIPOutputStream(archive.outputStream()).use { out ->
            out.writeTarEntry("a.txt", '0', "hello".toByteArray())
            out.writeTarEntry("sparse.bin", 'S', ByteArray(700) { 1 })
            out.writeTarEntry("b.txt", '0', "world".toByteArray())
            out.write(ByteArray(TAR_BLOCK * 2))
        }

        val target = tmp.newFolder("out")
        createInstaller().extractTar(archive, target) {}

        assertEquals("hello", File(target, "a.txt").readText())
        assertEquals("world", File(target, "b.txt").readText())
        assertFalse(File(target, "sparse.bin").exists())
    }

    @Test
    fun `extract handles directories and zero size entries`() {
        val archive = tmp.newFile("rootfs.tar.gz")
        GZIPOutputStream(archive.outputStream()).use { out ->
            out.writeTarEntry("dir/", '5', ByteArray(0))
            out.writeTarEntry("dir/file.txt", '0', "content".toByteArray())
            out.write(ByteArray(TAR_BLOCK * 2))
        }

        val target = tmp.newFolder("out")
        createInstaller().extractTar(archive, target) {}

        assertEquals(true, File(target, "dir").isDirectory)
        assertEquals("content", File(target, "dir/file.txt").readText())
    }

    @Test
    fun `extract skips Alpine root directory entries and preserves following files`() {
        val archive = tmp.newFile("alpine-armv7.tar.gz")
        GZIPOutputStream(archive.outputStream()).use { out ->
            out.writeTarEntry(".", '5', ByteArray(0))
            out.writeTarEntry("./", '5', ByteArray(700))
            out.writeTarEntry("./bin/", '5', ByteArray(0))
            out.writeTarEntry("./bin/busybox", '0', "armhf executable".toByteArray())
            out.write(ByteArray(TAR_BLOCK * 2))
        }
        val target = tmp.newFolder("alpine")
        createInstaller().extractTar(archive, target) {}
        assertEquals("armhf executable", File(target, "bin/busybox").readText())
    }

    @Test
    fun `GNU overridden root directory is skipped before path normalization`() {
        val archive = tmp.newFile("gnu.tar.gz")
        GZIPOutputStream(archive.outputStream()).use { out ->
            out.writeTarEntry("././@LongLink", 'L', "./\u0000".toByteArray())
            out.writeTarEntry("placeholder", '5', ByteArray(0))
            out.writeTarEntry("etc/os-release", '0', "Alpine".toByteArray())
            out.write(ByteArray(TAR_BLOCK * 2))
        }
        val target = tmp.newFolder("gnu")
        createInstaller().extractTar(archive, target) {}
        assertEquals("Alpine", File(target, "etc/os-release").readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `root entry exception does not permit traversal`() {
        val archive = tmp.newFile("unsafe.tar.gz")
        GZIPOutputStream(archive.outputStream()).use { out ->
            out.writeTarEntry("./", '5', ByteArray(0))
            out.writeTarEntry("./../escape", '0', "unsafe".toByteArray())
            out.write(ByteArray(TAR_BLOCK * 2))
        }
        createInstaller().extractTar(archive, tmp.newFolder("safe")) {}
    }

    private fun createInstaller() = RootfsInstaller(WorkspaceManager(tmp.newFolder()))

    private fun OutputStream.writeTarEntry(name: String, type: Char, data: ByteArray) {
        val header = ByteArray(TAR_BLOCK)
        name.toByteArray(Charsets.UTF_8).copyInto(header, 0)
        "0000755".toByteArray().copyInto(header, 100)
        data.size.toLong().toOctalField().copyInto(header, 124)
        header[156] = type.code.toByte()
        write(header)
        write(data)
        val padding = (TAR_BLOCK - data.size % TAR_BLOCK) % TAR_BLOCK
        write(ByteArray(padding))
    }

    private fun Long.toOctalField(): ByteArray =
        toString(8).padStart(11, '0').toByteArray(Charsets.UTF_8)

    companion object {
        private const val TAR_BLOCK = 512
    }
}
