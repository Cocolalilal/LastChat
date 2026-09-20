package me.rerere.common.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val failure = assertFailsWith<IllegalStateException> {
            runtime.generateText("id", "hi")
        }
        assertTrue(failure.message.orEmpty().contains("LiteRT-LM"))
    }

    @Test
    fun unavailableWorkspaceShimIsHonest() = runBlocking {
        val runtime = UnavailableOnDeviceWorkspaceRuntime()
        assertFalse(runtime.available)
        assertEquals(0, runtime.listFiles("/").size)
    }
}
