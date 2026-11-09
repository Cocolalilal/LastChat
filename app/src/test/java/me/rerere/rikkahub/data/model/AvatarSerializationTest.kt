package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AvatarSerializationTest {
    @Test
    fun legacyTraitOverridesAreIgnoredWithoutChangingAppearance() {
        val avatar = JsonInstant.decodeFromString(
            Avatar.serializer(),
            """
            {
              "type": "animated",
              "shape": "heart",
              "eyeType": "grok",
              "colorHex": "#123456",
              "colorPreset": null,
              "eyeColorHex": "#FFFFFF",
              "eyeShade": false,
              "traits": {"energy": 0.9, "warmth": 0.1, "playfulness": 0.8},
              "stockColorVersion": 1
            }
            """.trimIndent(),
        )
        assertEquals(
            Avatar.Animated(
                shape = "heart", eyeType = "grok", colorHex = "#123456",
                colorPreset = null, eyeColorHex = "#FFFFFF", eyeShade = false, stockColorVersion = 1,
            ),
            avatar,
        )
        val saved = JsonInstant.encodeToString(Avatar.serializer(), avatar)
        assertFalse(JsonInstant.parseToJsonElement(saved).jsonObject.containsKey("traits"))
        assertEquals(avatar, JsonInstant.decodeFromString(Avatar.serializer(), saved))
    }

    @Test
    fun olderAnimatedAvatarsKeepTheirAppearanceDefaults() {
        val avatar = JsonInstant.decodeFromString(Avatar.serializer(), """{"type":"animated","traits":null}""")
        assertEquals(Avatar.Animated(), avatar)
    }
}
