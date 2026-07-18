package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.LocalSettingsWideLayout
import me.rerere.rikkahub.ui.components.settings.LastChatProviderTab
import me.rerere.rikkahub.ui.components.settings.LastChatProvidersBottomBar

enum class ProvidersTab(
    val screen: Screen,
    val icon: ImageVector,
    val titleRes: Int,
) {
    Models(Screen.SettingProvider, Icons.Rounded.Cloud, R.string.setting_provider_page_title),
    Search(Screen.SettingSearch, Icons.Rounded.Public, R.string.setting_page_search_title),
    Tts(Screen.SettingTTS, Icons.AutoMirrored.Rounded.VolumeUp, R.string.setting_tts_page_title),
}

@Composable
fun ProvidersBottomBar(
    selectedTab: ProvidersTab,
    modifier: Modifier = Modifier,
    navController: NavHostController = LocalNavController.current,
    onTabSelected: ((ProvidersTab) -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val useWideLayout = LocalSettingsWideLayout.current
    LastChatProvidersBottomBar(
        tabs = ProvidersTab.entries.map { LastChatProviderTab(it.name, it.icon) },
        selectedId = selectedTab.name,
        useWideLayout = useWideLayout,
        modifier = modifier,
        onHaptic = { haptics.perform(HapticPattern.Tick) },
        onSelect = { id ->
            val tab = ProvidersTab.valueOf(id)
            if (onTabSelected != null) {
                onTabSelected(tab)
            } else {
                navController.navigate(tab.screen) {
                    popUpTo(selectedTab.screen) { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun ProvidersSecondaryActionSlot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val useWideLayout = LocalSettingsWideLayout.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (useWideLayout) 0.dp else 82.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
