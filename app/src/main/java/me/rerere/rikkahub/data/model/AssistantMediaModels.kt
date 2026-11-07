package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantMediaCategory(val label: String) {
    WATCHING("Watching"),
    PLAN_TO_WATCH("Plan to Watch"),
    WATCHED("Watched"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped");

    companion object {
        fun fromString(value: String): AssistantMediaCategory {
            return entries.firstOrNull { 
                it.name.equals(value, ignoreCase = true) || 
                it.label.equals(value, ignoreCase = true) 
            } ?: PLAN_TO_WATCH
        }
    }
}

@Serializable
data class AssistantMediaItem(
    val tmdbId: Long,
    val mediaType: String = "tv", // "tv" or "movie"
    val title: String,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val status: String = AssistantMediaCategory.PLAN_TO_WATCH.name,
    val isFavorite: Boolean = false,
    val userRating: Float? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 1,
    val watchedEpisodes: Set<String> = emptySet(), // format: "s{season}e{episode}"
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TmdbSeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val overview: String = "",
    val poster: String? = null,
)

@Serializable
data class TmdbEpisodeSummary(
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String,
    val overview: String = "",
    val airDate: String = "",
    val rating: String = "",
    val still: String? = null,
)
