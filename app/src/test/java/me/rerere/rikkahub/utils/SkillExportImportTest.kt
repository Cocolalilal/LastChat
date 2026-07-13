package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkillExportImportTest {
    @Test
    fun importsFullFrontmatterWithoutFlatteningIt() {
        val result = SkillExportImport.importFromString(
            """
            ---
            name: document-review
            description: Review documents when a structured review is requested.
            license: Apache-2.0
            compatibility: Requires Python 3.
            allowed-tools: Bash(python3:*) Read
            metadata:
              author: LastChat
              version: "1.2"
            ---
            Read `references/rubric.md` only for formal reviews.
            """.trimIndent()
        )

        val success = result as SkillExportImport.ImportResult.Success
        assertEquals("document-review", success.skill.name)
        assertEquals("Apache-2.0", success.skill.license)
        assertEquals("Requires Python 3.", success.skill.compatibility)
        assertEquals("Bash(python3:*) Read", success.skill.allowedTools)
        assertEquals("LastChat", success.skill.metadata["author"])
        assertEquals("1.2", success.skill.metadata["version"])
    }

    @Test
    fun importsPackageResourcesRelativeToSkillRoot() {
        val archive = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                fun entry(path: String, text: String) {
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
                entry("document-review/SKILL.md", "---\nname: document-review\ndescription: Review docs.\n---\nRead the rubric.")
                entry("document-review/references/rubric.md", "# Rubric")
                entry("document-review/scripts/check.py", "print('ok')")
            }
            output.toByteArray()
        }

        val success = SkillExportImport.importFromBytes(archive) as SkillExportImport.ImportResult.Success
        assertEquals("skill_package", success.format)
        assertEquals("Read the rubric.", success.skill.instructions)
        assertTrue(success.packageFiles.containsKey("SKILL.md"))
        assertTrue(success.packageFiles.containsKey("references/rubric.md"))
        assertTrue(success.packageFiles.containsKey("scripts/check.py"))
    }

    @Test
    fun rejectsArchiveTraversal() {
        val archive = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../SKILL.md"))
                zip.write("---\nname: bad\ndescription: bad\n---".toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }

        assertTrue(SkillExportImport.importFromBytes(archive) is SkillExportImport.ImportResult.Error)
    }

    @Test
    fun packageFileResolutionStaysInsideSkillRoot() {
        val root = Files.createTempDirectory("lastchat-skill").toFile()
        try {
            assertEquals(
                root.resolve("references/guide.md").canonicalFile,
                SkillExportImport.resolvePackageFile(root, "references/guide.md"),
            )
            assertEquals(null, SkillExportImport.resolvePackageFile(root, "../secret.txt"))
            assertEquals(null, SkillExportImport.resolvePackageFile(root, "/absolute.txt"))
            assertEquals(null, SkillExportImport.resolvePackageFile(root, "references//guide.md"))
        } finally {
            root.deleteRecursively()
        }
    }
}
