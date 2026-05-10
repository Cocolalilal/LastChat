package me.rerere.rikkahub.data.ai.local

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

const val LOCAL_PROVIDER_NAME = "Models"
const val LOCAL_PROVIDER_TYPE = "local"
val LOCAL_PROVIDER_ID: Uuid = Uuid.parse("84d71641-7af0-4cf0-970f-a2fd36491f79")

private const val GIB = 1024L * 1024L * 1024L
private const val MIB = 1024L * 1024L

@Serializable
enum class LocalRuntimeBackend {
    LITERT,
}

@Serializable
enum class LocalModelStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    INSTALLING,
    READY,
    FAILED,
    INCOMPATIBLE,
    CANCELED,
}

@Serializable
enum class LocalModelProvenance {
    CURATED,
    IMPORTED,
}

@Serializable
enum class LocalModelDownloadAccess {
    PUBLIC,
    AUTH_REQUIRED,
    IMPORT_ONLY,
}

@Serializable
enum class LocalPackageFormat(
    val extensions: List<String>,
) {
    LITERT_LM(listOf(".litertlm")),
    TFLITE(listOf(".tflite")),
    UNKNOWN(emptyList()),
}

@Serializable
enum class CompatibilityResult {
    Supported,
    Tight,
    Unsupported,
}

@Serializable
data class DeviceCompatibility(
    val result: CompatibilityResult,
    val reasons: List<String> = emptyList(),
) {
    val canDownload: Boolean
        get() = true

    val canRunInference: Boolean
        get() = true
}

data class LocalModelCatalogState(
    val entry: LocalModelCatalogEntry,
    val install: LocalModelInstallEntity?,
    val compatibility: DeviceCompatibility,
) {
    val status: LocalModelStatus
        get() = install?.status?.let { value -> runCatching { LocalModelStatus.valueOf(value) }.getOrNull() }
            ?: if (compatibility.result == CompatibilityResult.Unsupported) {
                LocalModelStatus.INCOMPATIBLE
            } else {
                LocalModelStatus.NOT_DOWNLOADED
            }
}

@Serializable
data class LocalDeviceProfile(
    val supportedAbis: List<String>,
    val sdkInt: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val lowRamDevice: Boolean,
    val gpuDelegateAvailable: Boolean = false,
    val isCharging: Boolean = false,
    val isDeviceIdle: Boolean = false,
)

@Serializable
data class LocalModelDownloadFile(
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

@Serializable
data class LocalModelCatalogEntry(
    val id: String,
    val modelUuid: Uuid,
    val repoId: String = "",
    val revision: String = "main",
    val modelId: String,
    val displayName: String,
    val description: String,
    val runtimeBackend: LocalRuntimeBackend = LocalRuntimeBackend.LITERT,
    val packageFormat: LocalPackageFormat = LocalPackageFormat.LITERT_LM,
    val provenance: LocalModelProvenance = LocalModelProvenance.CURATED,
    val downloadAccess: LocalModelDownloadAccess = LocalModelDownloadAccess.PUBLIC,
    val type: ModelType = ModelType.CHAT,
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val supportedAbis: List<String> = listOf("arm64-v8a"),
    val minSdk: Int = 28,
    val minimumRamBytes: Long,
    val recommendedRamBytes: Long,
    val estimatedDownloadBytes: Long,
    val estimatedInstalledBytes: Long,
    val safeForBackground: Boolean = false,
    val files: List<LocalModelDownloadFile> = emptyList(),
    val aliases: List<String> = emptyList(),
    val featureTags: List<String> = emptyList(),
    val customIconUri: String? = null,
    val maxContextTokens: Int? = null,
) {
    val primaryFile: LocalModelDownloadFile?
        get() = files.firstOrNull()

    fun buildResolveUrl(file: LocalModelDownloadFile): String {
        return "https://huggingface.co/$repoId/resolve/$revision/${file.relativePath}?download=true"
    }

    fun toModel(): Model {
        return Model(
            id = modelUuid,
            modelId = modelId,
            displayName = displayName,
            type = type,
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            abilities = abilities,
            customIconUri = customIconUri,
        )
    }

    fun withResolvedFiles(downloadFiles: List<LocalModelDownloadFile>): LocalModelCatalogEntry {
        return copy(
            files = downloadFiles,
            estimatedDownloadBytes = downloadFiles.sumOf { it.sizeBytes },
            estimatedInstalledBytes = downloadFiles.sumOf { it.sizeBytes },
        )
    }

    fun matchesImportFileName(fileName: String): Boolean {
        return importMatchScore(fileName) > 0
    }

    fun importMatchScore(fileName: String): Int {
        val normalizedFileName = fileName.normalizeLocalLookupKey()
        if (normalizedFileName.isBlank()) return 0
        val candidates = buildList {
            add(id)
            add(modelId)
            add(displayName)
            add(repoId.substringAfterLast('/'))
            addAll(aliases)
        }
        return candidates.maxOfOrNull { candidate ->
            val normalizedCandidate = candidate.normalizeLocalLookupKey()
            if (
                normalizedCandidate.isNotBlank() &&
                (normalizedFileName.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedFileName))
            ) {
                normalizedCandidate.length
            } else {
                0
            }
        } ?: 0
    }

    fun serialize(): String = JsonInstant.encodeToString(this)
}

