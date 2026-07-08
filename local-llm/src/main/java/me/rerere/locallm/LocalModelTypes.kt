package me.rerere.locallm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What kind of on-device model this is. [LLM] models run text generation through the LiteRT-LM
 * [me.rerere.locallm.LiteRtRuntime] engine; [EMBEDDING] models produce text embeddings through the
 * AI Edge RAG [me.rerere.locallm.LiteRtEmbedder] (a separate runtime — the generation engine has no
 * embedding API).
 */
@Serializable
enum class LocalModelKind {
    @SerialName("llm")
    LLM,

    @SerialName("embedding")
    EMBEDDING,
}

/**
 * Which on-device accelerator to run inference on.
 *
 * [AUTO] resolves at load time from the model's curated accelerator preference (GPU-first when the
 * model advertises GPU support), automatically falling back to CPU if the GPU backend has crashed
 * before on this device (see [LocalModelRuntimeFlags.gpuCrashed]).
 */
@Serializable
enum class LocalAccelerator {
    @SerialName("auto")
    AUTO,

    @SerialName("cpu")
    CPU,

    @SerialName("gpu")
    GPU,
}

/**
 * Per-model, user-tunable runtime configuration. All fields are nullable "overrides" — when null the
 * curated [LocalModelMetadata.defaultConfig] value is used. This keeps a downloaded model working with
 * sensible official defaults until the user deliberately changes something.
 */
@Serializable
data class LocalModelConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    /** Max tokens to generate (decode budget). */
    val maxTokens: Int? = null,
    /** Total context window (prefill + decode). Overrides the default KV cache size when set; capped at the model's maxContextLength. */
    val contextLength: Int? = null,
    val accelerator: LocalAccelerator = LocalAccelerator.AUTO,
)

/**
 * Non-user-facing runtime flags persisted per installed model — e.g. remembering that the GPU backend
 * crashed so [LocalAccelerator.AUTO] transparently drops to CPU next time.
 */
@Serializable
data class LocalModelRuntimeFlags(
    val gpuCrashed: Boolean = false,
    /** Vision failed to initialize on this device; run the model text-only. */
    val visionUnavailable: Boolean = false,
)

/**
 * Curated default generation config for a model, sourced from Google AI Edge Gallery's
 * `model_allowlist` `defaultConfig`.
 */
@Serializable
data class LocalModelDefaultConfig(
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    /** Total context window the model file supports (KV cache ceiling). Null → derive from [maxTokens]. */
    val maxContextLength: Int? = null,
    /** Curated max output tokens. */
    val maxTokens: Int = 4096,
    /** Ordered accelerator preference, e.g. ["gpu", "cpu"]. First entry is tried first under AUTO. */
    val accelerators: List<String> = listOf("gpu", "cpu"),
    /** Preferred accelerator for the vision encoder. */
    val visionAccelerator: String? = null,
) {
    /** Effective context window: explicit ceiling if given, otherwise the decode budget. */
    val effectiveContextLength: Int
        get() = maxContextLength ?: maxTokens
}

/**
 * A curated / importable on-device model description. Mirrors one entry of the Gallery allowlist plus
 * the fields LastChat needs to download, run and update it.
 */
@Serializable
data class LocalModelMetadata(
    /** Stable identifier used as the [me.rerere.ai.provider.Model.modelId] for the local provider. */
    val id: String,
    val name: String,
    val description: String = "",
    val kind: LocalModelKind = LocalModelKind.LLM,
    /** HuggingFace repo, e.g. "litert-community/Qwen2.5-1.5B-Instruct". */
    val hfRepo: String,
    /** The .litertlm (LLM) or .tflite (embedding) file name inside the repo. */
    val modelFile: String,
    /**
     * For [LocalModelKind.EMBEDDING] models: the SentencePiece tokenizer file name in the same repo
     * revision (downloaded alongside [modelFile]). Null for LLMs, whose tokenizer is bundled in the
     * .litertlm.
     */
    val tokenizerFile: String? = null,
    /** HuggingFace commit hash pinning the exact file revision (also drives update detection). */
    val commitHash: String,
    val sizeInBytes: Long,
    val minDeviceMemoryInGb: Int,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsSpeculativeDecoding: Boolean = false,
    /** Output vector dimension for [LocalModelKind.EMBEDDING] models (informational). */
    val embeddingDimension: Int? = null,
    val defaultConfig: LocalModelDefaultConfig = LocalModelDefaultConfig(),
    val updateInfo: String? = null,
    /** Whether this model is gated on HuggingFace and requires a token + license acceptance. */
    val requiresLicense: Boolean = false,
) {
    /** Direct, resumable download URL for the pinned revision (public litert-community mirror). */
    val downloadUrl: String
        get() = "https://huggingface.co/$hfRepo/resolve/$commitHash/$modelFile"

    /** Download URL of the [tokenizerFile] (embedding models only), pinned to the same revision. */
    val tokenizerDownloadUrl: String?
        get() = tokenizerFile?.let { "https://huggingface.co/$hfRepo/resolve/$commitHash/$it" }

    val sizeInGb: Float
        get() = sizeInBytes / 1_000_000_000f
}

/** Root of the bundled/remote curated catalog. */
@Serializable
data class LocalModelCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    /** Allowlist version this snapshot was generated from (e.g. "1_0_15"). */
    val allowlistVersion: String = "",
    val models: List<LocalModelMetadata> = emptyList(),
)

/**
 * A model that is downloaded and available on this device. Persisted by [LocalModelStore].
 */
@Serializable
data class InstalledLocalModel(
    val id: String,
    val displayName: String,
    val kind: LocalModelKind = LocalModelKind.LLM,
    /** Absolute path of the .litertlm (LLM) or .tflite (embedding) file on disk. */
    val filePath: String,
    /** Absolute path of the SentencePiece tokenizer on disk (embedding models only). */
    val tokenizerPath: String? = null,
    /** Commit hash of the installed file — compared against the catalog to detect updates. */
    val commitHash: String,
    val sizeInBytes: Long,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsSpeculativeDecoding: Boolean = false,
    /** Output vector dimension for embedding models (informational). */
    val embeddingDimension: Int? = null,
    val defaultConfig: LocalModelDefaultConfig = LocalModelDefaultConfig(),
    val config: LocalModelConfig = LocalModelConfig(),
    val runtimeFlags: LocalModelRuntimeFlags = LocalModelRuntimeFlags(),
    /** Optional custom icon uri chosen by the user. */
    val customIconUri: String? = null,
    /** True for models installed from a pasted URL rather than the curated catalog. */
    val imported: Boolean = false,
) {
    val isEmbedding: Boolean get() = kind == LocalModelKind.EMBEDDING
}
