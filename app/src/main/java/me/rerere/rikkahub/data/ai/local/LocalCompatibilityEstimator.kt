package me.rerere.rikkahub.data.ai.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

class LocalCompatibilityEstimator(
    private val context: Context?,
) {
    fun currentDeviceProfile(): LocalDeviceProfile {
        val context = requireNotNull(context) {
            "A Context is required to inspect the current device profile."
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val statFs = StatFs(context.filesDir.absolutePath)
        return LocalDeviceProfile(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            freeStorageBytes = statFs.availableBytes,
            lowRamDevice = activityManager.isLowRamDevice,
            gpuDelegateAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            isCharging = false,
            isDeviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isDeviceIdleMode
            } else {
                false
            },
        )
    }

    fun estimate(
        entry: LocalModelCatalogEntry,
        profile: LocalDeviceProfile = currentDeviceProfile(),
    ): DeviceCompatibility {
        val reasons = mutableListOf<String>()
        var result = CompatibilityResult.Supported

        if (profile.sdkInt < entry.minSdk) {
            reasons += "Requires Android ${entry.minSdk}+."
            result = CompatibilityResult.Unsupported
        }

        if (profile.supportedAbis.none { abi -> abi in entry.supportedAbis }) {
            reasons += "Unsupported CPU architecture."
            result = CompatibilityResult.Unsupported
        }

        val storageRequirement = entry.estimatedDownloadBytes + entry.estimatedInstalledBytes + STORAGE_HEADROOM_BYTES
        if (profile.freeStorageBytes < storageRequirement) {
            reasons += "Not enough free storage."
            result = CompatibilityResult.Unsupported
        }

        if (profile.totalRamBytes < entry.minimumRamBytes) {
            reasons += "Not enough device RAM."
            result = CompatibilityResult.Unsupported
        }

        if (result != CompatibilityResult.Unsupported) {
            if (profile.lowRamDevice) {
                reasons += "Android reports this as a low-RAM device."
                result = CompatibilityResult.Tight
            }

            if (profile.totalRamBytes < entry.recommendedRamBytes) {
                reasons += "Device RAM is below the recommended tier."
                result = CompatibilityResult.Tight
            }

            if (profile.availableRamBytes < entry.minimumRamBytes / 2) {
                reasons += "Available memory is currently tight."
                result = CompatibilityResult.Tight
            }

            if (profile.freeStorageBytes < storageRequirement + STORAGE_WARNING_HEADROOM_BYTES) {
                reasons += "Storage headroom is limited."
                result = CompatibilityResult.Tight
            }
        }

        return DeviceCompatibility(
            result = result,
            reasons = reasons.distinct(),
        )
    }

    fun canRunInBackground(
        entry: LocalModelCatalogEntry,
        profile: LocalDeviceProfile = currentDeviceProfile(),
    ): DeviceCompatibility {
        val base = estimate(entry, profile)
        if (base.result == CompatibilityResult.Unsupported) {
            return base
        }

        val reasons = base.reasons.toMutableList()
        var result = base.result
        if (!entry.safeForBackground) {
            reasons += "This model is not marked safe for background inference."
            result = CompatibilityResult.Unsupported
        } else if (!profile.isCharging && !profile.isDeviceIdle) {
            reasons += "Background local inference is limited to idle or charging conditions."
            result = CompatibilityResult.Tight
        }

        return DeviceCompatibility(
            result = result,
            reasons = reasons.distinct(),
        )
    }

    companion object {
        private const val STORAGE_HEADROOM_BYTES = 512L * 1024 * 1024
        private const val STORAGE_WARNING_HEADROOM_BYTES = 256L * 1024 * 1024
    }
}
