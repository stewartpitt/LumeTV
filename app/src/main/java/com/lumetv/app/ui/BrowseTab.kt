package com.lumetv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumetv.app.data.ContentType

/** Movies or Series: categories on the left, poster grid on the right. */
@Composable
fun BrowseTab(vm: AppViewModel, catalog: Catalog) {
    LaunchedEffect(catalog.type) { vm.ensureLoaded(catalog) }

    LaunchedEffect(catalog.items) {
        if (catalog.scrollToTop && catalog.items.isNotEmpty()) {
            runCatching { catalog.gridState.scrollToItem(0) }
            catalog.scrollToTop = false
        }
    }

    if (catalog.categories.isEmpty()) {
        when {
            catalog.error != null -> Message(catalog.error!!) { vm.retry(catalog) }
            else -> Message(if (catalog.type == ContentType.MOVIE) "Loading films…" else "Loading series…")
        }
        return
    }

    val filtered = remember(catalog.items, catalog.filter) {
        val q = catalog.filter.trim()
        if (q.isEmpty()) catalog.items else catalog.items.filter { it.name.contains(q, ignoreCase = true) }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CategoryColumn(vm, catalog)

        Column(Modifier.fillMaxSize()) {
            TvField("Search this category", catalog.filter) { catalog.filter = it }
            when {
                catalog.loading && catalog.items.isEmpty() -> Message("Loading…")
                catalog.error != null -> Message(catalog.error!!) { vm.retry(catalog) }
                filtered.isEmpty() -> Message("Nothing here")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    state = catalog.gridState,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    items(filtered, key = { it.key }) { item ->
                        PosterCard(
                            vm = vm,
                            item = item,
                            progress = vm.progress[item.key]?.fraction,
                            onClick = {
                                if (item.type == ContentType.SERIES) vm.push(Screen.SeriesDetail(item))
                                else vm.push(Screen.MovieDetail(item))
                            },
                        )
                    }
                }
            }
        }
    }
}
