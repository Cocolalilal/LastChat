package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.AssistantMemory

internal const val EPISODIC_MIN_SIMILARITY = 0.25f

internal fun computeCoreMemoryScore(similarity: Float): Float = similarity * 1.05f

internal fun computeEpisodeRecencyScore(
    startTimeMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Float {
    val ageInMillis = (nowMillis - startTimeMillis).coerceAtLeast(0L)
    val ageInDays = ageInMillis / (1000.0 * 60 * 60 * 24)
    return (1.0 / (1.0 + (ageInDays / 7.0))).toFloat()
}

internal fun normalizeEpisodeSignificance(significance: Int): Float {
    return ((significance.coerceIn(1, 10) - 1) / 9f).coerceIn(0f, 1f)
}

internal fun computeEpisodeScore(
    similarity: Float,
    startTimeMillis: Long,
    significance: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Float? {
    if (similarity < EPISODIC_MIN_SIMILARITY) return null

    val recency = computeEpisodeRecencyScore(
        startTimeMillis = startTimeMillis,
        nowMillis = nowMillis,
    )
    val significanceBoost = normalizeEpisodeSignificance(significance)

    return (similarity * 0.65f) + (recency * 0.25f) + (significanceBoost * 0.10f)
}

internal fun episodeRetentionDays(significance: Int): Long {
    return 30L + (significance.coerceIn(1, 10) * 2L)
}

internal fun episodeAccessGraceDays(significance: Int): Long {
    return 7L + significance.coerceIn(1, 10).toLong()
}

internal fun shouldPruneEpisode(
    episode: ChatEpisodeEntity,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val age = nowMillis - episode.startTime
    val timeSinceAccess = nowMillis - episode.lastAccessedAt
    val retentionMs = episodeRetentionDays(episode.significance) * 24 * 60 * 60 * 1000L
    val accessGraceMs = episodeAccessGraceDays(episode.significance) * 24 * 60 * 60 * 1000L
    return age > retentionMs && timeSinceAccess > accessGraceMs
}

internal fun needsEmbeddingRefresh(
    memory: AssistantMemory,
    currentEmbeddingModelId: String,
): Boolean {
    if (!memory.hasEmbedding) return true
    val normalizedCurrentModelId = currentEmbeddingModelId
        .takeUnless { it.isBlank() || it == "null" }
        ?: return false
    return memory.embeddingModelId != normalizedCurrentModelId
}
