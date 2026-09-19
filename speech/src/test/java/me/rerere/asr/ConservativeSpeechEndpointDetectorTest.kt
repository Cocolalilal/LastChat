package me.rerere.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConservativeSpeechEndpointDetectorTest {
    @Test
    fun doesNotStopOnBriefMidSentencePause() {
        val detector = ConservativeSpeechEndpointDetector()
        var now = 1_000L
        repeat(15) {
            assertFalse(detector.onFrame(0.4f, now))
            now += 100L
        }
        repeat(20) {
            assertFalse(detector.onFrame(0.05f, now))
            now += 100L
        }
    }

    @Test
    fun stopsOnlyAfterSustainedSpeechAndLongSilence() {
        val detector = ConservativeSpeechEndpointDetector()
        var now = 1_000L
        repeat(15) {
            assertFalse(detector.onFrame(0.4f, now))
            now += 100L
        }
        var stopped = false
        repeat(40) {
            if (detector.onFrame(0.05f, now)) {
                stopped = true
            }
            now += 100L
        }
        assertTrue(stopped)
    }

    @Test
    fun ignoresSilenceBeforeAnySpeech() {
        val detector = ConservativeSpeechEndpointDetector()
        var now = 1_000L
        repeat(50) {
            assertFalse(detector.onFrame(0.01f, now))
            now += 100L
        }
    }
}
