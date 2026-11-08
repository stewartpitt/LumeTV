package com.lumetv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * M3U / M3U8 playlist support. The playlist is downloaded to a cache file and parsed
 * line by line (large playlists would not fit in memory as one string on a Fire Stick).
 */
class M3uProvider(private val account: Account, cacheDir: File) : Provider {

    private val cacheFile = File(cacheDir, "playlist_${account.m3uUrl.hashCode()}.m3u")
    private val lock = Mutex()

    private var live = emptyList<MediaItem>()
    private var movies = emptyList<MediaItem>()
    private var series = emptyList<MediaItem>()
    private var episodesBySeries = emptyMap<String, List<Episode>>()
    private var loaded = false

    override suspend fun connect() = load(force = true)

    private suspend fun load(force: Boolean = false) = lock.withLock {
        if (loaded && !force) return@withLock
        withContext(Dispatchers.IO) {
            val stale = !cacheFile.exists() ||
                System.currentTimeMillis() - cacheFile.lastModified() > 12 * 60 * 60 * 1000L
            if (force || stale) download()
            parse()
        }
        loaded = true
    }

    private fun download() {
        val req = Request.Builder().url(account.m3uUrl.trim()).header("User-Agent", USER_AGENT).build()
        Http.client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IllegalStateException("Playlist download failed (${res.code})")
            val body = res.body ?: throw IllegalStateException("Playlist was empty")
            val tmp = File(cacheFile.path + ".tmp")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            tmp.renameTo(cacheFile)
        }
    }

    private fun parse() {
        val liveOut = ArrayList<MediaItem>()
        val movieOut = ArrayList<MediaItem>()
        val seriesOut = LinkedHashMap<String, MediaItem>()
        val episodesOut = HashMap<String, MutableList<Episode>>()

        var attrs: Map<String, String> = emptyMap()
        var name = ""
        var pending = false
        var sawHeader = false
        var counter = 0

        cacheFile.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#EXTM3U")) { sawHeader = true; continue }
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = extractName(line)
                    pending = true
                    continue
                }
                if (line.startsWith("#")) continue
                if (!pending) continue
                pending = false
                counter++

                val url = line
                val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Uncategorised"
                val logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                val title = name.ifBlank { attrs["tvg-name"] ?: "Item $counter" }
                val lower = url.lowercase().substringBefore('?')
                val ext = lower.substringAfterLast('.', "")
                val ep = EPISODE.find(title)

                when {
                    lower.contains("/series/") || (ep != null && ext in VOD_EXT) -> {
                        val show = ep?.groupValues?.get(1)?.trim()?.trimEnd('-', '.', '_')?.trim()
                            ?.ifBlank { null } ?: title
                        val seriesId = "$group|$show"
                        seriesOut.getOrPut(seriesId) {
                            MediaItem(seriesId, ContentType.SERIES, show, logo, group)
                        }
                        episodesOut.getOrPut(seriesId) { mutableListOf() } += Episode(
                            id = "m3u$counter",
                            season = ep?.groupValues?.get(2)?.toIntOrNull() ?: 1,
                            number = ep?.groupValues?.get(3)?.toIntOrNull() ?: (episodesOut[seriesId]?.size ?: 0) + 1,
                            title = title,
                            image = logo,
                            url = url,
                        )
                    }
                    lower.contains("/movie/") || ext in VOD_EXT -> movieOut += MediaItem(
                        "m3u$counter", ContentType.MOVIE, title, logo, group, url = url,
                    )
                    else -> liveOut += MediaItem(
                        "m3u$counter", ContentType.LIVE, title, logo, group, url = url,
                        epgId = attrs["tvg-id"],
                    )
                }
            }
        }
        if (!sawHeader && counter == 0) throw IllegalStateException("That link didn't return an M3U playlist")

        live = liveOut
        movies = movieOut
        series = seriesOut.values.toList()
        episodesBySeries = episodesOut
    }

    private fun listFor(type: ContentType) = when (type) {
        ContentType.LIVE -> live
        ContentType.MOVIE -> movies
        ContentType.SERIES -> series
    }

    override suspend fun categories(type: ContentType): List<Category> {
        load()
        val groups = listFor(type).mapNotNull { it.categoryId }.distinct()
        return listOf(Category(Provider.ALL, "All")) + groups.map { Category(it, it) }
    }

    override suspend fun items(type: ContentType, categoryId: String): List<MediaItem> {
        load()
        val all = listFor(type)
        return if (categoryId == Provider.ALL) all else all.filter { it.categoryId == categoryId }
    }

    override suspend fun movieInfo(item: MediaItem): MovieInfo? = null

    override suspend fun seriesInfo(item: MediaItem): SeriesInfo {
        load()
        val eps = episodesBySeries[item.id].orEmpty()
        return SeriesInfo(
            plot = null, genre = null, cast = null, rating = null, releaseDate = null, backdrop = null,
            seasons = eps.groupBy { it.season }.mapValues { (_, l) -> l.sortedBy { it.number } }.toSortedMap(),
        )
    }

    override suspend fun shortEpg(item: MediaItem): List<EpgEntry> = emptyList()

    override fun streamUrl(item: MediaItem): String =
        item.url ?: throw IllegalArgumentException("No stream URL")

    override fun episodeUrl(episode: Episode): String =
        episode.url ?: throw IllegalArgumentException("No stream URL")

    companion object {
        private val ATTR = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
        private val EPISODE = Regex("""^(.*?)[\s._-]*S(\d{1,2})\s*E(\d{1,3})""", RegexOption.IGNORE_CASE)
        private val VOD_EXT = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm")

        /** The display name is whatever follows the comma that ends the attribute list. */
        fun extractName(line: String): String {
            var inQuotes = false
            for (i in line.indices) {
                val c = line[i]
                if (c == '"') inQuotes = !inQuotes
                else if (c == ',' && !inQuotes) return line.substring(i + 1).trim()
            }
            return ""
        }
    }
}
