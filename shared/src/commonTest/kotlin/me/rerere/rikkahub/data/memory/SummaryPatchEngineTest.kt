package me.rerere.rikkahub.data.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummaryPatchEngineTest {

    @Test
    fun userExplicitlyRequestedFullRewrite_detectsIntent() {
        assertTrue(SummaryPatchEngine.userExplicitlyRequestedFullRewrite("Please rewrite the entire summary in Spanish."))
        assertTrue(SummaryPatchEngine.userExplicitlyRequestedFullRewrite("Rebuild from scratch with new categories."))
        assertTrue(SummaryPatchEngine.userExplicitlyRequestedFullRewrite("Start over completely."))
        assertFalse(SummaryPatchEngine.userExplicitlyRequestedFullRewrite("Update my devices section to include Tab M9."))
        assertFalse(SummaryPatchEngine.userExplicitlyRequestedFullRewrite("What do you remember about my humor?"))
    }

    @Test
    fun applyPatch_handlesNoOp() {
        val current = MemorySummaryData(
            sections = listOf(MemorySummarySection(icon = "star", title = "Preferences", content = "Likes tea [#10]"))
        )
        val patch = MemorySectionPatch(
            action = "no_op",
            assistantReply = "Your preferences note that you like tea.",
        )
        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        assertEquals(current, result.updated)
        assertTrue(result.modifiedSectionKeys.isEmpty())
    }

    @Test
    fun applyPatch_handlesLineEdits() {
        val current = MemorySummaryData(
            sections = listOf(
                MemorySummarySection(
                    icon = "devices",
                    title = "Devices",
                    content = "- Lenovo Tab M9 [#79]\n- Old phone [#80]\n- Bluetooth keyboard [#81]",
                )
            )
        )

        val patch = MemorySectionPatch(
            action = "update_section",
            targetSection = "Devices",
            lineEdits = listOf(
                SectionLineEdit(op = "replace", find = "- Old phone [#80]", replace = "- Pixel 8 [#85]"),
                SectionLineEdit(op = "remove", find = "- Bluetooth keyboard [#81]"),
                SectionLineEdit(op = "append", replace = "- Stylus pen [#86]"),
            )
        )

        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        val updatedContent = result.updated.sections.first().content
        assertTrue(updatedContent.contains("- Pixel 8 [#85]"))
        assertFalse(updatedContent.contains("- Old phone"))
        assertFalse(updatedContent.contains("- Bluetooth keyboard"))
        assertTrue(updatedContent.contains("- Stylus pen [#86]"))
        assertTrue(result.modifiedSectionKeys.contains("devices"))
        assertEquals(listOf(79, 85, 86), result.updated.sections.first().citedMemoryIds)
    }

    @Test
    fun applyPatch_handlesAddSection_andMergesExisting() {
        val current = MemorySummaryData(
            sections = listOf(MemorySummarySection(icon = "star", title = "Preferences", content = "- Dry humor [#182]"))
        )

        // 1. Add brand new section
        val addPatch = MemorySectionPatch(
            action = "add_section",
            section = MemorySummarySection(icon = "code", title = "Tech Stack", content = "- Kotlin Multiplatform [#90]")
        )
        val result1 = SummaryPatchEngine.applyPatch(current, addPatch, allowFullRewrite = false)
        assertEquals(2, result1.updated.sections.size)
        assertEquals("Tech Stack", result1.updated.sections[1].title)

        // 2. Add section with existing title -> merges safely instead of duplicating
        val mergePatch = MemorySectionPatch(
            action = "add_section",
            section = MemorySummarySection(icon = "star", title = "Preferences", content = "- Dislikes corporate jargon [#240]")
        )
        val result2 = SummaryPatchEngine.applyPatch(result1.updated, mergePatch, allowFullRewrite = false)
        assertEquals(2, result2.updated.sections.size)
        val prefContent = result2.updated.sections[0].content
        assertTrue(prefContent.contains("- Dry humor [#182]"))
        assertTrue(prefContent.contains("- Dislikes corporate jargon [#240]"))
        assertEquals(listOf(182, 240), result2.updated.sections[0].citedMemoryIds)
    }

    @Test
    fun applyPatch_handlesDeleteSection() {
        val current = MemorySummaryData(
            sections = listOf(
                MemorySummarySection(icon = "star", title = "Preferences", content = "Likes tea"),
                MemorySummarySection(icon = "heart", title = "Hobbies", content = "Gaming"),
            )
        )
        val patch = MemorySectionPatch(action = "delete_section", targetSection = "Hobbies")
        val result = SummaryPatchEngine.applyPatch(current, patch, allowFullRewrite = false)
        assertEquals(1, result.updated.sections.size)
        assertEquals("Preferences", result.updated.sections[0].title)
    }

    @Test
    fun applyPatch_guardsAgainstUnauthorizedFullRewrite() {
        val current = MemorySummaryData(
            sections = listOf(
                MemorySummarySection(icon = "devices", title = "Devices", content = "Lenovo Tab M9 [#79]"),
                MemorySummarySection(icon = "heart", title = "Interests", content = "One Piece [#182]"),
            )
        )

        // Unauthorized model rewrite trying to discard Interests and only keep Devices
        val unauthorizedPatch = MemorySectionPatch(
            action = "full_rewrite",
            fullSummary = MemorySummaryData(
                sections = listOf(
                    MemorySummarySection(icon = "devices", title = "Devices", content = "Lenovo Tab M9 (Android 13) [#79]")
                )
            )
        )

        val result = SummaryPatchEngine.applyPatch(current, unauthorizedPatch, allowFullRewrite = false)
        assertTrue(result.rejectedFullRewrite)
        // Preserves Interests section instead of dropping it
        assertEquals(2, result.updated.sections.size)
        assertEquals("Lenovo Tab M9 (Android 13) [#79]", result.updated.sections.first { it.title == "Devices" }.content)
        assertNotNull(result.updated.sections.firstOrNull { it.title == "Interests" })
    }

    @Test
    fun applyPatch_allowsAuthorizedFullRewrite() {
        val current = MemorySummaryData(
            sections = listOf(
                MemorySummarySection(icon = "devices", title = "Devices", content = "Lenovo Tab M9 [#79]"),
                MemorySummarySection(icon = "heart", title = "Interests", content = "One Piece [#182]"),
            )
        )

        val authorizedPatch = MemorySectionPatch(
            action = "full_rewrite",
            fullSummary = MemorySummaryData(
                title = "Total Rewrite",
                sections = listOf(
                    MemorySummarySection(icon = "star", title = "Brand New Profile", content = "Fresh start [#999]")
                )
            )
        )

        val result = SummaryPatchEngine.applyPatch(current, authorizedPatch, allowFullRewrite = true)
        assertFalse(result.rejectedFullRewrite)
        assertEquals(1, result.updated.sections.size)
        assertEquals("Brand New Profile", result.updated.sections[0].title)
        assertEquals(listOf(999), result.updated.sections[0].citedMemoryIds)
    }
}
