package me.rerere.locallm

/**
 * Live state of the on-device inference engine, surfaced as activity pills in the UI.
 * (Download progress is tracked separately by the install layer.)
 */
sealed interface LocalRuntimeState {
    data object Idle : LocalRuntimeState

    /** The engine is loading a model into memory (can take several seconds). */
    data class LoadingModel(val modelId: String, val displayName: String) : LocalRuntimeState

    /** A model is loaded and ready to generate. */
    data class Ready(val modelId: String, val displayName: String, val accelerator: LocalAccelerator) :
        LocalRuntimeState

    /** Actively decoding a response. */
    data class Generating(val modelId: String, val displayName: String) : LocalRuntimeState

    /** The GPU backend failed/crashed previously and inference fell back to CPU. Transient notice. */
    data class SwitchedToCpu(val modelId: String, val displayName: String) : LocalRuntimeState

    /** Something went wrong loading or running the model. */
    data class Error(val modelId: String?, val message: String) : LocalRuntimeState
}
