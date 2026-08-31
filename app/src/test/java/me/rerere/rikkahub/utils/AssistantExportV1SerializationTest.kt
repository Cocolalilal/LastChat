package me.rerere.rikkahub.utils

import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class AssistantExportV1SerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun olderBundleDefaultsEmbeddedBackgroundToNull() {
        val bundle = json.decodeFromString<AssistantExportV1>(
            """
            {
              "version": 1,
              "format": "lastchat_assistant",
              "assistant": {
                "id": "00000000-0000-0000-0000-000000000017",
                "name": "Legacy Bundle"
              }
            }
            """.trimIndent()
        )

        assertNull(bundle.backgroundContent)
        assertNull(bundle.backgroundMimeType)
    }

    @Test
    fun bundleRoundTripsEmbeddedBackgroundAndCustomColor() {
        val bundle = AssistantExportV1(
            assistant = Assistant(
                id = Uuid.parse("00000000-0000-0000-0000-000000000018"),
                name = "Portable Theme",
                background = "file:///original/background.png",
                useAssistantMaterialYouColors = true,
                materialYouColorIndex = -1,
                customMaterialYouColor = "#A1B2C3",
            ),
            backgroundContent = "cG5nLWJ5dGVz",
            backgroundMimeType = "image/png",
        )

        val decoded = json.decodeFromString<AssistantExportV1>(
            json.encodeToString(AssistantExportV1.serializer(), bundle)
        )

        assertEquals("cG5nLWJ5dGVz", decoded.backgroundContent)
        assertEquals("image/png", decoded.backgroundMimeType)
        assertEquals(-1, decoded.assistant.materialYouColorIndex)
        assertEquals("#A1B2C3", decoded.assistant.customMaterialYouColor)
    }
}
