package com.fenpo.cast.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import com.fenpo.cast.media.StreamState
import com.fenpo.cast.receiver.Notice
import com.fenpo.cast.receiver.NowPlaying
import com.fenpo.cast.receiver.ReceiverStatus
import com.fenpo.cast.receiver.Session
import com.fenpo.cast.settings.CastSettings
import com.fenpo.cast.ui.home.HomeScreen
import com.fenpo.cast.ui.session.MirrorScreen
import com.fenpo.cast.ui.session.MusicScreen
import com.fenpo.cast.ui.session.NoticeBanner
import com.fenpo.cast.ui.session.PinOverlay
import com.fenpo.cast.ui.session.StreamScreen
import com.fenpo.cast.ui.settings.SettingsActions
import com.fenpo.cast.ui.settings.SettingsScreen
import com.fenpo.cast.ui.theme.FenpoCastTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen in light and dark, English and Turkish, on a 1080p TV (960 x 540 dp). Images go to
 * `src/test/screenshots/`; `recordRoborazziDebug` writes them, `verifyRoborazziDebug` compares.
 * Session screens can only be seen here or on a real TV with an iPhone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    private val name = "Salon TV"
    private val client = "Buğra’nın iPhone’u"

    @Test
    fun home() {
        capture("home_ready") { Home(ReceiverStatus.Ready(7000)) }
        capture("home_no_network") { Home(ReceiverStatus.NoNetwork) }
        capture("home_permission") { Home(ReceiverStatus.NeedsLocalNetworkPermission) }
        capture("home_failed") { Home(ReceiverStatus.Failed(-7)) }
    }

    @Test
    fun settings() {
        capture("settings") {
            SettingsScreen(
                settings = CastSettings(),
                deviceName = name,
                language = AppLanguage.System,
                networkAddress = "192.168.1.42",
                actions = SettingsActions({}, {}, {}, {}, {}, {}),
            )
        }
    }

    @Test
    fun music() {
        val nowPlaying =
            NowPlaying(
                title = "Kıyıda Bir Akşam",
                artist = "Deniz Aral",
                album = "Kıyı",
                coverArt = cover(),
                durationMs = 245_000,
                positionMs = 83_000,
                positionAtMs = SystemClock.elapsedRealtime(),
            )
        capture("music") { MusicScreen(Session.Music(client, nowPlaying)) }
        capture("music_no_metadata") { MusicScreen(Session.Music(client)) }
    }

    @Test
    fun sessionOverlays() {
        capture("pin") {
            Home(ReceiverStatus.Ready(7000))
            PinOverlay("4821")
        }
        capture("notice_connected") {
            Home(ReceiverStatus.Ready(7000))
            NoticeBanner(Notice.Connected(client))
        }
        capture("notice_lost") {
            Home(ReceiverStatus.Ready(7000))
            NoticeBanner(Notice.ConnectionLost)
        }
        capture("mirror_waiting") { MirrorScreen(Session.Mirroring(client), onSurface = {}) }
        capture("mirror_paused") {
            MirrorScreen(Session.Mirroring(client, 1170, 2532, paused = true), onSurface = {})
        }
        capture("stream_loading") { StreamScreen(StreamState(buffering = true), onView = {}) }
    }

    @Composable
    private fun Home(status: ReceiverStatus) {
        HomeScreen(deviceName = name, status = status, onOpenSettings = {}, onGrantPermission = {}, onRetry = {})
    }

    private fun cover(): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val paint =
            Paint().apply {
                // Çiçek to Alacakaranlık, standing in for an album cover.
                shader = LinearGradient(0f, 0f, SIZE, SIZE, CICEK, ALACAKARANLIK, Shader.TileMode.CLAMP)
            }
        Canvas(bitmap).drawRect(0f, 0f, SIZE, SIZE, paint)
        return bitmap
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        val base = RuntimeEnvironment.getQualifiers()
        try {
            for (language in listOf("en", "tr")) {
                for (dark in listOf(false, true)) {
                    RuntimeEnvironment.setQualifiers("+$language")
                    captureRoboImage(
                        filePath = "src/test/screenshots/${name}_${language}_${if (dark) "dark" else "light"}.png",
                        roborazziOptions =
                            RoborazziOptions(
                                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
                            ),
                    ) {
                        FenpoCastTheme(darkTheme = dark) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                content()
                            }
                        }
                    }
                }
            }
        } finally {
            RuntimeEnvironment.setQualifiers(base)
        }
    }

    private companion object {
        const val SIZE = 360f
        const val CICEK = 0xFFE8577E.toInt()
        const val ALACAKARANLIK = 0xFF4E54C8.toInt()
    }
}
