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
 * We also keep a secondary safety check: if the model file size alone exceeds 80% of total RAM,
 * block the load (the model literally cannot fit regardless of what the allowlist says).
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
        @Suppress("UNUSED_PARAMETER") kvCacheTokens: Int = 4096,
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

        return MemoryCheck.Ok
    }

    /** Default minimum device RAM when the model's allowlist entry doesn't specify one. */
    private const val DEFAULT_MIN_DEVICE_MEMORY_GB = 6
}
