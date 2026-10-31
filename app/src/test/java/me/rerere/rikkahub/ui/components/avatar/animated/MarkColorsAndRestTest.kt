package me.rerere.rikkahub.ui.components.avatar.animated

import android.app.Application
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.migrateGenericalDefaultAvatar
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.random.Random

/**
 * Colour rules (swatch == render), Grok per-expression spacing, the Generical default
 * avatar migration, and the static-avatar settle (no cut, lands exactly at rest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MarkColorsAndRestTest {

    @Before
    fun load() {
        AvatarPackData.ensureLoaded(RuntimeEnvironment.getApplication())
    }

    private fun hex(c: Color) = MarkColors.colorToHex(c)

    @Test
    fun genericalUsesLightBlueAndMigratesOldStockMarks() {
        val stock = Avatar.Animated.defaultGenerical()
        assertEquals("#009FE0", stock.colorHex)
        assertEquals("azure", stock.colorPreset)
        assertEquals(stock.colorHex, hex(MarkColors.bodyColor(stock.colorHex, stock.colorPreset)))

        // Old indigo mark migrates to the new light blue default.
        val oldIndigo = Avatar.Animated(colorHex = "#7C5CFF", colorPreset = "indigo")
        val assistantIndigo = Assistant(
            id = me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID,
            name = "Generical",
            avatar = oldIndigo,
        )
        assertEquals(
            stock,
            Settings(assistants = listOf(assistantIndigo)).migrateGenericalDefaultAvatar().assistants.single().avatar,
        )

        // Old stock blue mark (#0078D7 / "generical") migrates to the new light blue default.
        val oldBlue = Avatar.Animated(
            colorHex = Avatar.Animated.GENERICAL_BLUE_HEX,
            colorPreset = Avatar.Animated.GENERICAL_BLUE_PRESET,
            stockColorVersion = 1,
        )
        val assistantBlue = Assistant(
            id = me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID,
            name = "Generical",
            avatar = oldBlue,
        )
        val migratedSettings = Settings(assistants = listOf(assistantBlue)).migrateGenericalDefaultAvatar()
        assertEquals(stock, migratedSettings.assistants.single().avatar)

        // Running the migration twice is a no-op (idempotent).
        assertEquals(migratedSettings, migratedSettings.migrateGenericalDefaultAvatar())

        // Explicitly chosen colours are left alone.
        val explicitIndigo = oldIndigo.copy(stockColorVersion = 1)
        assertEquals(
            explicitIndigo,
            Settings(assistants = listOf(assistantIndigo.copy(avatar = explicitIndigo)))
                .migrateGenericalDefaultAvatar().assistants.single().avatar,
        )
        val explicitRed = Avatar.Animated(colorHex = "#FF3E51", colorPreset = "red")
        val assistantRed = Assistant(
            id = me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID,
            name = "Generical",
            avatar = explicitRed,
        )
        assertEquals(
            explicitRed,
            Settings(assistants = listOf(assistantRed)).migrateGenericalDefaultAvatar().assistants.single().avatar,
        )
    }

    @Test
    fun everyBodySwatchRendersItsOwnColourInBothModes() {
        for (mode in listOf("generical", "grok")) {
            for ((name, h) in MarkColors.PRESETS) {
                assertEquals("$mode/$name", h.uppercase(), hex(MarkColors.bodyColor(h, name, mode)))
            }
        }
        // The teal swatch is teal in Generical mode too (it used to render the Generical blue).
        assertEquals("#1CC3B0", hex(MarkColors.bodyColor("#1CC3B0", "cyan", "generical")))
        // …and the stock blue swatches (generical and azure) render their distinct colours.
        assertEquals("#0078D7", hex(MarkColors.bodyColor("#0078D7", "generical", "generical")))
        assertEquals("#009FE0", hex(MarkColors.bodyColor("#009FE0", "azure", "generical")))
    }

    @Test
    fun legacyCyanPairKeepsWhatEachModeShowed() {
        assertEquals("#009FE0" to "azure", MarkColors.canonicalBody(null, null, "generical"))
        assertEquals("#009FE0" to "azure", MarkColors.canonicalBody("#009FE0", "cyan", "generical"))
        assertEquals("#1CC3B0" to "cyan", MarkColors.canonicalBody("#009FE0", "cyan", "grok"))
        assertEquals("#7C5CFF" to "indigo", MarkColors.canonicalBody("#7c5cff", null, "generical"))
        assertEquals("#123456" to null, MarkColors.canonicalBody("#123456", null, "grok"))
    }

    @Test
    fun eyeSwatchesRenderTheirColourAndDefaultsMatchTheFirstSwatch() {
        for (mode in listOf("generical", "grok")) {
            val swatches = eyeSwatches(mode)
            assertEquals(swatches.size, swatches.distinct().size)
            assertEquals(MarkColors.defaultEyeHex(mode).uppercase(), swatches.first())
            assertEquals(swatches.first(), hex(MarkColors.eyeColor(mode, null, "#2A92FE", "blue")))
            for (s in swatches) assertEquals("$mode/$s", s, hex(MarkColors.eyeColor(mode, s, "#2A92FE", "blue")))
        }
    }

    @Test
    fun genericalEyeShadeIsEightyPercentSurface() {
        for (bodyHex in listOf("#7C5CFF", "#FF781C", "#00C972", "#000000")) {
            val body = MarkColors.hexToColor(bodyHex)
            for (eye in listOf(Color.White, MarkColors.hexToColor("#FFD27A"), MarkColors.hexToColor("#1A1A1A"))) {
                val f = MarkColors.genericalEyeFade(body, eye)
                assertEquals(eye.red + (body.red - eye.red) * MarkColors.EYE_SHADE_MIX, f.red, 0.002f)
                assertEquals(eye.blue + (body.blue - eye.blue) * MarkColors.EYE_SHADE_MIX, f.blue, 0.002f)
                // Shade off: plain eye colour.
                assertEquals(hex(eye), hex(MarkColors.genericalEyeFade(body, eye, 0f)))
            }
        }
    }

    @Test
    fun themeOffersOnlyFittingMarkColours() {
        // Indigo body + default white eyes: body only.
        val a = MarkColors.themeCandidates("generical", "#7C5CFF", "indigo", null)
        assertEquals(listOf("#7C5CFF"), a.map(::hex))
        // Coloured eyes are offered too, after the body.
        val b = MarkColors.themeCandidates("grok", "#FF781C", "orange", "#C4A8FF")
        assertEquals(listOf("#FF781C", "#C4A8FF"), b.map(::hex))
        // Black / grey bodies and ink eyes are not theme material.
        assertTrue(MarkColors.themeCandidates("grok", "#000000", "black", null).isEmpty())
        assertTrue(MarkColors.themeCandidates("grok", "#959595", "gray", "#1A1A1A").isEmpty())
    }

    @Test
    fun grokExpressionsKeepNeutralSpacing() {
        // Engine14: no expression spreads the eyes; the never-touch guard does the minimum.
        val neutral = WhSphereEyes.expressionPair("neutral", 1f)
        assertEquals(WhSphereEyes.neutralPair().map { it.offX }, neutral.map { it.offX })
        for (e in listOf("sleepy", "dizzy", "squinted", "happy", "sad", "surprised", "angry")) {
            val pair = WhSphereEyes.expressionPair(e, 1f)
            assertTrue("$e spreads", pair.all { it.offX <= neutral[0].offX + 1e-4f })
        }
        // Spacing glides during a morph (offX is blended, never jumps).
        val mid = WhSphereEyes.morphPair("Neutral", "Sleeping", 0.5f, 1f)[0].offX
        val end = WhSphereEyes.morphPair("Neutral", "Sleeping", 1f, 1f)[0].offX
        assertTrue(mid > neutral[0].offX.coerceAtMost(end) - 1e-4f && mid < maxOf(neutral[0].offX, end) + 1e-4f)
    }

    @Test
    fun genericalStockPictureBecomesItsMarkOtherAvatarsUntouched() {
        val stock = Avatar.Resource(R.drawable.default_generical_pfp)
        val custom = Avatar.Emoji("🙂")
        val s = Settings.dummy().copy(
            assistants = listOf(
                Assistant(name = "Generical", avatar = stock),
                Assistant(name = "Generical", avatar = custom),
                Assistant(name = "Other", avatar = stock),
            )
        )
        val m = s.migrateGenericalDefaultAvatar()
        assertEquals(Avatar.Animated.defaultGenerical(), m.assistants[0].avatar)
        assertEquals(custom, m.assistants[1].avatar)
        assertEquals(stock, m.assistants[2].avatar)
        assertEquals(m, m.migrateGenericalDefaultAvatar())
    }

    @Test
    fun staticAvatarEasesToRestWithoutACutAndBackIn() {
        val st = AvatarMotionState(Random(5))
        val dt = 1f / 60f
        var ms = 0L
        // Busy for a while so the pose is well away from rest.
        repeat((3.0f / dt).toInt()) { st.tick(dt, AvatarMotionHints(isGenerating = true), ms); ms += 16 }
        var prev = st.frame
        st.animate = false
        var settledAt = -1f
        var t = 0f
        while (t < 2.5f) {
            st.tick(dt, AvatarMotionHints(isGenerating = true), ms); ms += 16; t += dt
            val f = st.frame
            assertTrue("no yaw cut", abs(f.body.yaw - prev.body.yaw) < 12f)
            assertTrue("no pitch cut", abs(f.body.pitch - prev.body.pitch) < 12f)
            assertTrue("no size jump", abs(f.body.scale - prev.body.scale) < 0.02f)
            if (settledAt < 0f && st.isSettled) settledAt = t
            prev = f
        }
        assertTrue("settles within ~2 s (was $settledAt)", settledAt in 0f..2.0f)
        val rest = st.frame
        assertEquals(0f, rest.body.yaw, 1e-3f)
        assertEquals(0f, rest.body.pitch, 1e-3f)
        assertEquals(1f, rest.body.scale, 1e-3f)
        assertEquals(1f, rest.lid, 1e-3f)
        assertEquals("Neutral", rest.exprTo)
        assertFalse(st.animate)
        // Back to newest: eases in from exactly that rest frame.
        st.animate = true
        st.tick(dt, AvatarMotionHints(), ms)
        val first = st.frame
        assertTrue(abs(first.body.yaw) < 1f && abs(first.body.scale - 1f) < 0.01f)
        prev = first
        repeat(90) {
            ms += 16
            st.tick(dt, AvatarMotionHints(), ms)
            val f = st.frame
            assertTrue(abs(f.body.yaw - prev.body.yaw) < 12f && abs(f.body.scale - prev.body.scale) < 0.02f)
            prev = f
        }
    }
}
