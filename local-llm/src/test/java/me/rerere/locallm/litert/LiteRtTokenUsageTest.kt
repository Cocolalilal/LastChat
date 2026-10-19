package me.rerere.locallm.litert

import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.LiteRtLmJniException
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

@OptIn(ExperimentalApi::class)
class LiteRtTokenUsageTest {
    @Test
    fun `benchmark failure leaves usage absent instead of failing the reply`() {
        val usage = readLiteRtTokenUsage {
            throw LiteRtLmJniException("Failed to get benchmark info: INTERNAL: Benchmark is not enabled.")
        }

        assertNull(usage)
    }

    @Test
    fun `available benchmark preserves token counts`() {
        assertEquals(
            TokenUsage(promptTokens = 24, completionTokens = 12, totalTokens = 36),
            readLiteRtTokenUsage { benchmark(promptTokens = 24, completionTokens = 12) },
        )
    }

    @Test
    fun `unknown counts do not reduce the total`() {
        assertEquals(
            TokenUsage(promptTokens = 0, completionTokens = 12, totalTokens = 12),
            readLiteRtTokenUsage { benchmark(promptTokens = -1, completionTokens = 12) },
        )
    }

    @Test
    fun `cancellation is propagated`() {
        val cancellation = CancellationException("Cancelled")
        val thrown = assertThrows(CancellationException::class.java) {
            readLiteRtTokenUsage { throw cancellation }
        }

        assertSame(cancellation, thrown)
    }

    private fun benchmark(promptTokens: Int, completionTokens: Int) = BenchmarkInfo(
        initTimeInSecond = 0.0,
        timeToFirstTokenInSecond = 0.0,
        lastPrefillTokenCount = promptTokens,
        lastDecodeTokenCount = completionTokens,
        lastPrefillTokensPerSecond = 0.0,
        lastDecodeTokensPerSecond = 0.0,
    )
}
