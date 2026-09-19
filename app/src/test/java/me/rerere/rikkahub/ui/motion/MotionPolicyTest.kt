package me.rerere.rikkahub.ui.motion

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import me.rerere.rikkahub.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {
    @Test
    fun chatAndMenuUseTopLevelFade() {
        assertTrue(
            shouldUseTopLevelFade(
                initialRoute = "$CHAT_ROUTE_BASE/{id}",
                targetRoute = MENU_ROUTE
            )
        )
    }

    @Test
    fun menuAndSettingDoNotUseTopLevelFade() {
        assertFalse(
            shouldUseTopLevelFade(
                initialRoute = MENU_ROUTE,
                targetRoute = SETTING_ROUTE
            )
        )
    }

    @Test
    fun settingDetailsStayHierarchical() {
        assertFalse(
            shouldUseTopLevelFade(
                initialRoute = SETTING_ROUTE,
                targetRoute = Screen.SettingDisplay.serializer().descriptor.serialName
            )
        )
    }

    @Test
    fun settingDisplayIsNotTopLevel() {
        assertFalse(isTopLevelRootRoute(Screen.SettingDisplay.serializer().descriptor.serialName))
    }

    @Test
    fun sectionExpandIsFadeOnlyWhenReduceMotion() {
        val reduced = MotionPolicy(reduceMotion = true)
        val full = MotionPolicy(reduceMotion = false)
        assertEquals(
            fadeIn(animationSpec = tween(durationMillis = 120)),
            reduced.sectionExpandEnter(),
        )
        assertEquals(
            fadeOut(animationSpec = tween(durationMillis = 90)),
            reduced.sectionExpandExit(),
        )
        assertNotEquals(reduced.sectionExpandEnter(), full.sectionExpandEnter())
        assertNotEquals(reduced.sectionExpandExit(), full.sectionExpandExit())
    }
}
