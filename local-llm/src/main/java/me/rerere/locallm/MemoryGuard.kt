package me.rerere.locallm

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Outcome of a pre-load memory check. */
sealed interface MemoryCheck {
    data object Ok : MemoryCheck

    data class Insufficient(
        val requiredMb: Long,
        val modelMb: Long,
        val availableMb: Long,
    ) : MemoryCheck
}

/**
 * Guards against loading a model that won't fit in available RAM. LiteRT-LM roughly needs the model
 * file size plus runtime headroom (KV cache, activations, framework) resident; loading past that
 * causes a hard native OOM/crash, so we block with a helpful message instead.
 *
 * The [kvCacheTokens] parameter lets us estimate the KV cache cost — each token in the KV cache
 * consumes memory proportional to the model's layer count and hidden size.
 */
object MemoryGuard {

    /** Runtime headroom on top of the raw model file size (activations, framework, JIT). */
    private const val HEADROOM_MB = 1024L

    /**
     * Estimated memory per KV cache token in KB.
     *
     * For int4-quantized models with ~20–40 layers, each token in the KV cache costs roughly
     * 0.5–2 KB per layer. We use a conservative average of ~1 KB/token/layer × ~40 layers ≈ 40 KB
     * per token, then round up to 64 KB to be safe across model sizes.
     */
    private const val KV_CACHE_KB_PER_TOKEN = 64L

    fun deviceTotalRamGb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        // API 34+ has advertisedMem which reflects the marketed RAM (more accurate than totalMem).
        val memBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.advertisedMem
        } else {
            info.totalMem
        }
        return Math.round(memBytes / 1_000_000_000.0).toInt().coerceAtLeast(1)
    }

    fun check(context: Context, modelSizeBytes: Long, kvCacheTokens: Int = 4096): MemoryCheck {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val modelMb = modelSizeBytes / (1024 * 1024)
        val kvCacheMb = (kvCacheTokens.toLong() * KV_CACHE_KB_PER_TOKEN) / 1024
        val requiredMb = modelMb + kvCacheMb + HEADROOM_MB
        val availableMb = info.availMem / (1024 * 1024)
        val totalMb = info.totalMem / (1024 * 1024)

        // Maximum heap the OS is likely to grant the app.
        // With largeHeap=true, Android typically allows up to totalMem - ~1.5GB (OS overhead).
        // Without largeHeap, the cap is much lower (~256–512MB depending on device).
        val osOverheadMb = 1500L
        val maxAppRamMb = totalMb - osOverheadMb

        // Both conditions must be met: the device must have enough total RAM *and* enough
        // currently-available RAM. The old code used || (OR), which allowed loading when total RAM
        // was sufficient but available RAM was far too low — the OS would then kill the process
        // during model loading.
        return if (availableMb >= requiredMb && maxAppRamMb >= requiredMb) {
            MemoryCheck.Ok
        } else {
            MemoryCheck.Insufficient(
                requiredMb = requiredMb,
                modelMb = modelMb,
                availableMb = availableMb,
            )
        }
    }
}
