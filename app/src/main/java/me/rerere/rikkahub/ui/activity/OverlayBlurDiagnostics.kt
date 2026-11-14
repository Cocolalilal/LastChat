package me.rerere.rikkahub.ui.activity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only rendering metadata is retained; no conversation, screenshot, app or assistant content. */
internal data class OverlayBlurDiagnostic(
    val requested: Boolean,
    val platform: Boolean,
    val hardware: Boolean,
    val active: Boolean,
    val translucent: Boolean,
    val windowType: Int,
    val background: String,
    val generated: Boolean,
    val geometry: Boolean,
    val reason: String,
    val sdk: Int,
) {
    val text: String get() = "requested=$requested platform=$platform hardware=$hardware active=$active " +
        "translucent=$translucent type=$windowType background=${background.take(96)} " +
        "generated=$generated geometry=$geometry reason=${reason.take(64)} sdk=$sdk"
}

internal class OverlayBlurDiagnosticHistory {
    private val entries = MutableStateFlow<List<OverlayBlurDiagnostic>>(emptyList())
    val history = entries.asStateFlow()

    @Synchronized
    fun record(diagnostic: OverlayBlurDiagnostic) {
        if (entries.value.lastOrNull() == diagnostic) return
        entries.value = (entries.value + diagnostic).takeLast(12)
    }

    fun copyText(): String = entries.value.joinToString("\n") { it.text }
}

/** Process-lifetime history survives closing the overlay before opening Settings. */
internal val OverlayBlurDiagnostics = OverlayBlurDiagnosticHistory()
