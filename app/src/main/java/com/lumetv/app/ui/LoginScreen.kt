package com.lumetv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.lumetv.app.data.Account
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel) {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    InitialFocus(vm, first)

    fun submit() {
        if (busy) return
        val account = if (xtream) {
            if (server.isBlank() || username.isBlank() || password.isBlank()) {
                error = "Please fill in the server, username and password"; return
            }
            Account("xtream", name.ifBlank { "My playlist" }, server.trim(), username.trim(), password.trim())
        } else {
            if (m3u.isBlank()) { error = "Please enter the playlist link"; return }
            Account("m3u", name.ifBlank { "My playlist" }, m3uUrl = m3u.trim())
        }
        busy = true
        error = null
        scope.launch {
            error = vm.login(account)
            busy = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Lume TV", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Brand)
            Text("Add your IPTV provider to get started", color = Muted)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeButton("Xtream Codes", xtream, Modifier.focusRequester(first)) { xtream = true }
                ModeButton("M3U link", !xtream) { xtream = false }
            }

            TvField("Playlist name (optional)", name) { name = it }
            if (xtream) {
                TvField("Server URL  e.g. http://example.com:8080", server, KeyboardType.Uri) { server = it }
                TvField("Username", username) { username = it }
                TvField("Password", password, KeyboardType.Password, secret = true) { password = it }
            } else {
                TvField("M3U / M3U8 playlist URL", m3u, KeyboardType.Uri) { m3u = it }
            }

            error?.let { Text(it, color = Accent) }

            Button(onClick = { submit() }, enabled = !busy) {
                Text(if (busy) "Connecting…" else "Connect")
            }
            Text(
                "Lume TV is a player only. Use it with a service you are entitled to watch.",
                color = Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Brand else Panel,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Ink,
        ),
    ) { Text(label) }
}

/**
 * Text field that plays nicely with a TV remote: up/down always move focus out of the
 * field instead of getting stuck moving the cursor.
 */
@Composable
fun TvField(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Brand,
            unfocusedBorderColor = Color(0x55FFFFFF),
            focusedLabelColor = Brand,
            unfocusedLabelColor = Muted,
            cursorColor = Brand,
        ),
        modifier = Modifier
            .width(560.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
    )
}
