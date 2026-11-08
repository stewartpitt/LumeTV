package com.lumetv.app.ui

import android.app.Activity
import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.lumetv.app.data.Http
import com.lumetv.app.data.PlayRequest
import com.lumetv.app.data.USER_AGENT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem as ExoItem

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer {
    val http = OkHttpDataSource.Factory(Http.client).setUserAgent(USER_AGENT)
    val extractors = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
    val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
        .build()
}

/**
 * Works out the video's frame rate from the timestamps of the first frames (live TS streams
 * rarely state it), then reports it once on the main thread.
 */
@OptIn(UnstableApi::class)
private class FpsProbe(private val onFps: (Float) -> Unit) : VideoFrameMetadataListener {
    private val main = Handler(Looper.getMainLooper())
    private val times = ArrayList<Long>()
    @Volatile private var done = false

    @Synchronized
    fun reset() {
        times.clear()
        done = false
    }

    @Synchronized
    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long, releaseTimeNs: Long, format: Format, mediaFormat: MediaFormat?,
    ) {
        if (done) return
        if (format.frameRate > 0f) {
            report(format.frameRate); return
        }
        if (times.isNotEmpty() && presentationTimeUs <= times.last()) times.clear()  // stream jumped
        times.add(presentationTimeUs)
        if (times.size < 40) return
        val deltas = times.zipWithNext { a, b -> b - a }.filter { it > 0 }.sorted()
        if (deltas.isEmpty()) { times.clear(); return }
        report(1_000_000f / deltas[deltas.size / 2])
    }

    private fun report(raw: Float) {
        done = true
        // Snap to the nearest broadcast rate when close.
        val standard = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
        val fps = standard.minByOrNull { kotlin.math.abs(it - raw) }
            ?.takeIf { kotlin.math.abs(it - raw) / it < 0.03f } ?: raw
        main.post { onFps(fps) }
    }
}

private fun mediaFor(url: String): ExoItem {
    val b = ExoItem.Builder().setUri(url)
    if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    return b.build()
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppViewModel, request: PlayRequest) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { buildPlayer(context) }
    val req by rememberUpdatedState(request)

    var shown by remember { mutableStateOf<PlayRequest?>(null) }
    var currentUrl by remember { mutableStateOf(request.url) }
    var attempts by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf(0) }       // bump to show the title banner
    var bannerVisible by remember { mutableStateOf(true) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    val matcher = remember { (context as? Activity)?.let { FrameRateMatcher(it) } }
    val probe = remember { FpsProbe { fps -> if (vm.matchFrameRate) matcher?.match(fps) } }

    fun start(url: String, positionMs: Long) {
        probe.reset()
        currentUrl = url
        player.setMediaItem(mediaFor(url), positionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
    }

    // ---- player events: retries for live TV, next episode at the end ----
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) { attempts = 0; error = null }
                if (state == Player.STATE_ENDED && !req.live) {
                    vm.saveProgress(req, player.duration, player.duration)
                    val next = vm.nextEpisode()
                    if (next != null) vm.replaceTop(Screen.Player(next)) else vm.back()
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val r = req
                attempts++
                when {
                    // Some providers only serve HLS: try the .m3u8 version of a .ts channel once.
                    r.live && attempts == 1 && currentUrl.endsWith(".ts") ->
                        start(currentUrl.removeSuffix(".ts") + ".m3u8", 0)
                    r.live && attempts <= 6 -> scope.launch {
                        delay(2_000)
                        start(currentUrl, 0)
                    }
                    !r.live && attempts <= 2 -> scope.launch {
                        delay(1_500)
                        start(currentUrl, player.currentPosition)
                    }
                    else -> error = "Can't play this stream (${e.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        player.setVideoFrameMetadataListener(probe)
        onDispose {
            shown?.let { vm.saveProgress(it, player.currentPosition, player.duration) }
            player.removeListener(listener)
            player.clearVideoFrameMetadataListener(probe)
            player.release()
            matcher?.restore()
        }
    }

    // ---- load whenever the request changes (first open, channel zap, next episode) ----
    LaunchedEffect(request.key, request.url) {
        shown?.let { prev -> vm.saveProgress(prev, player.currentPosition, player.duration) }
        shown = request
        attempts = 0
        error = null
        start(request.url, request.startMs)
        banner++
        // save the resume point every 10 seconds
        while (true) {
            delay(10_000)
            if (player.isPlaying) vm.saveProgress(request, player.currentPosition, player.duration)
        }
    }

    LaunchedEffect(banner) {
        bannerVisible = true
        delay(4_000)
        bannerVisible = false
    }

    /** Remote-control handling that the stock controller doesn't do for us. */
    fun handleKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val view = playerView ?: return false
        val controls = view.isControllerFullyVisible
        val r = req
        if (r.live && !controls) {
            val delta = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> 1
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
                else -> 0
            }
            if (delta != 0) {
                vm.zap(r.key, delta)?.let { vm.replaceTop(Screen.Player(it)) }
                return true
            }
        }
        if (!r.live && !controls) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); view.showController(); return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    val d = player.duration
                    val target = player.currentPosition + 30_000
                    player.seekTo(if (d != C.TIME_UNSET && d > 0) target.coerceAtMost(d - 1_000) else target)
                    view.showController(); return true
                }
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && controls) {
            view.hideController(); return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!controls) { view.showController(); banner++; return true }
        }
        return false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                object : PlayerView(ctx) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                        handleKey(event) || super.dispatchKeyEvent(event)
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    controllerAutoShow = false
                    setShowSubtitleButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    keepScreenOn = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    playerView = this
                    post { requestFocus() }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (bannerVisible || error != null) {
            Column(
                Modifier.align(Alignment.TopStart).padding(32.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xCC0E1317)).padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(request.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                request.subtitle?.let { Text(it, color = Muted, fontSize = 15.sp) }
                if (request.live) Text("▲ ▼  change channel", color = Muted, fontSize = 12.sp)
                error?.let { Text(it, color = Accent, fontSize = 15.sp) }
            }
        }

        if (buffering && error == null) {
            Text(
                "Loading…", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000)).padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
