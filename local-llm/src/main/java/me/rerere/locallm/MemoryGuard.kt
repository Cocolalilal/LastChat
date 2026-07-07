package me.rerere.locallm

import android.app.ActivityManager
import android.content.Context

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
 * size plus runtime headroom resident; loading past that causes a hard native OOM/crash, so we block
 * with a helpful message instead.
 */
object MemoryGuard {

    /** Runtime headroom on top of the raw model file size (KV cache, activations, framework). */
    private const val HEADROOM_MB = 1024L

    fun deviceTotalRamGb(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        // Round to the nearest GB the way device specs are quoted (e.g. 7.6 GB total -> 8).
        return Math.round(info.totalMem / 1_000_000_000.0).toInt().coerceAtLeast(1)
    }

    fun check(context: Context, modelSizeBytes: Long): MemoryCheck {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val modelMb = modelSizeBytes / (1024 * 1024)
        val requiredMb = modelMb + HEADROOM_MB
        val availableMb = info.availMem / (1024 * 1024)

        return if (availableMb >= requiredMb) {
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
