package com.lumetv.app.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Small on-device store: login, favourites, resume points and recent channels.
 * Favourites/progress are kept per account so switching playlists doesn't mix them.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("lumetv", Context.MODE_PRIVATE)
    private val json = Http.json

    private val itemList = ListSerializer(MediaItem.serializer())
    private val progressMap = MapSerializer(String.serializer(), ProgressEntry.serializer())

    var account: Account?
        get() = prefs.getString("account", null)?.let { runCatching { json.decodeFromString(Account.serializer(), it) }.getOrNull() }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("account") else putString("account", json.encodeToString(Account.serializer(), value))
            }.apply()
        }

    private fun scoped(key: String): String {
        val a = account ?: return key
        return "$key:${(a.server + a.username + a.m3uUrl).hashCode()}"
    }

    // ---- favourites ----
    fun favourites(): List<MediaItem> =
        prefs.getString(scoped("favs"), null)?.let { runCatching { json.decodeFromString(itemList, it) }.getOrNull() }
            ?: emptyList()

    fun isFavourite(item: MediaItem) = favourites().any { it.key == item.key }

    /** Returns true if the item is now a favourite. */
    fun toggleFavourite(item: MediaItem): Boolean {
        val list = favourites().toMutableList()
        val existing = list.indexOfFirst { it.key == item.key }
        val nowFav = existing < 0
        if (nowFav) list.add(0, item) else list.removeAt(existing)
        prefs.edit().putString(scoped("favs"), json.encodeToString(itemList, list)).apply()
        return nowFav
    }

    // ---- resume points ----
    fun progress(): Map<String, ProgressEntry> =
        prefs.getString(scoped("progress"), null)?.let { runCatching { json.decodeFromString(progressMap, it) }.getOrNull() }
            ?: emptyMap()

    fun saveProgress(entry: ProgressEntry) {
        val map = progress().toMutableMap()
        map[entry.key] = entry
        // keep the store small
        val trimmed = map.values.sortedByDescending { it.updatedAt }.take(300).associateBy { it.key }
        prefs.edit().putString(scoped("progress"), json.encodeToString(progressMap, trimmed)).apply()
    }

    fun continueWatching(): List<ProgressEntry> =
        progress().values.filter { !it.watched && it.positionMs > 60_000 }
            .sortedByDescending { it.updatedAt }.take(20)

    // ---- recent channels ----
    fun recentChannels(): List<MediaItem> =
        prefs.getString(scoped("recent"), null)?.let { runCatching { json.decodeFromString(itemList, it) }.getOrNull() }
            ?: emptyList()

    fun addRecentChannel(item: MediaItem) {
        val list = recentChannels().filter { it.key != item.key }.toMutableList()
        list.add(0, item)
        prefs.edit().putString(scoped("recent"), json.encodeToString(itemList, list.take(15))).apply()
    }
}
