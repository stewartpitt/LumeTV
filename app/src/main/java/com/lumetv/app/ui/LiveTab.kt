package com.lumetv.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lumetv.app.data.MediaItem
import kotlinx.coroutines.delay

@Composable
fun LiveTab(vm: AppViewModel) {
    val catalog = vm.live
    LaunchedEffect(Unit) { vm.ensureLoaded(catalog) }

    LaunchedEffect(catalog.items) {
        if (catalog.scrollToTop && catalog.items.isNotEmpty()) {
            runCatching { catalog.listState.scrollToItem(0) }
            catalog.scrollToTop = false
        }
    }

    if (catalog.categories.isEmpty()) {
        when {
            catalog.error != null -> Message(catalog.error!!) { vm.retry(catalog) }
            else -> Message("Loading channels…")
        }
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CategoryColumn(vm, catalog)

        Column(Modifier.fillMaxSize()) {
            when {
                catalog.loading && catalog.items.isEmpty() -> Message("Loading…")
                catalog.error != null -> Message(catalog.error!!) { vm.retry(catalog) }
                catalog.items.isEmpty() -> Message("No channels in this category")
                else -> LazyColumn(
                    state = catalog.listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, end = 8.dp),
                ) {
                    items(catalog.items, key = { it.key }) { ch ->
                        ChannelRow(vm, ch, onClick = { vm.playLive(ch, catalog.items) })
                    }
                }
            }
        }
    }
}

/** One channel: logo, name, what's on now (with times) and what's on next. */
@Composable
private fun ChannelRow(vm: AppViewModel, ch: MediaItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fav = vm.isFavourite(ch)

    // Load the guide for rows that stay on screen briefly, so fast scrolling doesn't flood the server.
    LaunchedEffect(ch.key) {
        delay(250)
        vm.loadEpg(ch)
    }

    val listings = vm.epg[ch.key].orEmpty()
    val now = System.currentTimeMillis()
    val current = listings.firstOrNull { it.start <= now && it.end > now }
    val next = listings.firstOrNull { it.start >= (current?.end ?: now) && it != current }

    Surface(
        onClick = onClick,
        onLongClick = { vm.toggleFavourite(ch) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Brand,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        modifier = Modifier
            .fillMaxWidth()
            .focusMemory(vm, ch.key)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // logo
            Box(
                Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ch.image, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ch.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fav) Text("  ★", color = if (focused) Color.White else Accent, fontSize = 16.sp)
                }
                val dim = if (focused) Color(0xE6FFFFFF) else Muted
                if (current != null) {
                    Text(current.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${formatTime(current.start)} – ${formatTime(current.end)}", color = dim, fontSize = 13.sp)
                    val span = (current.end - current.start).coerceAtLeast(1)
                    ProgressLine((now - current.start).toFloat() / span, Modifier.width(220.dp).padding(vertical = 4.dp))
                    if (focused && !current.description.isNullOrBlank()) {
                        Text(current.description, color = dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(if (vm.epg.containsKey(ch.key)) "No guide information" else " ", color = dim, fontSize = 14.sp)
                }
                if (next != null) {
                    Text(
                        "Next: ${next.title}  ${formatTime(next.start)}", color = dim, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
