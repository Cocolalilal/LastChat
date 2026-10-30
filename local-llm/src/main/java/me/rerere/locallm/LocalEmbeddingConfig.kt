package me.rerere.locallm

internal fun usesLiteRtEmbeddingEngine(modelFile: String): Boolean =
    modelFile.endsWith(".litertlm", ignoreCase = true)

/** The legacy RAG library is arm64-only; LiteRT-LM also ships an x86_64 embedding runtime. */
internal fun supportsLocalEmbedding(modelFile: String, deviceAbis: List<String>): Boolean {
    val supportedAbis = if (usesLiteRtEmbeddingEngine(modelFile)) {
        setOf("arm64-v8a", "x86_64")
    } else {
        setOf("arm64-v8a")
    }
    return deviceAbis.any { it.lowercase() in supportedAbis }
}

/** LastChat uses the same embedding API for stored text and queries, so use the symmetric task. */
internal fun embeddingGemma2Text(text: String): String =
    "task: sentence similarity | text: ${text.trim()}"
