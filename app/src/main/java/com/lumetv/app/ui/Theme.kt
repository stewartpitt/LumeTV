package com.lumetv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Brand = Color(0xFF00AAFF)
val Accent = Color(0xFFFF8D00)
val Ink = Color(0xFF1C242A)
val Background = Color(0xFF0E1317)
val Panel = Color(0xFF161D22)
val Muted = Color(0xFF9AA6AE)

@Composable
fun LumeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            onPrimary = Color.White,
            secondary = Accent,
            background = Background,
            surface = Panel,
            onSurface = Color.White,
            onBackground = Color.White,
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Background)) { content() }
        }
    }
}
