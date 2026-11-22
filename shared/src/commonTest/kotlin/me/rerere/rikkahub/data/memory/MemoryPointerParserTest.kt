package me.rerere.rikkahub.data.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryPointerParserTest {

    @Test
    fun parseSegments_tokenizesTextAndPointers() {
        val input = "Prefers dry humor [#182, #240]."
        val segments = MemoryPointerParser.parseSegments(input)
        assertEquals(4, segments.size)
        assertEquals(PointerSegment.Text("Prefers dry humor "), segments[0])
        assertEquals(PointerSegment.Pointer(182, "[#182]"), segments[1])
        assertEquals(PointerSegment.Pointer(240, "[#240]"), segments[2])
        assertEquals(PointerSegment.Text("."), segments[3])
    }

    @Test
    fun parseSegments_handlesDriftFormat() {
        val input = "Builds mobile-only [#79, 85]."
        val segments = MemoryPointerParser.parseSegments(input)
        assertEquals(4, segments.size)
        assertEquals(PointerSegment.Text("Builds mobile-only "), segments[0])
        assertEquals(PointerSegment.Pointer(79, "[#79]"), segments[1])
        assertEquals(PointerSegment.Pointer(85, "[#85]"), segments[2])
        assertEquals(PointerSegment.Text("."), segments[3])
    }

    @Test
    fun extractIds_extractsOrderedDistinctIds() {
        val input = "Fact A [#79], Fact B [#182, #240], Fact C [#79]."
        val ids = MemoryPointerParser.extractIds(input)
        assertEquals(listOf(79, 182, 240), ids)
    }

    @Test
    fun stripPointers_removesPointersAndCleansPunctuation() {
        val input = "Prefers dry humor [#182, #240]."
        val stripped = MemoryPointerParser.stripPointers(input)
        assertEquals("Prefers dry humor.", stripped)

        val inputTrailing = "Lenovo Tab M9 [#79]  "
        assertEquals("Lenovo Tab M9", MemoryPointerParser.stripPointers(inputTrailing))
    }

    @Test
    fun removePointerId_surgicallyRemovesSingleId() {
        val multi = "Loyalty is paramount [#182, #240]."
        val afterMulti = MemoryPointerParser.removePointerId(multi, 182)
        assertEquals("Loyalty is paramount [#240].", afterMulti.trim())

        val single = "Uses tablet [#79]."
        val afterSingle = MemoryPointerParser.removePointerId(single, 79)
        assertEquals("Uses tablet.", afterSingle.trim())
    }

    @Test
    fun resolveIdsForSelection_handlesDirectAndEnclosingLine() {
        val section = """
            - Uses Lenovo Tab M9 tablet for development [#79]
            - Relational history is defined by loyalty [#182, #240]
        """.trimIndent()

        // 1. Direct highlight of chip
        val direct = MemoryPointerParser.resolveIdsForSelection("[#79]", section)
        assertEquals(listOf(79), direct)

        // 2. Highlight text preceding chip without the chip
        val preceding = MemoryPointerParser.resolveIdsForSelection("Lenovo Tab M9", section)
        assertEquals(listOf(79), preceding)

        // 3. Highlight words in line 2
        val line2 = MemoryPointerParser.resolveIdsForSelection("defined by loyalty", section)
        assertEquals(listOf(182, 240), line2)

        // 4. Highlight text too short
        val short = MemoryPointerParser.resolveIdsForSelection("ab", section)
        assertTrue(short.isEmpty())
    }

    @Test
    fun formatForIngestion_formatsExpectedPattern() {
        assertEquals(
            "[#79] (2026-11-20) Lenovo Tab M9 tablet",
            MemoryPointerParser.formatForIngestion(79, "Lenovo Tab M9 tablet", "2026-11-20")
        )
        assertEquals(
            "[#80] Lenovo Tab M9 tablet",
            MemoryPointerParser.formatForIngestion(80, "Lenovo Tab M9\ntablet", null)
        )
    }

    @Test
    fun hydrateProvenance_updatesIdsAndIcons() {
        val raw = MemorySummaryData(
            sections = listOf(
                MemorySummarySection(icon = "invalid_icon", title = "My Hardware", content = "Tablet setup [#79]")
            )
        )
        val hydrated = MemoryPointerParser.hydrateProvenance(raw)
        val sec = hydrated.sections.first()
        assertEquals("my_hardware", sec.id)
        assertEquals("star", sec.icon) // Invalid icon replaced with default star
        assertEquals(listOf(79), sec.citedMemoryIds)
    }

    @Test
    fun markdownCodec_roundtripsCorrectly() {
        val initial = MemorySummaryData(
            title = "Summary Title",
            subtitle = "Subtitle",
            sections = listOf(
                MemorySummarySection(
                    id = "devices",
                    icon = "devices",
                    title = "Devices",
                    content = "- Lenovo Tab M9 [#79]",
                    citedMemoryIds = listOf(79),
                ),
                MemorySummarySection(
                    id = "interests",
                    icon = "heart",
                    title = "Interests",
                    content = "- One Piece and Dark [#182]",
                    citedMemoryIds = listOf(182),
                )
            )
        )

        val md = MemorySummaryMarkdownCodec.encodeSectionsToMarkdown(initial)
        val fullDoc = MemorySummaryMarkdownCodec.encodeFullDocument(initial)
        assertTrue(fullDoc.startsWith("# Summary Title\nSubtitle\n\n## Devices"))

        val decoded = MemorySummaryMarkdownCodec.decodeSectionsFromMarkdown(md, base = initial)
        assertEquals(2, decoded.sections.size)
        assertEquals("Devices", decoded.sections[0].title)
        assertEquals("devices", decoded.sections[0].icon)
        assertEquals(listOf(79), decoded.sections[0].citedMemoryIds)
        assertEquals("Interests", decoded.sections[1].title)
        assertEquals("heart", decoded.sections[1].icon)
        assertEquals(listOf(182), decoded.sections[1].citedMemoryIds)
    }
}
