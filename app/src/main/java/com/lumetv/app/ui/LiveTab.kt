package com.lumetv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.lumetv.app.data.EpgEntry
import com.lumetv.app.data.MediaItem
import kotlinx.coroutines.delay

@Composable
fun LiveTab(vm: AppViewModel) {
    val catalog = vm.live
    LaunchedEffect(Unit) { vm.ensureLoaded(catalog) }

    var focused by remember { mutableStateOf<MediaItem?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>>(emptyList()) }

    // Fetch now & next for the highlighted channel, after a short pause so scrolling stays smooth.
    LaunchedEffect(focused?.key) {
        epg = emptyList()
        val item = focused ?: return@LaunchedEffect
        delay(400)
        epg = runCatching { vm.provider?.shortEpg(item) ?: emptyList() }.getOrDefault(emptyList())
    }

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

        Column(Modifier.width(420.dp).fillMaxHeight()) {
            when {
                catalog.loading && catalog.items.isEmpty() -> Message("Loading…")
                catalog.error != null -> Message(catalog.error!!) { vm.retry(catalog) }
                catalog.items.isEmpty() -> Message("No channels in this category")
                else -> LazyColumn(state = catalog.listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(catalog.items, key = { it.key }) { ch ->
                        val fav = vm.isFavourite(ch)
                        ListItem(
                            selected = false,
                            onClick = { vm.playLive(ch, catalog.items) },
                            onLongClick = { vm.toggleFavourite(ch) },
                            leadingContent = { Logo(ch.image, 40) },
                            headlineContent = { Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = if (fav) ({ Text("★", color = Accent) }) else null,
                            colors = ListItemDefaults.colors(focusedContainerColor = Brand),
                            modifier = Modifier
                                .focusMemory(vm, ch.key)
                                .onFocusChanged { if (it.isFocused) focused = ch },
                        )
                    }
                }
            }
        }

        // ---- info panel: logo + now & next ----
        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            val ch = focused
            if (ch != null) {
                Logo(ch.image, 96)
                Spacer(Modifier.height(12.dp))
                Text(ch.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(16.dp))
                if (epg.isEmpty()) {
                    Text("No guide information", color = Muted)
                } else {
                    epg.take(3).forEachIndexed { i, e ->
                        Text(
                            (if (i == 0) "NOW  " else "NEXT  ") + "${formatTime(e.start)} – ${formatTime(e.end)}",
                            color = if (i == 0) Brand else Muted, fontSize = 13.sp,
                        )
                        Text(e.title, color = Color.White, fontSize = 17.sp)
                        if (i == 0) {
                            e.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = Muted, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            val now = System.currentTimeMillis()
                            val span = (e.end - e.start).coerceAtLeast(1)
                            ProgressLine(((now - e.start).toFloat() / span), Modifier.padding(top = 8.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }
                Text("Hold OK to add to My List", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
