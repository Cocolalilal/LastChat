package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ManagedFileRestoreTest {
    @Test
    fun resolveSkipsManifestDirsThatWereNotStaged() {
        val temp = Files.createTempDirectory("restore-resolve-").toFile()
        try {
            val staged = File(temp, "staged").apply { mkdirs() }
            File(staged, "upload").mkdirs()
            File(staged, "upload/keep.txt").writeText("keep")

            val manifest = BackupManifest(
                includesFiles = true,
                managedFileDirs = listOf("upload", "avatars", "chat_files"),
            )

            val dirs = resolveManagedDirsToRestore(
                manifest = manifest,
                stagedManagedDirs = setOf("upload"),
                stagedFilesDir = staged,
            )

            assertEquals(listOf("upload"), dirs)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun restoreDoesNotWipeLiveDirWhenStagedCopyIsMissing() {
        val temp = Files.createTempDirectory("restore-skip-").toFile()
        try {
            val live = File(temp, "live").apply { mkdirs() }
            val staged = File(temp, "staged").apply { mkdirs() }
            val bak = File(temp, "bak")

            File(live, "avatars").mkdirs()
            File(live, "avatars/keep.png").writeText("live-avatar")

            restoreManagedFileDirectories(
                liveFilesDir = live,
                stagedFilesDir = staged,
                directoriesToRestore = emptyList(),
                liveBackupDir = bak,
            )

            assertTrue(File(live, "avatars/keep.png").exists())
            assertEquals("live-avatar", File(live, "avatars/keep.png").readText())
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun restoreReplacesLiveDirFromStagedCopy() {
        val temp = Files.createTempDirectory("restore-swap-").toFile()
        try {
            val live = File(temp, "live").apply { mkdirs() }
            val staged = File(temp, "staged").apply { mkdirs() }
            val bak = File(temp, "bak")

            File(live, "upload").mkdirs()
            File(live, "upload/old.txt").writeText("old")
            File(staged, "upload").mkdirs()
            File(staged, "upload/new.txt").writeText("new")

            restoreManagedFileDirectories(
                liveFilesDir = live,
                stagedFilesDir = staged,
                directoriesToRestore = listOf("upload"),
                liveBackupDir = bak,
            )

            assertTrue(File(live, "upload/new.txt").exists())
            assertEquals("new", File(live, "upload/new.txt").readText())
            assertFalse(File(live, "upload/old.txt").exists())
            assertFalse("Live backup must be removed after success", bak.exists())
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun restoreEmptyStagedDirWipesLiveContents() {
        val temp = Files.createTempDirectory("restore-empty-").toFile()
        try {
            val live = File(temp, "live").apply { mkdirs() }
            val staged = File(temp, "staged").apply { mkdirs() }
            val bak = File(temp, "bak")

            File(live, "upload").mkdirs()
            File(live, "upload/old.txt").writeText("old")
            File(staged, "upload").mkdirs()

            restoreManagedFileDirectories(
                liveFilesDir = live,
                stagedFilesDir = staged,
                directoriesToRestore = listOf("upload"),
                liveBackupDir = bak,
            )

            assertTrue(File(live, "upload").isDirectory)
            assertFalse(File(live, "upload/old.txt").exists())
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun restoreWorkspacesLeavesLinuxRootfsInPlace() {
        val temp = Files.createTempDirectory("restore-ws-").toFile()
        try {
            val live = File(temp, "workspaces").apply { mkdirs() }
            val staged = File(temp, "staged-workspaces").apply { mkdirs() }
            val workspaceId = "ws-1"

            File(live, "$workspaceId/linux/bin").mkdirs()
            File(live, "$workspaceId/linux/bin/sh").writeText("rootfs")
            File(live, "$workspaceId/files").mkdirs()
            File(live, "$workspaceId/files/old.txt").writeText("old")
            File(staged, "$workspaceId/files").mkdirs()
            File(staged, "$workspaceId/files/new.txt").writeText("new")

            restoreWorkspacesDir(staged, live)

            assertEquals("rootfs", File(live, "$workspaceId/linux/bin/sh").readText())
            assertEquals("new", File(live, "$workspaceId/files/new.txt").readText())
            assertFalse(File(live, "$workspaceId/files/old.txt").exists())
        } finally {
            temp.deleteRecursively()
        }
    }
}
