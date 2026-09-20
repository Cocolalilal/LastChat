package me.rerere.rikkahub.data.widget

import me.rerere.common.platform.UnavailableShareSheet
import me.rerere.rikkahub.data.share.PortableSharePayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetAndShareContractTest {
    @Test
    fun widgetSnapshotUsesAppGroupSuiteName() {
        assertEquals("group.lastchat.rikkafork.cocolal", AssistantWidgetSnapshot.USER_DEFAULTS_SUITE)
        assertEquals("assistant_widget_snapshot", AssistantWidgetSnapshot.USER_DEFAULTS_KEY)
        assertEquals("pending_share_text", PortableSharePayload.PENDING_SHARE_TEXT_KEY)
        assertEquals("pending_overlay_prompt", PortableSharePayload.PENDING_OVERLAY_PROMPT_KEY)
    }

    @Test
    fun noOpWidgetStoreDoesNotThrow() {
        val store = NoOpWidgetStore()
        store.publish(
            AssistantWidgetSnapshot(
                assistantId = "a",
                assistantName = "Assistant",
            ),
        )
        store.clear()
        assertNull(store.consumePendingShareText())
    }

    @Test
    fun unavailableShareSheetIsHonest() {
        val sheet = UnavailableShareSheet()
        assertFalse(sheet.available)
        sheet.shareText("Title", "Body")
        assertTrue(PortableSharePayload(text = "Body").hasContent())
    }
}
