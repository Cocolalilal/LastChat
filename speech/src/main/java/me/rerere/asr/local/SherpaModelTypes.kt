package me.rerere.asr.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val archiveUrl: String,
    val archiveSizeBytes: Long,
    val revision: String,
    /** Runtime role to path relative to the archive's top-level model directory. */
    val files: Map<String, String>,
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
    val directoryPath: String,
    val sizeInBytes: Long,
    val revision: String,
    val files: Map<String, String>,
    val config: SherpaModelConfig = SherpaModelConfig(),
    val customIconUri: String? = null,
) {
    fun file(role: String): String = requireNotNull(files[role]) {
        "Missing sherpa model file role '$role' for $id"
    }

    fun withCatalogMetadata(meta: SherpaModelMetadata): InstalledSherpaModel = copy(
        displayName = displayName.ifBlank { meta.name },
        family = meta.family,
        languages = meta.languages,
        streaming = meta.streaming,
        files = meta.files.mapValues { (_, relative) ->
            java.io.File(directoryPath, relative).absolutePath
        },
    )
}

object SherpaFileRole {
    const val TOKENS = "tokens"
    const val ENCODER = "encoder"
    const val DECODER = "decoder"
    const val JOINER = "joiner"
    const val MODEL = "model"
}
