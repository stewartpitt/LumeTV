package com.lumetv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text

@Composable
fun HomeScreen(vm: AppViewModel) {
    val tabFocus = remember { HomeTab.entries.associateWith { FocusRequester() } }
    InitialFocus(vm, tabFocus.getValue(vm.homeTab))

    Row(Modifier.fillMaxSize()) {
        // ---- left navigation ----
        Column(
            Modifier.width(200.dp).fillMaxHeight().background(Panel).padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Lume TV", color = Brand, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 20.dp),
            )
            HomeTab.entries.forEach { tab ->
                ListItem(
                    selected = vm.homeTab == tab,
                    onClick = { vm.homeTab = tab },
                    headlineContent = { Text(tab.label) },
                    colors = ListItemDefaults.colors(
                        selectedContainerColor = Brand.copy(alpha = 0.25f),
                        focusedContainerColor = Brand,
                    ),
                    modifier = Modifier
                        .focusMemory(vm, "tab:$tab", tabFocus.getValue(tab))
                        .onFocusChanged { if (it.isFocused) vm.homeTab = tab },
                )
            }
        }

        // ---- content ----
        Box(Modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 24.dp)) {
            when (vm.homeTab) {
                HomeTab.LIVE -> LiveTab(vm)
                HomeTab.MOVIES -> BrowseTab(vm, vm.movies)
                HomeTab.SERIES -> BrowseTab(vm, vm.series)
                HomeTab.MY_LIST -> MyListTab(vm)
                HomeTab.SETTINGS -> SettingsTab(vm)
            }
        }
    }
}

@Composable
fun SettingsTab(vm: AppViewModel) {
    val account = vm.store.account
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Settings")
        Text("Playlist: ${account?.name ?: "-"}", color = Muted)
        Text(
            if (account?.isXtream == true) "Xtream Codes · ${account.server}" else "M3U · ${account?.m3uUrl ?: ""}",
            color = Muted,
        )
        Button(onClick = { vm.refreshAll() }) { Text("Refresh channels and films") }
        Button(onClick = { vm.logout() }) { Text("Change playlist / sign out") }
        Text(
            "Tip: hold OK on any channel, film or series to add it to My List.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 20.dp),
        )
    }
}
