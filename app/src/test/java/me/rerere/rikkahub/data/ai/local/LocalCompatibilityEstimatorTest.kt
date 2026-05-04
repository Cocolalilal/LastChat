package me.rerere.rikkahub.data.ai.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCompatibilityEstimatorTest {
    private val estimator = LocalCompatibilityEstimator(context = null)
    private val e2b = LocalModelCatalog.getById("gemma-4-e2b-it-litert")
        ?: error("Missing E2B catalog entry")

    @Test
    fun `estimate returns unsupported when abi does not match`() {
        val compatibility = estimator.estimate(
            entry = e2b,
            profile = baseProfile.copy(supportedAbis = listOf("x86"))
        )

        assertEquals(CompatibilityResult.Unsupported, compatibility.result)
        assertTrue(compatibility.reasons.any { it.contains("architecture", ignoreCase = true) })
    }

    @Test
    fun `estimate returns unsupported when storage is too low`() {
        val compatibility = estimator.estimate(
            entry = e2b,
            profile = baseProfile.copy(freeStorageBytes = 1L)
        )

        assertEquals(CompatibilityResult.Unsupported, compatibility.result)
        assertTrue(compatibility.reasons.any { it.contains("storage", ignoreCase = true) })
    }

    @Test
    fun `estimate returns tight when ram is below recommended but above minimum`() {
        val compatibility = estimator.estimate(
            entry = e2b,
            profile = baseProfile.copy(
                totalRamBytes = e2b.minimumRamBytes + (512L * 1024 * 1024),
                availableRamBytes = e2b.minimumRamBytes,
            )
        )

        assertEquals(CompatibilityResult.Tight, compatibility.result)
        assertTrue(compatibility.reasons.any { it.contains("recommended", ignoreCase = true) })
    }

    @Test
    fun `background inference requires safe entry and relaxed device state`() {
        val unsupported = estimator.canRunInBackground(
            entry = e2b,
            profile = baseProfile
        )
        assertEquals(CompatibilityResult.Unsupported, unsupported.result)

        val safeEntry = e2b.copy(safeForBackground = true)
        val tight = estimator.canRunInBackground(
            entry = safeEntry,
            profile = baseProfile.copy(isCharging = false, isDeviceIdle = false)
        )
        assertEquals(CompatibilityResult.Tight, tight.result)

        val supported = estimator.canRunInBackground(
            entry = safeEntry,
            profile = baseProfile.copy(isCharging = true)
        )
        assertEquals(CompatibilityResult.Supported, supported.result)
    }

    private val baseProfile = LocalDeviceProfile(
        supportedAbis = listOf("arm64-v8a"),
        sdkInt = e2b.minSdk,
        totalRamBytes = e2b.recommendedRamBytes + (2L * 1024 * 1024 * 1024),
        availableRamBytes = e2b.recommendedRamBytes,
        freeStorageBytes = e2b.estimatedDownloadBytes + e2b.estimatedInstalledBytes + (2L * 1024 * 1024 * 1024),
        lowRamDevice = false,
        gpuDelegateAvailable = true,
        isCharging = true,
        isDeviceIdle = true,
    )
}
