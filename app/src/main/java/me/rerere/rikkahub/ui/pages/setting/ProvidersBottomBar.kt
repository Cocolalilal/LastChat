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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        if (!useWideLayout) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProvidersTab.entries.forEach { tab ->
                        val selected = tab == selectedTab
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                } else {
                                    Modifier.clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        if (onTabSelected != null) {
                                            onTabSelected(tab)
                                        } else {
                                            navController.navigate(tab.screen) {
                                                popUpTo(selectedTab.screen) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            actions()
        }
    }
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
