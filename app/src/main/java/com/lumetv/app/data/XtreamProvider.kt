package com.lumetv.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

class XtreamProvider(private val account: Account) : Provider {

    private val base: String = normaliseServer(account.server)
    private val user = account.username.trim()
    private val pass = account.password.trim()

    private fun api(action: String? = null, vararg params: Pair<String, String>): String {
        val sb = StringBuilder("$base/player_api.php?username=${enc(user)}&password=${enc(pass)}")
        if (action != null) sb.append("&action=").append(action)
        params.forEach { (k, v) -> sb.append('&').append(k).append('=').append(enc(v)) }
        return sb.toString()
    }

    override suspend fun connect() {
        val root = Http.getJson(api()) as? JsonObject
            ?: throw IllegalStateException("That doesn't look like an Xtream Codes server")
        val info = root.obj("user_info") ?: throw IllegalStateException("No account info returned")
        if (info.s("auth") == "0") throw IllegalStateException("Username or password not accepted")
        val status = info.s("status")
        if (status != null && !status.equals("Active", true)) {
            throw IllegalStateException("Account status: $status")
        }
    }

    override suspend fun categories(type: ContentType): List<Category> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        val list = Http.getJson(api(action)).asArray().mapNotNull { o ->
            val id = o.s("category_id") ?: return@mapNotNull null
            Category(id, o.s("category_name") ?: "Unnamed")
        }
        return listOf(Category(Provider.ALL, "All")) + list
    }

    override suspend fun items(type: ContentType, categoryId: String): List<MediaItem> {
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        val url = if (categoryId == Provider.ALL) api(action) else api(action, "category_id" to categoryId)
        return Http.getJson(url).asArray().mapNotNull { o ->
            when (type) {
                ContentType.LIVE -> MediaItem(
                    id = o.s("stream_id") ?: return@mapNotNull null,
                    type = type,
                    name = o.s("name") ?: "Channel",
                    image = o.s("stream_icon"),
                    categoryId = o.s("category_id"),
                    epgId = o.s("epg_channel_id"),
                )
                ContentType.MOVIE -> MediaItem(
                    id = o.s("stream_id") ?: return@mapNotNull null,
                    type = type,
                    name = o.s("name") ?: "Movie",
                    image = o.s("stream_icon"),
                    categoryId = o.s("category_id"),
                    ext = o.s("container_extension") ?: "mp4",
                    rating = o.s("rating"),
                )
                ContentType.SERIES -> MediaItem(
                    id = o.s("series_id") ?: return@mapNotNull null,
                    type = type,
                    name = o.s("name") ?: "Series",
                    image = o.s("cover"),
                    categoryId = o.s("category_id"),
                    rating = o.s("rating"),
                )
            }
        }
    }

    override suspend fun movieInfo(item: MediaItem): MovieInfo? {
        val root = Http.getJson(api("get_vod_info", "vod_id" to item.id)) as? JsonObject ?: return null
        val info = root.obj("info") ?: return null
        return MovieInfo(
            plot = info.s("plot") ?: info.s("description"),
            genre = info.s("genre"),
            cast = info.s("cast") ?: info.s("actors"),
            director = info.s("director"),
            releaseDate = info.s("releasedate") ?: info.s("release_date"),
            duration = info.s("duration"),
            rating = info.s("rating"),
            backdrop = backdrop(info),
        )
    }

    override suspend fun seriesInfo(item: MediaItem): SeriesInfo {
        val root = Http.getJson(api("get_series_info", "series_id" to item.id)) as? JsonObject
            ?: throw IllegalStateException("No details for this series")
        val info = root.obj("info")

        val raw = mutableListOf<JsonObject>()
        when (val eps = root["episodes"]) {
            is JsonObject -> eps.values.forEach { raw += it.asArray() }
            is JsonArray -> eps.forEach { el ->
                if (el is JsonArray) raw += el.asArray() else (el as? JsonObject)?.let { raw += it }
            }
            else -> {}
        }

        val episodes = raw.mapNotNull { o ->
            val epInfo = o.obj("info")
            Episode(
                id = o.s("id") ?: return@mapNotNull null,
                season = o.s("season")?.toIntOrNull() ?: 1,
                number = o.s("episode_num")?.toIntOrNull() ?: 0,
                title = o.s("title") ?: "Episode",
                ext = o.s("container_extension") ?: "mp4",
                image = epInfo?.s("movie_image"),
                plot = epInfo?.s("plot"),
            )
        }

        return SeriesInfo(
            plot = info?.s("plot"),
            genre = info?.s("genre"),
            cast = info?.s("cast"),
            rating = info?.s("rating"),
            releaseDate = info?.s("releaseDate") ?: info?.s("release_date"),
            backdrop = info?.let { backdrop(it) },
            seasons = episodes.distinctBy { it.id }.groupBy { it.season }
                .mapValues { (_, list) -> list.sortedBy { it.number } }
                .toSortedMap(),
        )
    }

    override suspend fun shortEpg(item: MediaItem): List<EpgEntry> {
        val root = Http.getJson(api("get_short_epg", "stream_id" to item.id, "limit" to "4")) as? JsonObject
            ?: return emptyList()
        return root["epg_listings"].asArray().mapNotNull { o ->
            val start = o.s("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = o.s("stop_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            EpgEntry(
                title = o.s("title")?.fromBase64() ?: "",
                start = start * 1000,
                end = end * 1000,
                description = o.s("description")?.fromBase64(),
            )
        }.filter { it.end > System.currentTimeMillis() }.sortedBy { it.start }
    }

    override fun streamUrl(item: MediaItem): String = when (item.type) {
        ContentType.LIVE -> "$base/live/$user/$pass/${item.id}.ts"
        ContentType.MOVIE -> "$base/movie/$user/$pass/${item.id}.${item.ext ?: "mp4"}"
        ContentType.SERIES -> throw IllegalArgumentException("Pick an episode to play a series")
    }

    override fun episodeUrl(episode: Episode): String =
        "$base/series/$user/$pass/${episode.id}.${episode.ext ?: "mp4"}"

    private fun backdrop(info: JsonObject): String? {
        val b = info["backdrop_path"]
        return (b as? JsonArray)?.firstOrNull()?.str() ?: b.str() ?: info.s("cover_big")
    }

    companion object {
        fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

        /** Accepts "host:port", "http://host:port/", or a pasted player_api / get.php link. */
        fun normaliseServer(input: String): String {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            listOf("/player_api.php", "/get.php", "/xmltv.php").forEach { marker ->
                val i = s.indexOf(marker, ignoreCase = true)
                if (i > 0) s = s.substring(0, i)
            }
            return s.trimEnd('/')
        }
    }
}
