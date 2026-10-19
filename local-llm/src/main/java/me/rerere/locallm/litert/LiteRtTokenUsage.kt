package me.rerere.locallm.litert

import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.ExperimentalApi
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.TokenUsage
import me.rerere.common.platform.PlatformLog

/** Optional statistics must not turn a completed reply into a generation failure. */
@OptIn(ExperimentalApi::class)
internal fun readLiteRtTokenUsage(readBenchmark: () -> BenchmarkInfo): TokenUsage? {
    val benchmark = try {
        readBenchmark()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PlatformLog.w("LiteRtProvider", "Token usage unavailable: ${e.message}")
        return null
    }
    val promptTokens = benchmark.lastPrefillTokenCount.coerceAtLeast(0)
    val completionTokens = benchmark.lastDecodeTokenCount.coerceAtLeast(0)
    return TokenUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = promptTokens + completionTokens,
    )
}