object LocalModelCatalog {
    val entries: List<LocalModelCatalogEntry> = listOf(
        LocalModelCatalogEntry(
            id = "gemma-4-e2b-it-litert",
            modelUuid = Uuid.parse("f945b6fe-1e08-4307-b901-315a253e2870"),
            repoId = "litert-community/gemma-4-E2B-it-litert-lm",
            modelId = "gemma-4-E2B-it",
            displayName = "Gemma 4 E2B",
            description = "Compact multimodal chat model with vision, audio and reasoning.",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 4L * GIB,
            recommendedRamBytes = 6L * GIB,
            estimatedDownloadBytes = 2_583L * MIB,
            estimatedInstalledBytes = 2_583L * MIB,
            files = listOf(
                LocalModelDownloadFile(
                    relativePath = "gemma-4-E2B-it.litertlm",
                    sizeBytes = 2_583L * MIB,
                )
            ),
            aliases = listOf("gemma-4-e2b", "e2b"),
            featureTags = listOf("vision", "audio", "multimodal", "reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "gemma-4-e4b-it-litert",
            modelUuid = Uuid.parse("cc876e1b-343a-4bdc-8483-f9e67897af2b"),
            repoId = "litert-community/gemma-4-E4B-it-litert-lm",
            modelId = "gemma-4-E4B-it",
            displayName = "Gemma 4 E4B",
            description = "Larger multimodal chat model with vision, audio and reasoning.",
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 8L * GIB,
            recommendedRamBytes = 12L * GIB,
            estimatedDownloadBytes = 3_654L * MIB,
            estimatedInstalledBytes = 3_654L * MIB,
            files = listOf(
                LocalModelDownloadFile(
                    relativePath = "gemma-4-E4B-it.litertlm",
                    sizeBytes = 3_654L * MIB,
                )
            ),
            aliases = listOf("gemma-4-e4b", "e4b"),
            featureTags = listOf("vision", "audio", "multimodal", "reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "gemma-3n-e2b-it-litert",
            modelUuid = Uuid.parse("801b95dc-2f1c-4db7-a6b8-7cd651fb0f59"),
            repoId = "google/gemma-3n-E2B-it-litert-lm",
            modelId = "gemma-3n-E2B-it",
            displayName = "Gemma 3n E2B",
            description = "Compact multimodal model with vision and audio. Requires Hugging Face access.",
            downloadAccess = LocalModelDownloadAccess.AUTH_REQUIRED,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 4L * GIB,
            recommendedRamBytes = 6L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            aliases = listOf("gemma-3n-e2b", "gemma3ne2b"),
            featureTags = listOf("vision", "audio", "multimodal", "reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "gemma-3n-e4b-it-litert",
            modelUuid = Uuid.parse("21a28689-2e66-460d-aeb2-4286aace0bb1"),
            repoId = "google/gemma-3n-E4B-it-litert-lm",
            modelId = "gemma-3n-E4B-it",
            displayName = "Gemma 3n E4B",
            description = "Stronger multimodal model with vision and audio. Requires Hugging Face access.",
            downloadAccess = LocalModelDownloadAccess.AUTH_REQUIRED,
            inputModalities = listOf(Modality.TEXT, Modality.IMAGE),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 6L * GIB,
            recommendedRamBytes = 8L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            aliases = listOf("gemma-3n-e4b", "gemma3ne4b"),
            featureTags = listOf("vision", "audio", "multimodal", "reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "gemma-3-1b-it-litert",
            modelUuid = Uuid.parse("a3b1c0de-1f2a-4b3c-8d4e-5f6a7b8c9d0e"),
            repoId = "litert-community/Gemma3-1B-IT",
            modelId = "Gemma3-1B-IT",
            displayName = "Gemma 3 1B",
            description = "Small text-only local chat model. Requires Hugging Face access.",
            downloadAccess = LocalModelDownloadAccess.AUTH_REQUIRED,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 2L * GIB,
            recommendedRamBytes = 4L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            safeForBackground = true,
            aliases = listOf("gemma-3-1b", "gemma31b"),
            featureTags = listOf("reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "qwen2.5-1.5b-instruct-litert",
            modelUuid = Uuid.parse("1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d"),
            repoId = "litert-community/Qwen2.5-1.5B-Instruct",
            modelId = "Qwen2.5-1.5B-Instruct",
            displayName = "Qwen2.5 1.5B Instruct",
            description = "Fast instruction-tuned text model.",
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 2L * GIB,
            recommendedRamBytes = 4L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            safeForBackground = true,
            aliases = listOf("qwen", "qwen2.5"),
            featureTags = listOf("reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "deepseek-r1-distill-qwen-1.5b-litert",
            modelUuid = Uuid.parse("7d8e9f0a-1b2c-4d3e-af6b-7c8d9e0f1a2b"),
            repoId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            modelId = "DeepSeek-R1-Distill-Qwen-1.5B",
            displayName = "DeepSeek R1 Distill Qwen 1.5B",
            description = "Reasoning-focused distilled model.",
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            abilities = listOf(ModelAbility.REASONING, ModelAbility.TOOL),
            minimumRamBytes = 2L * GIB,
            recommendedRamBytes = 4L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            safeForBackground = true,
            aliases = listOf("deepseek", "deepseek-r1", "r1"),
            featureTags = listOf("reasoning", "tools"),
        ),
        LocalModelCatalogEntry(
            id = "embeddinggemma-300m-litert",
            modelUuid = Uuid.parse("c159d4ca-4253-4786-a8dd-93fc04c7c0f9"),
            repoId = "litert-community/embeddinggemma-300m",
            modelId = "embeddinggemma-300m",
            displayName = "EmbeddingGemma 300M",
            description = "Local embedding model for RAG. Requires Hugging Face access.",
            downloadAccess = LocalModelDownloadAccess.AUTH_REQUIRED,
            type = ModelType.EMBEDDING,
            inputModalities = listOf(Modality.TEXT),
            outputModalities = listOf(Modality.TEXT),
            minimumRamBytes = 2L * GIB,
            recommendedRamBytes = 4L * GIB,
            estimatedDownloadBytes = 0L,
            estimatedInstalledBytes = 0L,
            safeForBackground = true,
            aliases = listOf("embeddinggemma", "embedgemma"),
            featureTags = listOf("embeddings"),
        ),
    )

    fun getById(id: String): LocalModelCatalogEntry? = entries.find { it.id == id }

    fun findByImportFileName(fileName: String): LocalModelCatalogEntry? {
        return entries
            .map { entry -> entry to entry.importMatchScore(fileName) }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }
}

fun LocalModelInstallEntity.toCatalogEntryOrNull(): LocalModelCatalogEntry? {
    entryJson.takeIf { it.isNotBlank() }?.let { encoded ->
        return runCatching { JsonInstant.decodeFromString<LocalModelCatalogEntry>(encoded) }.getOrNull()
    }

    return LocalModelCatalog.getById(catalogId)?.copy(
        downloadAccess = runCatching { LocalModelDownloadAccess.valueOf(downloadAccess) }.getOrDefault(LocalModelDownloadAccess.PUBLIC),
        provenance = runCatching { LocalModelProvenance.valueOf(provenance) }.getOrDefault(LocalModelProvenance.CURATED),
    )
}

fun inferImportedCatalogEntry(
    fileName: String,
    fileSizeBytes: Long,
): LocalModelCatalogEntry {
    val curated = LocalModelCatalog.findByImportFileName(fileName)
    if (curated != null) {
        return curated.copy(
            provenance = LocalModelProvenance.IMPORTED,
            estimatedDownloadBytes = fileSizeBytes,
            estimatedInstalledBytes = fileSizeBytes,
        )
    }

    val baseName = fileName.substringBeforeLast('.').ifBlank { "imported-local-model" }
    val normalizedName = baseName.normalizeLocalLookupKey()
    val packageFormat = detectLocalPackageFormat(fileName)
    val runtimeBackend = LocalRuntimeBackend.LITERT
    val detectedType = if ("embedding" in normalizedName) ModelType.EMBEDDING else ModelType.CHAT
    val inputModalities = buildList {
        add(Modality.TEXT)
        if (
            "vision" in normalizedName ||
            "image" in normalizedName ||
            "multimodal" in normalizedName ||
            "vl" in normalizedName
        ) {
            add(Modality.IMAGE)
        }
    }.distinct()
    val abilities = buildList {
        if ("reason" in normalizedName || "think" in normalizedName) {
            add(ModelAbility.REASONING)
        }
        if (runtimeBackend == LocalRuntimeBackend.LITERT && ("tool" in normalizedName || "function" in normalizedName)) {
            add(ModelAbility.TOOL)
        }
    }

    return LocalModelCatalogEntry(
        id = "imported-${normalizedName.take(48)}",
        modelUuid = Uuid.random(),
        modelId = baseName,
        displayName = prettifyImportedModelName(baseName),
        description = "Imported ${packageFormat.displayName()} package stored on this device.",
        runtimeBackend = runtimeBackend,
        packageFormat = packageFormat,
        provenance = LocalModelProvenance.IMPORTED,
        downloadAccess = LocalModelDownloadAccess.IMPORT_ONLY,
        type = detectedType,
        inputModalities = if (detectedType == ModelType.EMBEDDING) listOf(Modality.TEXT) else inputModalities,
        outputModalities = listOf(Modality.TEXT),
        abilities = abilities,
        minimumRamBytes = maxOf(fileSizeBytes * 2, 2L * GIB),
        recommendedRamBytes = maxOf(fileSizeBytes * 3, 4L * GIB),
        estimatedDownloadBytes = fileSizeBytes,
        estimatedInstalledBytes = fileSizeBytes,
        files = listOf(LocalModelDownloadFile(relativePath = fileName, sizeBytes = fileSizeBytes)),
        aliases = listOf(baseName),
        featureTags = buildList {
            if (detectedType == ModelType.EMBEDDING) add("embeddings")
            if (Modality.IMAGE in inputModalities) add("vision")
            if (ModelAbility.REASONING in abilities) add("reasoning")
            if (ModelAbility.TOOL in abilities) add("tools")
        }
    )
}

fun detectLocalPackageFormat(fileName: String): LocalPackageFormat {
    return LocalPackageFormat.entries.firstOrNull { format ->
        format.extensions.any { fileName.endsWith(it, ignoreCase = true) }
    } ?: LocalPackageFormat.UNKNOWN
}

private fun LocalPackageFormat.displayName(): String {
    return when (this) {
        LocalPackageFormat.LITERT_LM -> "LiteRT"
        LocalPackageFormat.TFLITE -> "TensorFlow Lite"
        LocalPackageFormat.UNKNOWN -> "local model"
    }
}

private fun prettifyImportedModelName(value: String): String {
    return value
        .replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

private fun String.normalizeLocalLookupKey(): String {
    return lowercase()
        .substringBeforeLast(".litertlm")
        .substringBeforeLast(".tflite")
        .replace(Regex("[^a-z0-9]+"), "")
}
