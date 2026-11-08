package com.lumetv.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType { LIVE, MOVIE, SERIES }

@Serializable
data class Account(
    val type: String,            // "xtream" or "m3u"
    val name: String,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
) {
    val isXtream get() = type == "xtream"
}

@Serializable
data class Category(val id: String, val name: String)

/** A channel, movie or series as it appears in a list. */
@Serializable
data class MediaItem(
    val id: String,
    val type: ContentType,
    val name: String,
    val image: String? = null,
    val categoryId: String? = null,
    val ext: String? = null,       // container extension for Xtream movies
    val url: String? = null,       // direct URL (M3U playlists)
    val epgId: String? = null,
    val rating: String? = null,
) {
    val key get() = "${type.name}:$id"
}

data class MovieInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val duration: String?,
    val rating: String?,
    val backdrop: String?,
)

@Serializable
data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String? = null,
    val image: String? = null,
    val plot: String? = null,
    val url: String? = null,
) {
    val key get() = "EPISODE:$id"
}

data class SeriesInfo(
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val rating: String?,
    val releaseDate: String?,
    val backdrop: String?,
    val seasons: Map<Int, List<Episode>>,
)

data class EpgEntry(val title: String, val start: Long, val end: Long, val description: String?)

/** Everything the player needs to start a stream. */
@Serializable
data class PlayRequest(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val image: String? = null,
    val live: Boolean = false,
    val startMs: Long = 0,
)

/** Saved resume point, also used for the Continue Watching row. */
@Serializable
data class ProgressEntry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val url: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val watched get() = fraction >= 0.9f
}
