package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.rikkahub.service.SpontaneousMessaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpontaneousScheduleSelectionTest {
    @Test
    fun sameDayScheduleRoundTripsThroughSelection() {
        val selection = spontaneousScheduleSelectionFromHours(
            startHour = 9,
            endHour = 18,
        )

        assertEquals(9, selection.absoluteStart)
        assertEquals(18, selection.absoluteEnd)
        assertEquals(9 to 18, spontaneousScheduleSelectionToHours(selection))
    }

    @Test
    fun overnightScheduleRoundTripsThroughSelection() {
        val selection = spontaneousScheduleSelectionFromHours(
            startHour = 22,
            endHour = 6,
        )

        assertEquals(22, selection.absoluteStart)
        assertEquals(30, selection.absoluteEnd)
        assertEquals(22 to 6, spontaneousScheduleSelectionToHours(selection))
    }

    @Test
    fun allDayScheduleRoundTripsThroughSelection() {
        val selection = spontaneousScheduleSelectionFromHours(
            startHour = 8,
            endHour = 8,
        )

        assertEquals(8, selection.absoluteStart)
        assertEquals(32, selection.absoluteEnd)
        assertTrue(selection.isAllDay)
        assertEquals(8 to 8, spontaneousScheduleSelectionToHours(selection))
    }

    @Test
    fun scheduleSelectionClampsToAtMostTwentyFourHours() {
        val selection = normalizeSpontaneousScheduleSelection(
            rawStart = 5,
            rawEnd = 40,
        )

        assertEquals(5, selection.absoluteStart)
        assertEquals(29, selection.absoluteEnd)
        assertEquals(24, selection.spanHours)
        assertEquals(5 to 5, spontaneousScheduleSelectionToHours(selection))
    }

    @Test
    fun scheduleSelectionKeepsAtLeastOneHourSpan() {
        val selection = normalizeSpontaneousScheduleSelection(
            rawStart = 12,
            rawEnd = 12,
        )

        assertEquals(12, selection.absoluteStart)
        assertEquals(13, selection.absoluteEnd)
        assertEquals(1, selection.spanHours)
        assertEquals(12 to 13, spontaneousScheduleSelectionToHours(selection))
    }

    @Test
    fun mappedHoursStillFollowExistingWraparoundLogic() {
        val (startHour, endHour) = spontaneousScheduleSelectionToHours(
            spontaneousScheduleSelectionFromHours(
                startHour = 22,
                endHour = 6,
            )
        )

        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 23,
                startHour = startHour,
                endHour = endHour,
            )
        )
        assertTrue(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 3,
                startHour = startHour,
                endHour = endHour,
            )
        )
        assertFalse(
            SpontaneousMessaging.isWithinActiveHours(
                currentHour = 12,
                startHour = startHour,
                endHour = endHour,
            )
        )
    }
}
