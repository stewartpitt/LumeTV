package com.lumetv.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lumetv.app.data.ContentType
import com.lumetv.app.data.MediaItem
import com.lumetv.app.data.ProgressEntry

/** Continue Watching, favourite channels/films/series and recently watched channels. */
@Composable
fun MyListTab(vm: AppViewModel) {
    val favChannels = vm.favourites.filter { it.type == ContentType.LIVE }
    val favMovies = vm.favourites.filter { it.type == ContentType.MOVIE }
    val favSeries = vm.favourites.filter { it.type == ContentType.SERIES }

    val empty = vm.continueWatching.isEmpty() && vm.favourites.isEmpty() && vm.recentChannels.isEmpty()
    if (empty) {
        Message("Nothing here yet.\n\nHold OK on any channel, film or series to add it to My List.\nFilms and episodes you start will appear under Continue Watching.")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        if (vm.continueWatching.isNotEmpty()) item {
            Rail("Continue watching") {
                items(vm.continueWatching, key = { it.key }) { ResumeCard(vm, it) }
            }
        }
        if (favChannels.isNotEmpty()) item {
            Rail("Favourite channels") {
                items(favChannels, key = { it.key }) { ch -> ChannelCard(vm, ch, favChannels, "fav") }
            }
        }
        if (vm.recentChannels.isNotEmpty()) item {
            Rail("Recently watched channels") {
                items(vm.recentChannels, key = { "recent:" + it.key }) { ch -> ChannelCard(vm, ch, vm.recentChannels, "recent") }
            }
        }
        if (favMovies.isNotEmpty()) item {
            Rail("Favourite films") {
                items(favMovies, key = { it.key }) { m ->
                    PosterCard(vm, m, onClick = { vm.push(Screen.MovieDetail(m)) }, progress = vm.progress[m.key]?.fraction, focusKey = "my:" + m.key)
                }
            }
        }
        if (favSeries.isNotEmpty()) item {
            Rail("Favourite series") {
                items(favSeries, key = { it.key }) { s ->
                    PosterCard(vm, s, onClick = { vm.push(Screen.SeriesDetail(s)) }, focusKey = "my:" + s.key)
                }
            }
        }
    }
}

@Composable
private fun Rail(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(bottom = 10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
            content = content,
        )
    }
}

private val focusBorder = BorderStroke(3.dp, Brand)

@Composable
private fun ResumeCard(vm: AppViewModel, entry: ProgressEntry) {
    Column(Modifier.width(260.dp)) {
        Card(
            onClick = { vm.resume(entry) },
            border = CardDefaults.border(focusedBorder = Border(focusBorder)),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusMemory(vm, "resume:" + entry.key),
        ) {
            Box(Modifier.fillMaxSize().background(Panel)) {
                AsyncImage(entry.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                ProgressLine(entry.fraction, Modifier.align(Alignment.BottomCenter))
            }
        }
        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        entry.subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun ChannelCard(vm: AppViewModel, ch: MediaItem, list: List<MediaItem>, prefix: String) {
    Column(Modifier.width(180.dp)) {
        Card(
            onClick = { vm.playLive(ch, list) },
            onLongClick = { vm.toggleFavourite(ch) },
            border = CardDefaults.border(focusedBorder = Border(focusBorder)),
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusMemory(vm, "$prefix:" + ch.key),
        ) {
            Box(Modifier.fillMaxSize().background(Panel), contentAlignment = Alignment.Center) {
                Text(ch.name, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
                AsyncImage(ch.image, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
            }
        }
        Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
