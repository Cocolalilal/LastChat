package me.rerere.common.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OnDeviceRuntimeTest {
    @Test
    fun unavailableLlmShimIsHonest() = runBlocking {
        val runtime = UnavailableOnDeviceLlmRuntime()
        assertFalse(runtime.available)
        assertEquals(0, runtime.listModels().size)
        assertEquals(null, runtime.embed("model", "text"))
        runCatching { runtime.generateText("id", "hi") }
            .onFailure { assertTrue(it.message.orEmpty().contains("LiteRT-LM")) }
            .onSuccess { error("unavailable LLM should throw") }
    }

    @Test
    fun unavailableWorkspaceShimIsHonest() = runBlocking {
        val runtime = UnavailableOnDeviceWorkspaceRuntime()
        assertFalse(runtime.available)
        assertEquals(0, runtime.listFiles("/").size)
    }
}
