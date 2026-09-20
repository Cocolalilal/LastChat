package me.rerere.common.runtime.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LocalModelKind {
    @SerialName("llm")
    LLM,

    @SerialName("embedding")
    EMBEDDING,
}

@Serializable
enum class LocalAccelerator {
    @SerialName("auto")
    AUTO,

    @SerialName("cpu")
    CPU,

    @SerialName("gpu")
    GPU,
}

@Serializable
data class LocalModelConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
    val contextLength: Int? = null,
    val accelerator: LocalAccelerator = LocalAccelerator.AUTO,
)

@Serializable
data class LocalModelRuntimeFlags(
    val gpuCrashed: Boolean = false,
    val visionUnavailable: Boolean = false,
)

@Serializable
data class LocalModelDefaultConfig(
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 1.0f,
    val maxContextLength: Int? = null,
    val maxTokens: Int = 4096,
    val accelerators: List<String> = listOf("gpu", "cpu"),
    val visionAccelerator: String? = null,
) {
    val effectiveContextLength: Int
        get() = maxContextLength ?: maxTokens
}

@Serializable
data class LocalModelMetadata(
    val id: String,
    val name: String,
    val description: String = "",
    val kind: LocalModelKind = LocalModelKind.LLM,
    val hfRepo: String,
    val modelFile: String,
    val tokenizerFile: String? = null,
    val commitHash: String,
    val sizeInBytes: Long,
    val minDeviceMemoryInGb: Int,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsSpeculativeDecoding: Boolean = false,
    val embeddingDimension: Int? = null,
    val defaultConfig: LocalModelDefaultConfig = LocalModelDefaultConfig(),
    val updateInfo: String? = null,
    val requiresLicense: Boolean = false,
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$hfRepo/resolve/$commitHash/$modelFile"

    val tokenizerDownloadUrl: String?
        get() = tokenizerFile?.let { "https://huggingface.co/$hfRepo/resolve/$commitHash/$it" }

    val sizeInGb: Float
        get() = sizeInBytes / 1_000_000_000f
}

@Serializable
data class LocalModelCatalog(
    @SerialName("schema_version")
    val schemaVersion: Int = 1,
    val allowlistVersion: String = "",
    val models: List<LocalModelMetadata> = emptyList(),
)

@Serializable
data class InstalledLocalModel(
    val id: String,
    val displayName: String,
    val kind: LocalModelKind = LocalModelKind.LLM,
    val filePath: String,
    val tokenizerPath: String? = null,
    val commitHash: String,
    val sizeInBytes: Long,
    val minDeviceMemoryGb: Int = 6,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsSpeculativeDecoding: Boolean = false,
    val embeddingDimension: Int? = null,
    val defaultConfig: LocalModelDefaultConfig = LocalModelDefaultConfig(),
    val config: LocalModelConfig = LocalModelConfig(),
    val runtimeFlags: LocalModelRuntimeFlags = LocalModelRuntimeFlags(),
    val customIconUri: String? = null,
    val imported: Boolean = false,
) {
    val isEmbedding: Boolean get() = kind == LocalModelKind.EMBEDDING
}

@Serializable
enum class SherpaModelFamily {
    @SerialName("whisper")
    WHISPER,

    @SerialName("sense_voice")
    SENSE_VOICE,

    @SerialName("moonshine")
    MOONSHINE,

    @SerialName("online_transducer")
    ONLINE_TRANSDUCER,
}

@Serializable
data class SherpaModelConfig(
    val language: String = "",
    val numThreads: Int = 2,
    val useInverseTextNormalization: Boolean = true,
)

@Serializable
data class SherpaModelMetadata(
    val id: String,
    val name: String,
    val description: String = "",
    val family: SherpaModelFamily,
    val languages: List<String> = emptyList(),
    val streaming: Boolean = false,
    val onlineModelType: String = "zipformer2",
    val archiveUrl: String,
    val archiveSizeBytes: Long,
    val revision: String,
    val files: Map<String, String> = emptyMap(),
    val defaultConfig: SherpaModelConfig = SherpaModelConfig(),
)

@Serializable
data class SherpaModelCatalog(
    val schemaVersion: Int = 1,
    val models: List<SherpaModelMetadata> = emptyList(),
)

@Serializable
data class InstalledSherpaModel(
    val id: String,
    val displayName: String,
    val family: SherpaModelFamily,
    val languages: List<String> = emptyList(),
    val streaming: Boolean = false,
    val onlineModelType: String = "zipformer2",
    val directoryPath: String,
    val sizeInBytes: Long,
    val revision: String,
    val files: Map<String, String> = emptyMap(),
    val config: SherpaModelConfig = SherpaModelConfig(),
    val customIconUri: String? = null,
)
