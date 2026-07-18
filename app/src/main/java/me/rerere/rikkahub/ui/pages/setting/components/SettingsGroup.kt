package me.rerere.rikkahub.ui.pages.setting.components

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem

@Composable
fun SettingsGroup(
    title: String,
    horizontalPadding: Dp = 16.dp,
    titleStartPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    LastChatSettingsGroup(
        title = title,
        horizontalPadding = horizontalPadding,
        titleStartPadding = titleStartPadding,
        content = content,
    )
}

@Composable
fun SettingGroupItem(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberPremiumHaptics()
    LastChatSettingGroupItem(
        title = title,
        darkTheme = LocalDarkMode.current,
        subtitle = subtitle,
        icon = icon,
        trailing = trailing,
        contentPadding = contentPadding,
        onHaptic = { haptics.perform(HapticPattern.Pop) },
        onClick = onClick,
    )
}

@Composable
fun SettingGroupInputItem(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    LastChatSettingGroupInputItem(
        title = title,
        darkTheme = LocalDarkMode.current,
        subtitle = subtitle,
        icon = icon,
        trailing = trailing,
        content = content,
    )
}
