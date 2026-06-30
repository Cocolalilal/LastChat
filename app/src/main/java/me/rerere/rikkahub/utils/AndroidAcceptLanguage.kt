package me.rerere.rikkahub.utils

import android.content.Context
import android.os.Build
import me.rerere.common.http.AcceptLanguageBuilder

fun Context.acceptLanguageHeader(): String {
    return AcceptLanguageBuilder.withLanguageTags(systemLanguageTags())
        .build()
}

private fun Context.systemLanguageTags(): List<String> {
    val config = resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val locales = config.locales
        (0 until locales.size()).map { index -> locales[index].toLanguageTag() }
    } else {
        @Suppress("DEPRECATION")
        listOf(config.locale.toLanguageTag())
    }
}
