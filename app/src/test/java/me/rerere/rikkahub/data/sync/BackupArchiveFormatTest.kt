package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupArchiveFormatTest {
    @Test
    fun manifestRoundTripPreservesPortableScope() {
        val manifest = BackupManifest(
            includesDatabase = true,
            includesFiles = true,
            managedFileDirs = BackupArchiveFormat.MANAGED_FILE_DIRS,
            sharedPrefsStores = BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES,
        )

        val decoded = JsonInstant.decodeFromString<BackupManifest>(
            JsonInstant.encodeToString(manifest),
        )

        assertEquals(BackupArchiveFormat.CURRENT_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(BackupArchiveFormat.MANAGED_FILE_DIRS, decoded.managedFileDirs)
        assertEquals(BackupArchiveFormat.PORTABLE_SHARED_PREF_STORES, decoded.sharedPrefsStores)
    }

    @Test
    fun sharedPreferencesSnapshotRoundTripPreservesTypedValues() {
        val snapshot = buildSharedPreferencesSnapshot(
            storeName = "rikkahub.preferences",
            entries = linkedMapOf(
                "string" to "value",
                "bool" to true,
                "int" to 4,
                "long" to 5L,
                "float" to 1.5f,
                "set" to setOf("b", "a"),
            ),
        )

        val decoded = JsonInstant.decodeFromString<SharedPreferencesSnapshot>(
            JsonInstant.encodeToString(snapshot),
        )

        assertEquals("rikkahub.preferences", decoded.name)
        assertEquals(6, decoded.entries.size)
        assertEquals(
            listOf(
                SharedPreferencesSnapshotTypes.BOOLEAN,
                SharedPreferencesSnapshotTypes.FLOAT,
                SharedPreferencesSnapshotTypes.INT,
                SharedPreferencesSnapshotTypes.LONG,
                SharedPreferencesSnapshotTypes.STRING_SET,
                SharedPreferencesSnapshotTypes.STRING,
            ).sorted(),
            decoded.entries.map { it.type }.sorted(),
        )
    }

    @Test
    fun enumerateDirectoryEntriesIncludesNestedFilesAndEmptyDirectories() {
        val tempDir = createTempDir(prefix = "backup-entries-")
        try {
            val root = File(tempDir, "workspaces").apply { mkdirs() }
            File(root, "conversation-1").mkdirs()
            File(root, "conversation-1/output.txt").writeText("hello")
            File(root, "conversation-2/empty").mkdirs()

            val entries = enumerateDirectoryEntries(root, "workspaces").map { it.entryName }

            assertTrue(entries.contains("workspaces/"))
            assertTrue(entries.contains("workspaces/conversation-1/"))
            assertTrue(entries.contains("workspaces/conversation-1/output.txt"))
            assertTrue(entries.contains("workspaces/conversation-2/"))
            assertTrue(entries.contains("workspaces/conversation-2/empty/"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun safeZipDestinationRejectsTraversal() {
        val tempDir = createTempDir(prefix = "backup-path-")
        try {
            safeZipDestination(tempDir, "../outside.txt")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
