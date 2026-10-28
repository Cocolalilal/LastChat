package me.rerere.locallm

const val TEXT_EMBEDDING_MODEL_ID = "EmbeddingGemma-2-Text-270M"
const val RETIRED_EMBEDDING_MODEL_ID = "EmbeddingGemma-300M"

fun InstalledLocalModel.hasUsableTextEmbeddingFile(): Boolean =
    id == TEXT_EMBEDDING_MODEL_ID && isEmbedding && sizeInBytes > 0 &&
        java.io.File(filePath).let { it.isFile && it.length() == sizeInBytes }

internal fun usesLiteRtEmbeddingEngine(modelFile: String): Boolean =
    modelFile.endsWith(".litertlm", ignoreCase = true)

/** Text embeddings use LiteRT-LM on arm64 or x86_64. Legacy .tflite files are retired. */
internal fun supportsLocalEmbedding(modelFile: String, deviceAbis: List<String>): Boolean =
    usesLiteRtEmbeddingEngine(modelFile) && deviceAbis.any { it.lowercase() in setOf("arm64-v8a", "x86_64") }

/** LastChat uses the same embedding API for stored text and queries, so use the symmetric task. */
internal fun embeddingGemma2Text(text: String): String =
    "task: sentence similarity | text: ${text.trim()}"
