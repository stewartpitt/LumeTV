package com.lumetv.app.ui

import android.app.Activity
import android.os.Build
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Switches the TV's refresh rate to match the video, e.g. 50Hz for 25/50fps UK broadcasts.
 * Without this the Fire Stick plays 25fps video on a 60Hz output, which looks juddery.
 */
class FrameRateMatcher(private val activity: Activity) {

    private val originalModeId: Int =
        if (Build.VERSION.SDK_INT >= 23) activity.window.attributes.preferredDisplayModeId else 0
    private var changed = false
    private var lastFps = 0f

    fun match(fps: Float) {
        if (Build.VERSION.SDK_INT < 23 || fps < 10f || fps > 130f) return
        if (abs(fps - lastFps) < 0.5f) return
        lastFps = fps

        @Suppress("DEPRECATION")
        val display = activity.window.decorView.display ?: activity.windowManager.defaultDisplay ?: return
        val current = display.mode

        // Same resolution as now, with a refresh rate that is a whole multiple of the video's frame rate.
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .mapNotNull { mode ->
                val ratio = mode.refreshRate / fps
                val k = ratio.roundToInt()
                if (k >= 1 && abs(ratio - k) < 0.01f * k) mode to k else null
            }
            // Prefer 48Hz and above (e.g. 50Hz over 25Hz), then the smallest multiple.
            .sortedWith(compareBy({ if (it.first.refreshRate < 47f) 1 else 0 }, { it.second }))
            .firstOrNull()?.first ?: return

        if (best.modeId == current.modeId) return
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs
        changed = true
    }

    fun restore() {
        if (!changed || Build.VERSION.SDK_INT < 23) return
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = originalModeId
        activity.window.attributes = attrs
        changed = false
        lastFps = 0f
    }
}
