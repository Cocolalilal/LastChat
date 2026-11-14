package me.rerere.rikkahub.ui.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayBlurDiagnosticsTest {
    private fun diagnostic(type: Int = 2032, requested: Boolean = true) = OverlayBlurDiagnostic(
        requested = requested, platform = true, hardware = true, active = requested,
        translucent = true, windowType = type, background = "LayerDrawable", generated = true,
        geometry = true, reason = if (requested) "native-layer-ready" else "not-requested", sdk = 36,
    )

    @Test fun `diagnostics remain available after the overlay closes and copy actual rendering state`() {
        val history = OverlayBlurDiagnosticHistory()
        history.record(diagnostic())
        history.record(diagnostic())
        history.record(diagnostic(requested = false))
        assertEquals(2, history.history.value.size)
        assertEquals(history.history.value.joinToString("\n") { it.text }, history.copyText())
        assertTrue(history.copyText().contains("reason=native-layer-ready"))
        assertTrue(history.copyText().contains("reason=not-requested"))
        assertTrue(history.copyText().contains("sdk=36"))
    }

    @Test fun `only the latest twelve distinct rendering states are retained`() {
        val history = OverlayBlurDiagnosticHistory()
        repeat(40) { history.record(diagnostic(type = it)) }
        assertEquals(12, history.history.value.size)
        assertEquals(28, history.history.value.first().windowType)
        assertEquals(39, history.history.value.last().windowType)
    }
}
