package me.rerere.tts.provider

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformSystemTtsContractTest {
    @Test
    fun unavailableSystemTtsIsHonestAndSafeToStop() {
        val tts = UnavailableSystemTts()
        assertFalse(tts.available)
        var done = false
        tts.speak("hello", speechRate = 1.0f, pitch = 1.0f) { done = true }
        assertTrue(done)
        tts.stop()
    }
}
