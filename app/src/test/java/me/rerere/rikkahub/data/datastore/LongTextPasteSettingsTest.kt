package me.rerere.rikkahub.data.datastore

import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Test

class LongTextPasteSettingsTest {
    @Test
    fun `existing display settings default to asking before a large paste`() {
        val setting = JsonInstant.decodeFromString<DisplaySetting>("""{"userNickname":"Tester"}""")
        assertEquals(LongTextPasteMode.ASK, setting.longTextPasteMode)
        assertEquals("Tester", setting.userNickname)
    }

    @Test
    fun `all three paste preferences survive serialization`() {
        for (mode in LongTextPasteMode.entries) {
            val setting = DisplaySetting(longTextPasteMode = mode)
            val decoded = JsonInstant.decodeFromString<DisplaySetting>(JsonInstant.encodeToString(setting))
            assertEquals(mode, decoded.longTextPasteMode)
        }
    }
}
