package com.fenpo.cast.receiver

import android.graphics.Bitmap

/** Whether the receiver is visible to iPhones. */
sealed interface ReceiverStatus {
    /** Starting or restarting after a settings change. */
    data object Starting : ReceiverStatus

    /** Visible on the local network on [port]. */
    data class Ready(
        val port: Int,
    ) : ReceiverStatus

    /** Android 17 and later: local network access has not been granted yet. */
    data object NeedsLocalNetworkPermission : ReceiverStatus

    /** The TV has no network connection. */
    data object NoNetwork : ReceiverStatus

    /** The receiver could not start or advertise itself; [code] is for the log. */
    data class Failed(
        val code: Int,
    ) : ReceiverStatus

    /** Not running (the app is in the background and Fenpo Cast should not stay ready). */
    data object Stopped : ReceiverStatus
}

/**
 * What is playing from a music app.
 *
 * @property positionMs Position when [positionAtMs] was taken (SystemClock.elapsedRealtime).
 */
data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverArt: Bitmap? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val positionAtMs: Long = 0,
)

/** What an iPhone is showing on the TV. */
sealed interface Session {
    /** The iPhone's name, as it introduced itself. */
    val clientName: String

    /** Screen mirroring. [pictureWidth] and [pictureHeight] are 0 until the first frame. */
    data class Mirroring(
        override val clientName: String,
        val pictureWidth: Int = 0,
        val pictureHeight: Int = 0,
        val paused: Boolean = false,
    ) : Session

    /** Audio only, from a music or podcast app. */
    data class Music(
        override val clientName: String,
        val nowPlaying: NowPlaying = NowPlaying(),
    ) : Session

    /** A video handed over as a stream (YouTube and similar apps). */
    data class Stream(
        override val clientName: String,
    ) : Session
}

/** Short messages shown on top of whatever is on screen. */
sealed interface Notice {
    /** An iPhone started showing something. */
    data class Connected(
        val clientName: String,
    ) : Notice

    /** The connection dropped without a goodbye. */
    data object ConnectionLost : Notice

    /** The handed-over video could not be played. */
    data object StreamFailed : Notice
}

/**
 * Everything the screens need.
 *
 * @property deviceName Name the iPhone sees.
 * @property pin Code to show while an iPhone waits for it.
 * @property networkAddress IPv4 address of the TV, for the About section; null without a network.
 */
data class CastState(
    val status: ReceiverStatus = ReceiverStatus.Starting,
    val deviceName: String = "",
    val session: Session? = null,
    val pin: String? = null,
    val notice: Notice? = null,
    val networkAddress: String? = null,
)
