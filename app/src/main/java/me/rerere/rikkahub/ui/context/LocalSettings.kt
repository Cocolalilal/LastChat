package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import me.rerere.rikkahub.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
