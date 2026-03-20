package me.rerere.rikkahub.ui.motion

import me.rerere.rikkahub.Screen
import org.junit.Assert.assertFalse
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
    fun menuAndSettingUseTopLevelFade() {
        assertTrue(
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
}
