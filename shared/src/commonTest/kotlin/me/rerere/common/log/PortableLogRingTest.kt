package me.rerere.common.log

import kotlin.test.Test
import kotlin.test.assertEquals

class PortableLogRingTest {
    @Test
    fun addDropsOldestWhenFull() {
        val ring = PortableLogRing<Int>(maxEntries = 3)
        ring.add(1)
        ring.add(2)
        ring.add(3)
        ring.add(4)
        assertEquals(listOf(2, 3, 4), ring.snapshot())
        ring.clear()
        assertEquals(emptyList(), ring.snapshot())
    }

    @Test
    fun debugLogKeepsNewestFirstLikeAndroidRing() {
        PortableDebugLog.clear()
        PortableDebugLog.log("Gen", "first")
        PortableDebugLog.log("Gen", "second")
        assertEquals(listOf("Gen: second", "Gen: first"), PortableDebugLog.getRecentLogs())
        PortableDebugLog.clear()
    }
}
