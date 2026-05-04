package me.rerere.rikkahub.data.ai.local

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.SettingsStore

@Serializable
data class DevicePerformanceProfile(
    val averageTokensPerSecond: Float = -1f,
    val sampleCount: Int = 0,
)

class LocalPerformanceEstimator(
    private val settingsStore: SettingsStore,
) {
    suspend fun recordStats(stats: LocalRuntimeStats) {
        val completionTokens = stats.completionTokens ?: return
        val durationMs = stats.durationMs ?: return
        if (durationMs <= 0 || completionTokens <= 0) return

        val tokensPerSecond = (completionTokens.toFloat() / durationMs) * 1000f
        
        settingsStore.update { current ->
            val profile = current.devicePerformanceProfile ?: DevicePerformanceProfile()
            val newSampleCount = profile.sampleCount + 1
            val newAverage = if (profile.averageTokensPerSecond < 0) {
                tokensPerSecond
            } else {
                // Moving average favoring recent
                (profile.averageTokensPerSecond * 0.8f) + (tokensPerSecond * 0.2f)
            }
            
            current.copy(
                devicePerformanceProfile = profile.copy(
                    averageTokensPerSecond = newAverage,
                    sampleCount = newSampleCount
                )
            )
        }
    }

    fun getProfile(): DevicePerformanceProfile {
        return settingsStore.settingsFlow.value.devicePerformanceProfile ?: DevicePerformanceProfile()
    }

    fun estimatePerformanceRisk(entry: LocalModelCatalogEntry): Boolean {
        val profile = getProfile()
        if (profile.sampleCount < 2) return false // Not enough data
        
        // If average TPS is extremely low (e.g., < 2.0) on previous models, 
        // we flag heavy models (e.g., >= 2GB) as high risk.
        if (profile.averageTokensPerSecond in 0.1f..3.0f && entry.estimatedInstalledBytes > 1024L * 1024 * 1024 * 2) {
            return true
        }
        
        return false
    }
}
