package com.fenpo.cast.receiver

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.fenpo.cast.media.VideoDecoder
import com.fenpo.cast.settings.VideoQuality
import kotlin.math.roundToInt

/**
 * Picture the iPhone is asked to send.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 * @property refreshRate Display refresh rate in Hz.
 * @property maxFps Highest frame rate the iPhone may send.
 * @property h265 Whether H.265 is allowed (only for 4K, as UxPlay does).
 */
data class DisplayProfile(
    val width: Int,
    val height: Int,
    val refreshRate: Int,
    val maxFps: Int,
    val h265: Boolean,
) {
    companion object {
        /** Works out the profile for this TV's screen and decoders under [quality]. */
        fun detect(
            context: Context,
            quality: VideoQuality,
        ): DisplayProfile {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val mode = display?.mode
            val physicalWidth = maxOf(mode?.physicalWidth ?: 1920, mode?.physicalHeight ?: 1080)
            val refresh = (mode?.refreshRate ?: 60f).roundToInt().coerceIn(24, 120)
            val maxFps = refresh.coerceAtMost(60)
            return when (quality) {
                VideoQuality.Auto -> {
                    if (physicalWidth >= 3840 && VideoDecoder.supportsHevc(3840, 2160)) {
                        DisplayProfile(3840, 2160, refresh, maxFps, h265 = true)
                    } else {
                        DisplayProfile(1920, 1080, refresh, maxFps, h265 = false)
                    }
                }

                VideoQuality.P1080 -> {
                    DisplayProfile(1920, 1080, refresh, maxFps, h265 = false)
                }

                VideoQuality.P720 -> {
                    DisplayProfile(1280, 720, refresh, maxFps, h265 = false)
                }
            }
        }
    }
}
