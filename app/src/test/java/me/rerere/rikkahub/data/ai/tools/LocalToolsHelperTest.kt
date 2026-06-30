package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
class LocalToolsHelperTest {
    // Scheduled message and notification tool json tests

    @Test
    fun `scheduledMessageToolJson preserves success response shape`() {
        val json = scheduledMessageToolJson(
            ScheduledLocalToolMessage(
                status = "success",
                scheduledAt = "2026-06-18T10:15:30Z",
                workName = "scheduled-message-work",
            )
        )

        assertEquals("success", json["status"]?.jsonPrimitive?.content)
        assertEquals("2026-06-18T10:15:30Z", json["scheduled_at"]?.jsonPrimitive?.content)
        assertEquals("scheduled-message-work", json["work_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `notificationsToolJson preserves notification list response shape`() {
        val json = notificationsToolJson(
            listOf(
                LocalToolNotificationSnapshot(
                    packageName = "com.example",
                    title = "Title",
                    content = "Content",
                    postTime = 123L,
                )
            )
        )
        val first = json["notifications"]?.jsonArray?.first()

        assertEquals("com.example", first?.jsonObject?.get("package")?.jsonPrimitive?.content)
        assertEquals("Title", first?.jsonObject?.get("title")?.jsonPrimitive?.content)
        assertEquals("Content", first?.jsonObject?.get("content")?.jsonPrimitive?.content)
        assertEquals("123", first?.jsonObject?.get("time")?.jsonPrimitive?.content)
    }
}
