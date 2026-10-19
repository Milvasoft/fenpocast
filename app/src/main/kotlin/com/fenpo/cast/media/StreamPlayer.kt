package com.fenpo.cast.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a handed-over video stands, for the screen and for the iPhone's scrubber. */
data class StreamState(
    val buffering: Boolean = true,
    val playing: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Plays videos the iPhone hands over as an HLS stream (YouTube and similar apps) with Media3.
 * The receiver serves the playlists on localhost; the iPhone drives play, pause and seek.
 *
 * Every method must be called on the main thread; [NativeReceiver] callbacks are moved there
 * by the caller.
 *
 * @param reportPlayback Receives playback state twice a second: duration and position in
 *   seconds, rate, whether the buffer is empty, whether it is full, whether the stream ended.
 * @param onEnded Called once when the stream plays to its end.
 * @param onFailed Called when the stream cannot be played.
 */
@OptIn(UnstableApi::class)
class StreamPlayer(
    private val context: Context,
    private val reportPlayback: (Double, Double, Float, Boolean, Boolean, Boolean) -> Unit,
    private val onEnded: () -> Unit,
    private val onFailed: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var endedReported = false
    private var volume = 1f

    private val _state = MutableStateFlow(StreamState())

    /** State of the current stream. */
    val state: StateFlow<StreamState> = _state.asStateFlow()

    /** True while a stream is loaded. */
    val isActive: Boolean get() = player != null

    /** Starts [url] at [startSeconds]. */
    fun play(
        url: String,
        startSeconds: Float,
    ) {
        stop()
        endedReported = false
        _state.value = StreamState()
        val newPlayer =
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(
                    MediaItem
                        .Builder()
                        .setUri(url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build(),
                )
                seekTo((startSeconds * 1000).toLong().coerceAtLeast(0))
                volume = this@StreamPlayer.volume
                playWhenReady = true
                addListener(listener)
                prepare()
            }
        surfaceView?.let(newPlayer::setVideoSurfaceView)
        player = newPlayer
        handler.post(reporter)
    }

    /** Seeks to [seconds]. */
    fun seek(seconds: Float) {
        player?.seekTo((seconds * 1000).toLong().coerceAtLeast(0))
    }

    /** 0 pauses, anything else plays. */
    fun setRate(rate: Float) {
        player?.playWhenReady = rate != 0f
    }

    /** Pauses playback. */
    fun pause() {
        player?.playWhenReady = false
    }

    /** Applies the iPhone's volume as a 0 to 1 gain. */
    fun setVolume(gain: Float) {
        volume = gain
        player?.volume = gain
    }

    /** Shows the video on [view], or nowhere when null. */
    fun attach(view: SurfaceView?) {
        surfaceView = view
        if (view != null) player?.setVideoSurfaceView(view) else player?.clearVideoSurface()
    }

    /** Ends the stream. */
    fun stop() {
        handler.removeCallbacks(reporter)
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        _state.value = StreamState(buffering = false)
    }

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                update()
                if (playbackState == Player.STATE_ENDED && !endedReported) {
                    endedReported = true
                    report()
                    onEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = update()

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(failed = true, buffering = false)
                onFailed()
            }
        }

    private fun update() {
        val current = player ?: return
        val state = current.playbackState
        _state.value =
            StreamState(
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE,
                playing = current.isPlaying,
                failed = current.playerError != null,
            )
    }

    private val reporter =
        object : Runnable {
            override fun run() {
                report()
                handler.postDelayed(this, REPORT_INTERVAL_MS)
            }
        }

    private fun report() {
        val current = player ?: return
        val duration = if (current.duration == C.TIME_UNSET) 0.0 else current.duration / 1000.0
        val position = current.currentPosition / 1000.0
        val buffering = current.playbackState == Player.STATE_BUFFERING
        reportPlayback(
            duration,
            position,
            if (current.isPlaying) 1f else 0f,
            buffering,
            !buffering,
            current.playbackState == Player.STATE_ENDED,
        )
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 500L
    }
}
