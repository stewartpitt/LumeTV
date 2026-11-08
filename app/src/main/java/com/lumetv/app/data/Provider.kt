package com.lumetv.app.data

/** Common interface so the UI doesn't care whether it's Xtream or M3U. */
interface Provider {
    /** Throws with a readable message if the login details are wrong. */
    suspend fun connect()

    suspend fun categories(type: ContentType): List<Category>
    suspend fun items(type: ContentType, categoryId: String): List<MediaItem>
    suspend fun movieInfo(item: MediaItem): MovieInfo?
    suspend fun seriesInfo(item: MediaItem): SeriesInfo
    suspend fun shortEpg(item: MediaItem): List<EpgEntry>

    fun streamUrl(item: MediaItem): String
    fun episodeUrl(episode: Episode): String

    companion object {
        const val ALL = "*"

        fun create(account: Account, cacheDir: java.io.File): Provider =
            if (account.isXtream) XtreamProvider(account) else M3uProvider(account, cacheDir)
    }
}
