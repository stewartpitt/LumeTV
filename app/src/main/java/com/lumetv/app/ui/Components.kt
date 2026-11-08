package com.lumetv.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.lumetv.app.data.Category
import com.lumetv.app.data.MediaItem

/**
 * Remembers which item had focus so it can be focused again when the user comes back
 * from the player or a detail page.
 */
@Composable
fun Modifier.focusMemory(vm: AppViewModel, key: String, requester: FocusRequester? = null): Modifier {
    val fr = requester ?: remember { FocusRequester() }
    val isTarget = vm.restoreKey == key
    LaunchedEffect(isTarget) {
        if (isTarget) {
            withFrameNanos { }
            if (runCatching { fr.requestFocus() }.isSuccess) vm.restoreKey = null
        }
    }
    return this
        .focusRequester(fr)
        .onFocusChanged { if (it.isFocused) vm.lastFocus = key }
}

/** Ask for focus once the screen has drawn, unless we're restoring an earlier position. */
@Composable
fun InitialFocus(vm: AppViewModel, requester: FocusRequester, key: Any = Unit) {
    LaunchedEffect(key) {
        withFrameNanos { }
        if (vm.restoreKey == null) runCatching { requester.requestFocus() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
}

@Composable
fun Message(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Muted, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Try again") }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(Brand))
    }
}

/** Left-hand list of categories shared by Live TV, Movies and Series. */
@Composable
fun CategoryColumn(vm: AppViewModel, catalog: Catalog, modifier: Modifier = Modifier) {
    LazyColumn(
        state = catalog.categoryState,
        modifier = modifier.width(250.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(catalog.categories, key = { _, c -> c.id }) { _, cat: Category ->
            ListItem(
                selected = cat.id == catalog.selected,
                onClick = { vm.selectCategory(catalog, cat.id) },
                headlineContent = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = ListItemDefaults.colors(
                    selectedContainerColor = Brand.copy(alpha = 0.25f),
                    focusedContainerColor = Brand,
                ),
                modifier = Modifier.focusMemory(vm, "cat:${catalog.type}:${cat.id}"),
            )
        }
    }
}

/** Poster card for movies and series. Hold OK to add or remove from favourites. */
@Composable
fun PosterCard(
    vm: AppViewModel,
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    focusKey: String = item.key,
) {
    val fav = vm.isFavourite(item)
    Column(modifier.width(150.dp)) {
        Card(
            onClick = onClick,
            onLongClick = { vm.toggleFavourite(item) },
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Brand))),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).focusMemory(vm, focusKey),
        ) {
            Box(Modifier.fillMaxSize().background(Panel)) {
                Text(
                    item.name, color = Muted, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp), textAlign = TextAlign.Center,
                )
                AsyncImage(
                    model = item.image, contentDescription = item.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                if (fav) {
                    Text(
                        "★", color = Accent, fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
                if (progress != null && progress > 0f) {
                    ProgressLine(progress, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Text(
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
            color = Color.White, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun Logo(url: String?, size: Int = 48) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
    }
}

fun formatTime(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}
