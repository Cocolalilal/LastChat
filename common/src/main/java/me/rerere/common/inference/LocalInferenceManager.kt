package me.rerere.common.inference

import kotlinx.coroutines.flow.StateFlow

enum class LocalInferenceWorkload {
    CHAT,
    EMBEDDING,
    SPEECH,
}

data class LocalInferenceRequest(
    val id: Long,
    val workload: LocalInferenceWorkload,
    val modelId: String,
)

data class LocalInferenceQueueState(
    val active: LocalInferenceRequest? = null,
    val waiting: List<LocalInferenceRequest> = emptyList(),
)

/**
 * Process-wide admission control for native on-device inference runtimes.
 *
 * Implementations must hold the lease for the complete native operation, not merely while loading
 * a model. This prevents a model switch from closing an engine that another conversation is using.
 */
interface LocalInferenceManager {
    val queueState: StateFlow<LocalInferenceQueueState>

    suspend fun <T> withLease(
        workload: LocalInferenceWorkload,
        modelId: String,
        block: suspend () -> T,
    ): T

    /** Queues eviction behind any active operation. */
    fun requestEviction(workload: LocalInferenceWorkload? = null)
}
