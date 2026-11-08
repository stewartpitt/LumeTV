package com.lumetv.app.ui

import android.app.Application
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumetv.app.data.Account
import com.lumetv.app.data.Category
import com.lumetv.app.data.ContentType
import com.lumetv.app.data.EpgEntry
import com.lumetv.app.data.Episode
import com.lumetv.app.data.MediaItem
import com.lumetv.app.data.PlayRequest
import com.lumetv.app.data.ProgressEntry
import com.lumetv.app.data.Provider
import com.lumetv.app.data.Store
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data class MovieDetail(val item: MediaItem) : Screen
    data class SeriesDetail(val item: MediaItem) : Screen
    data class Player(val request: PlayRequest) : Screen
}

enum class HomeTab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series"), MY_LIST("My List"), SETTINGS("Settings") }

/** Browse state for one content type, kept here so it survives going into the player and back. */
class Catalog(val type: ContentType) {
    var categories by mutableStateOf<List<Category>>(emptyList())
    var selected by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<MediaItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var filter by mutableStateOf("")
    /** Set when a new category loads so the list jumps back to the top. */
    var scrollToTop by mutableStateOf(false)
    val categoryState = LazyListState()
    val listState = LazyListState()
    val gridState = LazyGridState()
    val cache = HashMap<String, List<MediaItem>>()

