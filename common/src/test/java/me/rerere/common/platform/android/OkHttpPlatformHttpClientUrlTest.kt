package me.rerere.common.platform.android

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformServerEvent
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OkHttpPlatformHttpClientUrlTest {
    @Test
    fun executeRejectsBlankUrlWithoutOkHttpParseCrash() {
        val client = OkHttpPlatformHttpClient(OkHttpClient())
        try {
            runBlocking {
                client.execute(PlatformHttpRequest(method = "GET", url = "   "))
            }
            fail("Expected blank URL to fail before OkHttp parse")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("absolute http or https URL"))
        }
    }

    @Test
    fun streamEventsEmitsFailureForMissingSchemeInsteadOfCrashing() = runBlocking {
        val client = OkHttpPlatformHttpClient(OkHttpClient())
        val events = client.streamEvents(
            PlatformHttpRequest(method = "GET", url = "")
        ).toList()

        val failure = events.first() as PlatformServerEvent.Failure
        assertTrue(failure.message.orEmpty().contains("absolute http or https URL"))
        assertEquals(PlatformServerEvent.Closed, events.last())
    }
}
