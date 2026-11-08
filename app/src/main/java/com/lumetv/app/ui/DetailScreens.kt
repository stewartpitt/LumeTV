package com.lumetv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lumetv.app.data.MediaItem
import com.lumetv.app.data.MovieInfo
import com.lumetv.app.data.SeriesInfo

@Composable
private fun Backdrop(url: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Background, Background.copy(alpha = 0.92f), Background.copy(alpha = 0.55f)))
            )
        )
        content()
    }
}

@Composable
private fun Poster(url: String?) {
    AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Panel),
    )
}

@Composable
private fun Meta(vararg parts: String?) {
    val text = parts.filterNotNull().filter { it.isNotBlank() }.joinToString("  ·  ")
    if (text.isNotEmpty()) Text(text, color = Muted, fontSize = 14.sp)
}

// ---------------------------------------------------------------- Movie

@Composable
fun MovieDetailScreen(vm: AppViewModel, item: MediaItem) {
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    LaunchedEffect(item.key) { info = runCatching { vm.provider?.movieInfo(item) }.getOrNull() }

    val saved = vm.progress[item.key]
    val canResume = saved != null && !saved.watched && saved.positionMs > 60_000
    val playFocus = remember { FocusRequester() }
    InitialFocus(vm, playFocus)

    Backdrop(info?.backdrop) {
        Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Poster(item.image)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Meta(info?.releaseDate?.take(4), info?.duration, info?.genre, (info?.rating ?: item.rating)?.let { "★ $it" })
                info?.plot?.let { Text(it, color = Color.White, fontSize = 16.sp, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                info?.cast?.let { Text("Cast: $it", color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                info?.director?.let { Text("Director: $it", color = Muted, fontSize = 13.sp) }

                if (canResume) {
                    ProgressLine(saved!!.fraction, Modifier.width(320.dp).padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { vm.playMovie(item) },
                        modifier = Modifier.focusMemory(vm, "play:${item.key}", playFocus),
                    ) { Text(if (canResume) "Resume" else "Play") }
                    if (canResume) {
                        Button(onClick = { vm.playMovie(item, fromStart = true) }) { Text("Start over") }
                    }
                    val fav = vm.isFavourite(item)
                    Button(onClick = { vm.toggleFavourite(item) }) {
                        Text(if (fav) "★ In My List" else "+ My List")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Series

@Composable
fun SeriesDetailScreen(vm: AppViewModel, item: MediaItem) {
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(item.key) {
        try {
            val p = vm.provider ?: return@LaunchedEffect
            val loaded = p.seriesInfo(item)
            info = loaded
            if (season == null) season = loaded.seasons.keys.firstOrNull()
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this series"
        }
    }

    val favFocus = remember { FocusRequester() }
    InitialFocus(vm, favFocus, info != null)

    Backdrop(info?.backdrop) {
        Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Poster(item.image)
                Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Meta(info?.releaseDate?.take(4), info?.genre, (info?.rating ?: item.rating)?.let { "★ $it" })
                val fav = vm.isFavourite(item)
                Button(
                    onClick = { vm.toggleFavourite(item) },
                    modifier = Modifier.focusRequester(favFocus),
                ) { Text(if (fav) "★ In My List" else "+ My List") }
            }

            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val i = info
                when {
                    error != null -> Message(error!!)
                    i == null -> Message("Loading episodes…")
                    i.seasons.isEmpty() -> Message("No episodes found")
                    else -> {
                        i.plot?.let { Text(it, color = Color.White, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(i.seasons.keys.toList()) { s ->
                                ListItem(
                                    selected = s == season,
                                    onClick = { season = s },
                                    headlineContent = { Text("Season $s") },
                                    colors = ListItemDefaults.colors(
                                        selectedContainerColor = Brand.copy(alpha = 0.3f),
                                        focusedContainerColor = Brand,
                                    ),
                                    modifier = Modifier.width(140.dp).focusMemory(vm, "season:${item.key}:$s"),
                                )
                            }
                        }

                        val episodes = i.seasons[season].orEmpty()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(episodes, key = { it.id }) { ep ->
                                val prog = vm.progress[ep.key]
                                ListItem(
                                    selected = false,
                                    onClick = { vm.playEpisode(item, ep, episodes) },
                                    headlineContent = {
                                        Text("${ep.number}. ${ep.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Column {
                                            ep.plot?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }
                                            if (prog != null) {
                                                if (prog.watched) Text("Watched", color = Brand, fontSize = 12.sp)
                                                else ProgressLine(prog.fraction, Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(focusedContainerColor = Brand),
                                    modifier = Modifier.fillMaxWidth().focusMemory(vm, ep.key),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
