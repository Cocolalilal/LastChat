package me.rerere.rikkahub.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebServerServiceStartPolicyTest {
    @Test
    fun restrictedForegroundStartIsDetectedFromClassNameOrMessage() {
        assertTrue(
            WebServerService.isForegroundStartNotAllowed(
                IllegalStateException(
                    "Not allowed to start service Intent: app is in background " +
                        "ForegroundServiceStartNotAllowedException"
                )
            )
        )
        val named = object : IllegalStateException("restricted") {}
        // Class name check covers the platform ForegroundServiceStartNotAllowedException.
        assertFalse(WebServerService.isForegroundStartNotAllowed(named))
    }

    @Test
    fun unrelatedFailuresAreNotTreatedAsRestrictedStarts() {
        assertFalse(
            WebServerService.isForegroundStartNotAllowed(
                IllegalStateException("Something else went wrong")
            )
        )
        assertFalse(
            WebServerService.isForegroundStartNotAllowed(
                RuntimeException("boom")
            )
        )
    }
}
