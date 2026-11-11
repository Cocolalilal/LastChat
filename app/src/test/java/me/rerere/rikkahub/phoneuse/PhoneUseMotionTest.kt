package me.rerere.rikkahub.phoneuse

import org.junit.Assert.*
import org.junit.Test

class PhoneUseMotionTest {
    private val bounds = phoneOverlayBounds(1080, 2200, 360, 112, 16f)

    @Test fun `quiet releases dock to the nearest side without moving to a corner`() {
        val right = phoneOverlaySnap(100f, 1000f, 0f, 0f, bounds, 48f)
        assertEquals(0f, right.x, 0.01f)
        assertEquals(1000f, right.y, 0.01f)
        assertNull(right.vertical)
        val left = phoneOverlaySnap(600f, 1000f, 0f, 0f, bounds, 48f)
        assertEquals(bounds.maxX, left.x, 0.01f)
        assertEquals(PhoneOverlaySide.LEFT, left.side)
    }

    @Test fun `a strong diagonal throw reaches its intended corner`() {
        val topLeft = phoneOverlaySnap(300f, 1000f, 4000f, 4000f, bounds, 48f)
        assertEquals(bounds.maxX, topLeft.x, 0.01f)
        assertEquals(bounds.maxY, topLeft.y, 0.01f)
        assertEquals(PhoneOverlayVerticalEdge.TOP, topLeft.vertical)
        val bottomRight = phoneOverlaySnap(400f, 1000f, -4000f, -4000f, bounds, 48f)
        assertEquals(0f, bottomRight.x, 0.01f)
        assertEquals(bounds.minY, bottomRight.y, 0.01f)
        assertEquals(PhoneOverlayVerticalEdge.BOTTOM, bottomRight.vertical)
    }

    @Test fun `edge magnets preserve breathing room above navigation and below status bars`() {
        assertEquals(16f, bounds.minY, 0.01f)
        assertEquals(2072f, bounds.maxY, 0.01f)
        assertEquals(bounds.minY, phoneOverlaySnap(50f, 48f, 0f, 0f, bounds, 48f).y, 0.01f)
        assertEquals(bounds.maxY, phoneOverlaySnap(50f, 2040f, 0f, 0f, bounds, 48f).y, 0.01f)
    }

    @Test fun `full width assistant bars move vertically and retain side bounds`() {
        val full = phoneOverlayBounds(1080, 2200, 1080, 112, 16f)
        val target = phoneOverlaySnap(0f, 1000f, 5000f, 500f, full, 48f)
        assertEquals(0f, target.x, 0.01f)
        assertEquals(1140f, target.y, 0.01f)
        assertNull(target.vertical)
    }

    @Test fun `rotation and oversize replies always produce valid bounds`() {
        val small = phoneOverlayBounds(320, 480, 360, 640, 16f)
        val target = phoneOverlaySnap(800f, 2000f, 4000f, -4000f, small, 48f)
        assertEquals(0f, target.x, 0.01f)
        assertEquals(0f, target.y, 0.01f)
        assertEquals(0f, small.clampY(1000f), 0.01f)
        val landscape = phoneOverlayBounds(2200, 1080, 360, 640, 16f)
        assertEquals(424f, landscape.clampY(2000f), 0.01f)
    }

    @Test fun `invalid velocity samples cannot poison a spring target`() {
        val target = phoneOverlaySnap(100f, 1000f, Float.NaN, Float.NaN, bounds, 48f)
        assertEquals(0f, target.x, 0.01f)
        assertEquals(1000f, target.y, 0.01f)
    }
    @Test fun `assistant handoff position matches activity side and bottom insets`() {
        val frame = phoneOverlayBounds(1233, 2400, 1137, 168, 72f)
        val dock = assistantDockAnchor(frame, 3f)
        assertEquals(48f, dock.x, 0.01f)
        assertEquals(72f, dock.y, 0.01f)
        assertEquals(PhoneOverlayVerticalEdge.BOTTOM, dock.vertical)
    }

}