    fun reset() {
        categories = emptyList(); selected = null; items = emptyList(); error = null; filter = ""; cache.clear()
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val store = Store(app)
    var provider: Provider? = null
        private set

    val stack = mutableStateListOf<Screen>()
    val current: Screen get() = stack.last()

    var homeTab by mutableStateOf(HomeTab.LIVE)
    val live = Catalog(ContentType.LIVE)
    val movies = Catalog(ContentType.MOVIE)
    val series = Catalog(ContentType.SERIES)

    var favourites by mutableStateOf(store.favourites())
        private set
    var continueWatching by mutableStateOf(store.continueWatching())
        private set
    var recentChannels by mutableStateOf(store.recentChannels())
        private set
    var progress by mutableStateOf(store.progress())
        private set

    // ---- TV guide (now & next) cache for the channel list ----
    val epg = mutableStateMapOf<String, List<EpgEntry>>()
    private val epgFetchedAt = HashMap<String, Long>()
    private val epgLimit = Semaphore(4)

    var matchFrameRate by mutableStateOf(store.matchFrameRate)
        private set

    fun setMatchFrameRate(on: Boolean) {
        store.matchFrameRate = on
        matchFrameRate = on
    }

    // ---- focus restore when coming back to a screen ----
    var lastFocus: String? = null
    var restoreKey by mutableStateOf<String?>(null)
    private val focusStack = ArrayList<String?>()

    // ---- playback context ----
    private var zapList: List<MediaItem> = emptyList()
    private var episodeQueue: List<PlayRequest> = emptyList()

    init {
        val account = store.account
        if (account != null) {
            provider = Provider.create(account, app.cacheDir)
            stack.add(Screen.Home)
        } else {
            stack.add(Screen.Login)
        }
    }

    // ---------------- navigation ----------------

    fun push(screen: Screen) {
        focusStack.add(lastFocus)
        stack.add(screen)
    }

    /** Swap the top screen without touching the focus history (used for next episode / zapping). */
    fun replaceTop(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    /** Returns false when already at the root (let the system close the app). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        restoreKey = focusStack.removeLastOrNull()
        refreshPersonal()
        return true
    }

    // ---------------- account ----------------

    /** Returns an error message, or null on success. */
    suspend fun login(account: Account): String? {
        val p = Provider.create(account, getApplication<Application>().cacheDir)
        return try {
            p.connect()
            store.account = account
            provider = p
            listOf(live, movies, series).forEach { it.reset() }
            epg.clear(); epgFetchedAt.clear()
            refreshPersonal()
            stack.clear(); focusStack.clear()
            stack.add(Screen.Home)
            homeTab = HomeTab.LIVE
            null
        } catch (e: Exception) {
            e.message ?: "Couldn't connect"
        }
    }

    fun logout() {
        store.account = null
        epg.clear(); epgFetchedAt.clear()
        provider = null
        listOf(live, movies, series).forEach { it.reset() }
        stack.clear(); focusStack.clear()
        stack.add(Screen.Login)
    }

    fun refreshPersonal() {
        favourites = store.favourites()
        continueWatching = store.continueWatching()
        recentChannels = store.recentChannels()
        progress = store.progress()
    }

    fun toggleFavourite(item: MediaItem): Boolean {
        val now = store.toggleFavourite(item)
        favourites = store.favourites()
        return now
    }

    fun isFavourite(item: MediaItem) = favourites.any { it.key == item.key }

    // ---------------- browsing ----------------

    fun catalogFor(type: ContentType) = when (type) {
        ContentType.LIVE -> live
        ContentType.MOVIE -> movies
        ContentType.SERIES -> series
    }

    fun ensureLoaded(catalog: Catalog) {
        if (catalog.categories.isNotEmpty() || catalog.loading) return
        val p = provider ?: return
        catalog.loading = true
        catalog.error = null
        viewModelScope.launch {
            try {
                catalog.categories = p.categories(catalog.type).distinctBy { it.id }
                catalog.loading = false
                catalog.categories.firstOrNull()?.let { selectCategory(catalog, it.id) }
            } catch (e: Exception) {
                catalog.error = e.message ?: "Couldn't load categories"
                catalog.loading = false
            }
        }
    }

    fun selectCategory(catalog: Catalog, categoryId: String) {
        if (catalog.selected == categoryId && catalog.items.isNotEmpty()) return
        val p = provider ?: return
        catalog.selected = categoryId
        catalog.filter = ""
        catalog.cache[categoryId]?.let {
            catalog.items = it
            catalog.scrollToTop = true
            return
        }
        catalog.loading = true
        catalog.error = null
        catalog.items = emptyList()
        viewModelScope.launch {
            try {
                val list = p.items(catalog.type, categoryId).distinctBy { it.key }
                catalog.cache[categoryId] = list
                if (catalog.selected == categoryId) {
                    catalog.items = list
                    catalog.scrollToTop = true
                }
            } catch (e: Exception) {
                catalog.error = e.message ?: "Couldn't load this category"
            } finally {
                catalog.loading = false
            }
        }
    }

    /** Re-download everything (for M3U this fetches the playlist file again). */
    fun refreshAll() {
        viewModelScope.launch {
            if (store.account?.isXtream == false) runCatching { provider?.connect() }
            listOf(live, movies, series).forEach { retry(it) }
        }
    }

    fun retry(catalog: Catalog) {
        catalog.reset()
        ensureLoaded(catalog)
    }

    /** Fetch now & next for a channel, at most every 10 minutes and only once the current show has ended. */
    fun loadEpg(item: MediaItem) {
        val p = provider ?: return
        val now = System.currentTimeMillis()
        val last = epgFetchedAt[item.key]
        val cached = epg[item.key]
        val stillCurrent = cached?.any { it.end > now } == true
        if (last != null && (stillCurrent || now - last < 10 * 60_000)) return
        epgFetchedAt[item.key] = now
        viewModelScope.launch {
            val list = epgLimit.withPermit { runCatching { p.shortEpg(item) }.getOrDefault(emptyList()) }
            epg[item.key] = list
        }
    }

    // ---------------- playback ----------------

    fun playLive(item: MediaItem, list: List<MediaItem>) {
        val p = provider ?: return
        zapList = list
        episodeQueue = emptyList()
        store.addRecentChannel(item)
        push(Screen.Player(liveRequest(p, item)))
    }

    private fun liveRequest(p: Provider, item: MediaItem) =
        PlayRequest(key = item.key, title = item.name, url = p.streamUrl(item), image = item.image, live = true)

    /** Channel up/down from inside the player. */
    fun zap(currentKey: String, delta: Int): PlayRequest? {
        val p = provider ?: return null
        if (zapList.isEmpty()) return null
        val idx = zapList.indexOfFirst { it.key == currentKey }
        val next = zapList[((if (idx < 0) 0 else idx) + delta).mod(zapList.size)]
        store.addRecentChannel(next)
        return liveRequest(p, next)
    }

    fun playMovie(item: MediaItem, fromStart: Boolean = false) {
        val p = provider ?: return
        episodeQueue = emptyList()
        val saved = progress[item.key]
        push(
            Screen.Player(
                PlayRequest(
                    key = item.key, title = item.name, url = p.streamUrl(item), image = item.image,
                    startMs = if (!fromStart && saved != null && !saved.watched) saved.positionMs else 0,
                )
            )
        )
    }

    fun playEpisode(show: MediaItem, episode: Episode, seasonEpisodes: List<Episode>) {
        val p = provider ?: return
        val toRequest = { ep: Episode, resume: Boolean ->
            val saved = progress[ep.key]
            PlayRequest(
                key = ep.key,
                title = show.name,
                subtitle = "S${ep.season} E${ep.number} · ${ep.title}",
                url = p.episodeUrl(ep),
                image = ep.image ?: show.image,
                startMs = if (resume && saved != null && !saved.watched) saved.positionMs else 0,
            )
        }
        val idx = seasonEpisodes.indexOfFirst { it.id == episode.id }
        episodeQueue = seasonEpisodes.drop(idx + 1).map { toRequest(it, false) }
        push(Screen.Player(toRequest(episode, true)))
    }

    fun resume(entry: ProgressEntry) {
        episodeQueue = emptyList()
        push(
            Screen.Player(
                PlayRequest(entry.key, entry.title, entry.subtitle, entry.url, entry.image, startMs = entry.positionMs)
            )
        )
    }

    fun nextEpisode(): PlayRequest? {
        val next = episodeQueue.firstOrNull() ?: return null
        episodeQueue = episodeQueue.drop(1)
        return next
    }

    fun saveProgress(req: PlayRequest, positionMs: Long, durationMs: Long) {
        if (req.live || durationMs <= 0 || positionMs < 5_000) return
        store.saveProgress(
            ProgressEntry(req.key, req.title, req.subtitle, req.image, req.url, positionMs, durationMs, System.currentTimeMillis())
        )
    }
}
