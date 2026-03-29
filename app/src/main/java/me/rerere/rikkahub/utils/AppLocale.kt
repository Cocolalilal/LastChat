package me.rerere.rikkahub.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import me.rerere.rikkahub.LastChatApp
import java.util.Locale

fun Context.appLocale(): Locale = resources.configuration.primaryLocale()

fun currentAppLocale(): Locale {
    return runCatching { LastChatApp.instance.appLocale() }
        .getOrElse { Locale.getDefault() }
}

fun Locale.localizedDisplayLanguage(displayLocale: Locale = currentAppLocale()): String {
    return getDisplayLanguage(displayLocale).ifBlank { displayLanguage }
}

private fun Configuration.primaryLocale(): Locale {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        locales[0] ?: Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        locale ?: Locale.getDefault()
    }
}
