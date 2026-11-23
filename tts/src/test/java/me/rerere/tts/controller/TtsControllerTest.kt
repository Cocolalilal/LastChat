package me.rerere.tts.controller

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import me.rerere.tts.model.*
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TtsSpeechGenerator
import org.junit.Assert.*
import org.junit.Test

class TtsControllerTest {
    private class Player : TtsAudioPlayer {
        override val playbackState = MutableStateFlow(PlaybackState())
        val enqueued = mutableListOf<Int>()
        var skips = 0
        override fun enqueue(chunkIndex: Int, totalChunks: Int, response: TTSResponse) { enqueued += chunkIndex }
        override fun skipNext() { skips++ }
        override fun pause() {}
        override fun resume() {}
        override fun stop() {}
        override fun clear() {}
        override fun release() {}
        override fun seekBy(ms: Long) {}
        override fun setSpeed(speed: Float) {}
        override fun setTotalChunks(total: Int) {}
    }
    private fun generator(block: suspend (String) -> Unit) = object : TtsSpeechGenerator {
        override fun generateSpeech(providerSetting: TTSProviderSetting, request: TTSRequest): Flow<AudioChunk> = flow {
            block(request.text)
            emit(AudioChunk(byteArrayOf(1, 0), AudioFormat.PCM, 24000, isLast = true))
        }
    }
    @Test fun retriesMakeFreshRequestsAndRetainEveryParagraph() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val calls = mutableMapOf<String, Int>()
        val player = Player()
        val controller = TtsController(generator { text ->
            val count = (calls[text] ?: 0) + 1
            calls[text] = count
            if (text == "First." && count == 1) error("Transient error")
        }, player, dispatcher)
        try {
            controller.setProvider(TTSProviderSetting.Gemini())
            controller.speak("First.\n\nSecond.")
            advanceUntilIdle()
            assertEquals(2, calls["First."])
            assertEquals(listOf(0, 1), player.enqueued)
        } finally { controller.dispose(); Dispatchers.resetMain() }
    }
    @Test fun persistentFailureStopsAtTheParagraphAndResumeRetriesIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var attempts = 0
        var failFirst = true
        val player = Player()
        val controller = TtsController(generator { text ->
            if (text == "First.") { attempts++; if (failFirst) error("Unavailable") }
        }, player, dispatcher)
        try {
            controller.setProvider(TTSProviderSetting.Gemini())
            controller.speak("First.\n\nSecond.")
            advanceUntilIdle()
            assertEquals(15, attempts)
            assertTrue(player.enqueued.isEmpty())
            assertNotNull(controller.error.value)
            failFirst = false
            controller.resume()
            advanceUntilIdle()
            assertEquals(16, attempts)
            assertEquals(listOf(0, 1), player.enqueued)
            assertNull(controller.error.value)
        } finally { controller.dispose(); Dispatchers.resetMain() }
    }
    @Test fun skippingPlaybackDoesNotDiscardAnUnsynthesizedParagraph() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gate = CompletableDeferred<Unit>()
        val player = Player()
        val controller = TtsController(generator { text -> if (text.first().digitToInt() >= 3) gate.await() }, player, dispatcher)
        try {
            controller.setProvider(TTSProviderSetting.Gemini())
            controller.speak((0..7).joinToString("\n\n") { "$it paragraph." })
            runCurrent()
            assertEquals(listOf(0, 1, 2), player.enqueued)
            controller.skipNext()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals((0..7).toList(), player.enqueued)
            assertEquals(1, player.skips)
        } finally { controller.dispose(); Dispatchers.resetMain() }
    }
    @Test fun prefetchDoesNotRunPastTheRequestedWindow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val gate = CompletableDeferred<Unit>()
        val requested = mutableListOf<String>()
        val controller = TtsController(generator { requested += it; gate.await() }, Player(), dispatcher)
        try {
            controller.setProvider(TTSProviderSetting.Gemini())
            controller.speak((0..19).joinToString("\n\n") { "$it paragraph." })
            runCurrent()
            assertEquals((0..4).map { "$it paragraph." }, requested)
        } finally { controller.dispose(); Dispatchers.resetMain() }
    }

}
