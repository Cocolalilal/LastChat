package me.rerere.rikkahub.data.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import me.rerere.document.PortableZip

class PortableSkillPackageTest {
    @Test
    fun importsFullFrontmatterWithoutFlatteningIt() {
        val result = PortableSkillPackage.importFromString(
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
        val success = assertIs<PortableSkillPackage.ImportResult.Success>(result)
        assertEquals("document-review", success.parsed.name)
        assertEquals("Apache-2.0", success.parsed.license)
        assertEquals("Requires Python 3.", success.parsed.compatibility)
        assertEquals("Bash(python3:*) Read", success.parsed.allowedTools)
        assertEquals("LastChat", success.parsed.metadata["author"])
        assertEquals("1.2", success.parsed.metadata["version"])
        assertTrue(success.parsed.instructions.contains("references/rubric.md"))
    }

    @Test
    fun importsPackageResourcesRelativeToSkillRoot() {
        val skillMd = "---\nname: document-review\ndescription: Review docs.\n---\nRead the rubric."
        val archive = PortableZip.writeStoreArchive(
            mapOf(
                "document-review/SKILL.md" to skillMd.encodeToByteArray(),
                "document-review/references/rubric.md" to "# Rubric".encodeToByteArray(),
                "document-review/scripts/check.py" to "print('ok')".encodeToByteArray(),
            )
        )
        val success = assertIs<PortableSkillPackage.ImportResult.Success>(
            PortableSkillPackage.importFromBytes(archive),
        )
        assertEquals("skill_package", success.format)
        assertEquals("Read the rubric.", success.parsed.instructions)
        assertTrue(success.packageFiles.containsKey("SKILL.md"))
        assertTrue(success.packageFiles.containsKey("references/rubric.md"))
        assertTrue(success.packageFiles.containsKey("scripts/check.py"))
        val portable = success.toPortableSkill(id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        assertEquals("skill-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", portable.packageRoot)
        assertEquals("/skills/skill-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", portable.workspaceDirectory)
        assertTrue(portable.bundledResources.any { it.endsWith("references/rubric.md") })
    }

    @Test
    fun rejectsArchiveTraversal() {
        val archive = PortableZip.writeStoreArchive(
            mapOf("../SKILL.md" to "---\nname: bad\ndescription: bad\n---".encodeToByteArray()),
        )
        assertIs<PortableSkillPackage.ImportResult.Error>(PortableSkillPackage.importFromBytes(archive))
    }

    @Test
    fun importFromBytesAcceptsPlainSkillMd() {
        val md = PortableSkillPackage.exportToSkillMd(
            name = "plain",
            description = "A markdown skill",
            instructions = "Do the thing.",
        )
        val success = assertIs<PortableSkillPackage.ImportResult.Success>(
            PortableSkillPackage.importFromBytes(md.encodeToByteArray()),
        )
        assertEquals("skill_md", success.format)
        assertEquals("plain", success.parsed.name)
        assertEquals("Do the thing.", success.parsed.instructions)
    }

    @Test
    fun exportZipRoundTripsSkillMdAndAssets() {
        val files = mapOf(
            "SKILL.md" to PortableSkillPackage.exportToSkillMd(
                name = "pack",
                description = "A package",
                instructions = "Do the thing.",
                license = "MIT",
            ).encodeToByteArray(),
            "assets/note.txt" to "hello".encodeToByteArray(),
        )
        val zip = PortableSkillPackage.exportZip("pack", files)
        val imported = assertIs<PortableSkillPackage.ImportResult.Success>(
            PortableSkillPackage.importFromBytes(zip),
        )
        assertEquals("pack", imported.parsed.name)
        assertEquals("MIT", imported.parsed.license)
        assertEquals("hello", imported.packageFiles.getValue("assets/note.txt").decodeToString())
    }
}
