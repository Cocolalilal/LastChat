package me.rerere.rikkahub.widget

import android.graphics.Bitmap
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.MindRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetAvatarRendererTest {

    private fun Bitmap.extractPixels(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun Bitmap.nonTransparentPixelCount(): Int {
        return extractPixels().count { (it ushr 24) != 0 }
    }

    @Test
    fun renderProducesNonNullNonBlankAndDistinctBitmapsAcrossSeedsAndVariants() {
        val context = RuntimeEnvironment.getApplication()
        val genericalAvatar = Avatar.Animated.defaultGenerical(shape = "blob")
        val grokAvatar = Avatar.Animated(
            shape = "star",
            eyeType = "grok",
            colorHex = "#7C5CFF",
            colorPreset = "indigo",
        )

        val bmpSeed1Var0 = WidgetAvatarRenderer.render(context, genericalAvatar, seed = 101, sizePx = 256, variant = 0)
        val bmpSeed1Var0Repeat = WidgetAvatarRenderer.render(context, genericalAvatar, seed = 101, sizePx = 256, variant = 0)
        val bmpSeed2Var0 = WidgetAvatarRenderer.render(context, genericalAvatar, seed = 202, sizePx = 256, variant = 0)
        val bmpSeed1Var1 = WidgetAvatarRenderer.render(context, genericalAvatar, seed = 101, sizePx = 256, variant = 1)
        val bmpSeed1Var2 = WidgetAvatarRenderer.render(context, genericalAvatar, seed = 101, sizePx = 256, variant = 2)
        val bmpGrok = WidgetAvatarRenderer.render(context, grokAvatar, seed = 303, sizePx = 256, variant = 0)

        assertNotNull(bmpSeed1Var0)
        assertNotNull(bmpSeed1Var0Repeat)
        assertNotNull(bmpSeed2Var0)
        assertNotNull(bmpSeed1Var1)
        assertNotNull(bmpSeed1Var2)
        assertNotNull(bmpGrok)

        requireNotNull(bmpSeed1Var0)
        requireNotNull(bmpSeed1Var0Repeat)
        requireNotNull(bmpSeed2Var0)
        requireNotNull(bmpSeed1Var1)
        requireNotNull(bmpSeed1Var2)
        requireNotNull(bmpGrok)

        assertTrue("Expected > 2000 non-transparent pixels", bmpSeed1Var0.nonTransparentPixelCount() > 2_000)
        assertTrue("Expected > 2000 non-transparent pixels", bmpSeed2Var0.nonTransparentPixelCount() > 2_000)
        assertTrue("Expected > 2000 non-transparent pixels", bmpSeed1Var1.nonTransparentPixelCount() > 2_000)
        assertTrue("Expected > 2000 non-transparent pixels for Grok mark", bmpGrok.nonTransparentPixelCount() > 2_000)

        val pixelsSeed1Var0 = bmpSeed1Var0.extractPixels()
        assertTrue("Same (seed, variant) must render deterministically", pixelsSeed1Var0.contentEquals(bmpSeed1Var0Repeat.extractPixels()))
        assertFalse("Different seeds must produce different pixels", pixelsSeed1Var0.contentEquals(bmpSeed2Var0.extractPixels()))
        assertFalse("Advancing variant (0 -> 1) must produce different pixels", pixelsSeed1Var0.contentEquals(bmpSeed1Var1.extractPixels()))
        assertFalse("Advancing variant (1 -> 2) must produce different pixels", bmpSeed1Var1.extractPixels().contentEquals(bmpSeed1Var2.extractPixels()))
    }

    @Test
    fun faceForNeverSelectsAngryOrPoutyAndAlwaysChangesOnVariantAdvance() {
        // Only the ANGER family is banned from a home-screen widget. The rest of
        // MindRules.NEGATIVE_FACES (Sad, Bored, hurt or cute, ...) is deliberately in the pool:
        // a bored or pouty-cute mark on the home screen is fine, a cross one is not.
        val forbiddenFaces = setOf("Angry", "pouty", "Annoyed", "angered or concentrated")

        var checked = 0
        for (seed in -15..15) {
            var prevFace: String? = null
            for (variant in 0 until 10) {
                val face = WidgetAvatarRenderer.faceFor(seed * 7919 + 17, variant)
                assertFalse("Forbidden face selected: $face", face in forbiddenFaces)
                assertTrue("Face must belong to NO_ANGER_FACES: $face", face in WidgetAvatarRenderer.NO_ANGER_FACES)
                if (prevFace != null) {
                    assertNotEquals(
                        "Advancing variant must change the face (seed=$seed, variant=$variant)",
                        prevFace,
                        face,
                    )
                }
                prevFace = face
                checked++
            }
        }
        assertTrue("Expected at least 200 combinations checked, got $checked", checked >= 200)
    }

    @Test
    fun widgetPrefsAdvanceVariantIncrementsOnceAndNeverRepeatsStoredFace() {
        val context = RuntimeEnvironment.getApplication()
        val widgetId = 42
        val seed = WidgetPrefs.generateSeed(widgetId)
        val initialFaceIdx = WidgetAvatarRenderer.faceIndexFor(seed, 0)

        WidgetPrefs.saveConfig(
            context = context,
            widgetId = widgetId,
            assistantId = "00000000-0000-0000-0000-000000000001",
            assistantName = "Rikka",
            avatarType = "animated",
            avatarData = """{"shape":"blob","eyeType":"generical"}""",
            seed = seed,
            variant = 0,
            faceIndex = initialFaceIdx,
        )

        var prevVariant = WidgetPrefs.getVariant(context, widgetId)
        var prevFaceIdx = WidgetPrefs.getFaceIndex(context, widgetId)
        assertEquals(0, prevVariant)
        assertEquals(initialFaceIdx, prevFaceIdx)

        for (step in 1..20) {
            val nextVariant = WidgetPrefs.advanceVariant(context, widgetId)
            val nextFaceIdx = WidgetPrefs.getFaceIndex(context, widgetId)
            assertEquals(prevVariant + 1, nextVariant)
            assertEquals(nextVariant, WidgetPrefs.getVariant(context, widgetId))
            assertNotEquals("Stored face index must change on every update", prevFaceIdx, nextFaceIdx)
            prevVariant = nextVariant
            prevFaceIdx = nextFaceIdx
        }
    }

    @Test
    fun pickedPresetRendersIdenticallyRegardlessOfSeedOrRefreshForBothEyeStyles() {
        val context = RuntimeEnvironment.getApplication()
        val avatars = listOf(
            Avatar.Animated.defaultGenerical(),
            Avatar.Animated(shape = "star", eyeType = "grok"),
        )
        for (avatar in avatars) {
            val pixelsByPreset = WidgetAvatarPreset.entries.map { preset ->
                val preview = requireNotNull(WidgetAvatarRenderer.render(
                    context, avatar, seed = 0, sizePx = 128, variant = 0, presetId = preset.id,
                ))
                val refreshed = requireNotNull(WidgetAvatarRenderer.render(
                    context, avatar, seed = 7919, sizePx = 128, variant = 12, presetId = preset.id,
                ))
                assertTrue("Preset must paint a visible mark", preview.nonTransparentPixelCount() > 500)
                val pixels = preview.extractPixels()
                assertTrue("Picked look must survive refresh: ${preset.id}",
                    pixels.contentEquals(refreshed.extractPixels()))
                preview.recycle()
                refreshed.recycle()
                pixels
            }
            for (i in pixelsByPreset.indices) {
                for (j in 0 until i) {
                    assertFalse("Each handpicked option must look different ($i, $j)",
                        pixelsByPreset[i].contentEquals(pixelsByPreset[j]))
                }
            }
        }
    }

    @Test
    fun presetPersistsPerWidgetAndIsRemovedOnDeletionOrReconfiguration() {
        val context = RuntimeEnvironment.getApplication()
        fun save(widgetId: Int, presetId: String?) = WidgetPrefs.saveConfig(
            context, widgetId, "character-$widgetId", "Character", "animated", "{}",
            seed = 17, presetId = presetId,
        )
        assertTrue(save(801, WidgetAvatarPreset.Curious.id))
        assertTrue(save(802, WidgetAvatarPreset.Sleepy.id))
        repeat(5) { assertEquals(0, WidgetPrefs.advanceVariant(context, 801)) }
        assertEquals(WidgetAvatarPreset.Curious.id, WidgetPrefs.getPresetId(context, 801))
        assertEquals(WidgetAvatarPreset.Sleepy.id, WidgetPrefs.getPresetId(context, 802))

        WidgetPrefs.deleteConfig(context, 801)
        assertEquals(null, WidgetPrefs.getPresetId(context, 801))
        assertEquals(WidgetAvatarPreset.Sleepy.id, WidgetPrefs.getPresetId(context, 802))
        assertTrue(save(802, null))
        assertEquals(null, WidgetPrefs.getPresetId(context, 802))
        assertEquals(1, WidgetPrefs.advanceVariant(context, 802))
    }

    @Test
    fun unknownPresetFallsBackToLegacyRenderingAndRefresh() {
        val context = RuntimeEnvironment.getApplication()
        val avatar = Avatar.Animated.defaultGenerical()
        val legacy = requireNotNull(WidgetAvatarRenderer.render(context, avatar, 123, 128, 2))
        val unknown = requireNotNull(WidgetAvatarRenderer.render(context, avatar, 123, 128, 2, "unknown"))
        assertTrue(legacy.extractPixels().contentEquals(unknown.extractPixels()))
        legacy.recycle()
        unknown.recycle()
        WidgetPrefs.saveConfig(context, 803, "character", "Character", "animated", "{}", presetId = "unknown")
        assertEquals(1, WidgetPrefs.advanceVariant(context, 803))
    }

}
