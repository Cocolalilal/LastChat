package me.rerere.lastchat.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosChatDisplayTest {
    @Test
    fun greetingUsesHourBandsAndAssistantName() {
        assertEquals("Good morning, Ada", iosGreeting(7, "Ada"))
        assertEquals("Good afternoon, Ada", iosGreeting(15, "Ada"))
        assertEquals("Good evening.", iosGreeting(22, "  "))
    }

    @Test
    fun fencedCodeSplitsLanguageAndBody() {
        val segments = splitIosChatText("intro\n```kotlin\nval x = 1\n```\noutro")
        assertEquals(3, segments.size)
        assertEquals(IosChatTextSegment.Text("intro\n"), segments[0])
        val code = segments[1] as IosChatTextSegment.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.value)
        assertEquals(IosChatTextSegment.Text("\noutro"), segments[2])
    }

    @Test
    fun contextStackLabelJoinsNonZeroSources() {
        val summary = IosContextStackSummary(lore = 2, modes = 1, memories = 0)
        assertEquals(3, summary.total)
        assertEquals("2 lore · 1 skills", summary.label)
        assertEquals("Context", IosContextStackSummary(0, 0, 0).label)
    }

    @Test
    fun tokenUsageAndReasoningPreviewMatchChatChrome() {
        assertEquals("12 → 34  (46)", iosTokenUsageLabel(12, 34, 0))
        assertEquals("12 → 34  (99)", iosTokenUsageLabel(12, 34, 99))
        assertEquals("short thought", iosReasoningPreview("short thought"))
        val preview = iosReasoningPreview("word ".repeat(80), maxChars = 20)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 21)
    }

    @Test
    fun assistantUiOverridesHideCharacterNameWhenAvatarHidden() {
        val global = IosAppearancePreferences(showModelIcon = true, showModelName = true, showUserAvatar = true)
        val effective = global.withAssistantUi(
            IosAssistantUiSettings(showAssistantAvatar = false, showUserAvatar = false, fontSizeRatio = 1.25f),
        )
        assertEquals(false, effective.showModelIcon)
        assertEquals(false, effective.showModelName)
        assertEquals(false, effective.showUserAvatar)
        assertEquals(1.25f, effective.fontSizeRatio)
        assertEquals("A", iosAvatarLetter(" ada", "Y"))
        assertEquals("Y", iosAvatarLetter("  ", "Y"))
    }

    @Test
    fun customFontSourcePrefersLoadedFamilyOverBundledFlex() {
        val custom = androidx.compose.ui.text.font.FontFamily.Serif
        val settings = IosFontSettings(
            headerFont = IosFontConfig(fontSource = IosFontSource.CUSTOM, customFontPath = "custom_fonts/x.ttf"),
        )
        assertEquals(
            custom,
            iosFontFamilyChoice(
                lastChatFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                settings = settings,
                usePhoneSystemFont = false,
                customFamily = custom,
            ),
        )
        assertEquals(
            androidx.compose.ui.text.font.FontFamily.SansSerif,
            iosFontFamilyChoice(
                lastChatFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                settings = settings,
                usePhoneSystemFont = false,
                customFamily = null,
            ),
        )
        val customCode = androidx.compose.ui.text.font.FontFamily.Serif
        assertEquals(
            customCode,
            iosCodeFontFamily(
                settings = IosFontSettings(
                    codeFont = IosFontConfig(
                        fontSource = IosFontSource.CUSTOM,
                        customFontPath = "custom_fonts/code.ttf",
                    ),
                ),
                customFamily = customCode,
            ),
        )
    }
}
