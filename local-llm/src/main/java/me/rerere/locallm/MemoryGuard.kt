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
 * Guards against loading a model that won't fit on the device. Uses the same strategy as Google's
 * Edge Gallery app: check the device's **total** RAM against the model's [minDeviceMemoryInGb]
 * threshold from the curated allowlist. Gallery does NOT check available RAM — it shows a warning
 * and lets the user proceed, because available RAM fluctuates with background processes and is not
 * a reliable indicator of whether a model will load successfully.
 *
 * We also keep process-safety checks for current memory pressure and a conservative working-set
 * estimate. Those checks turn a likely native OOM into a recoverable error while leaving ordinary
 * fluctuations alone when the model still has sufficient headroom.
 */
object MemoryGuard {

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

    /**
     * @param minDeviceMemoryGb The minimum device RAM in GB (from the model's allowlist entry).
     *   When null, defaults to [DEFAULT_MIN_DEVICE_MEMORY_GB].
     */
    fun check(
        context: Context,
        modelSizeBytes: Long,
        kvCacheTokens: Int = 4096,
        minDeviceMemoryGb: Int? = null,
    ): MemoryCheck {
        val totalRamGb = deviceTotalRamGb(context)
        val requiredGb = minDeviceMemoryGb ?: DEFAULT_MIN_DEVICE_MEMORY_GB

        // Primary check: device total RAM must meet the model's minimum (same as Edge Gallery).
        if (totalRamGb < requiredGb) {
            return MemoryCheck.Insufficient(
                requiredMb = requiredGb.toLong() * 1024,
                modelMb = modelSizeBytes / (1024 * 1024),
                availableMb = totalRamGb.toLong() * 1024,
            )
        }

        // Secondary safety: if the model file alone is >80% of total RAM, it physically cannot fit.
        val totalRamBytes = totalRamGb.toLong() * 1_000_000_000L
        if (modelSizeBytes > totalRamBytes * 8 / 10) {
            return MemoryCheck.Insufficient(
                requiredMb = modelSizeBytes / (1024 * 1024),
                modelMb = modelSizeBytes / (1024 * 1024),
                availableMb = totalRamGb.toLong() * 1024,
            )
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val reserveBytes = maxOf(MIN_SYSTEM_RESERVE_BYTES, totalRamBytes * SYSTEM_RESERVE_PERCENT / 100)
        val estimatedWorkingSet = modelSizeBytes * MODEL_OVERHEAD_PERCENT / 100 +
            kvCacheTokens.toLong() * ESTIMATED_KV_BYTES_PER_TOKEN + reserveBytes
        if (info.lowMemory || info.availMem < estimatedWorkingSet) {
            return MemoryCheck.Insufficient(
                requiredMb = estimatedWorkingSet / (1024 * 1024),
                modelMb = modelSizeBytes / (1024 * 1024),
                availableMb = info.availMem / (1024 * 1024),
            )
        }

        return MemoryCheck.Ok
    }

    /** Conservative automatic context ceiling for phones that meet a model's basic allowlist. */
    fun safeContextTokenCap(totalRamGb: Int): Int = when {
        totalRamGb <= 6 -> 4_096
        totalRamGb <= 8 -> 8_192
        totalRamGb <= 12 -> 16_384
        else -> 32_768
    }

    /** Default minimum device RAM when the model's allowlist entry doesn't specify one. */
    private const val DEFAULT_MIN_DEVICE_MEMORY_GB = 6
    private const val MODEL_OVERHEAD_PERCENT = 110L
    private const val SYSTEM_RESERVE_PERCENT = 15L
    private const val ESTIMATED_KV_BYTES_PER_TOKEN = 64L * 1024L
    private const val MIN_SYSTEM_RESERVE_BYTES = 512L * 1024L * 1024L
}
