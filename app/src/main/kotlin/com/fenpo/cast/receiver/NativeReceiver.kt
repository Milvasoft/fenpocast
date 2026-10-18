package com.fenpo.cast.receiver

/**
 * Settings the native receiver is started with.
 *
 * @property name Name shown in the iPhone's screen mirroring list.
 * @property deviceId Receiver identity in `aa:bb:cc:dd:ee:ff` form; the iPhone remembers it.
 * @property keyFile Where the receiver's Ed25519 key is kept so the identity survives restarts.
 * @property width Display width the iPhone encodes for.
 * @property height Display height the iPhone encodes for.
 * @property refreshRate Display refresh rate in Hz.
 * @property maxFps Highest frame rate the iPhone may send.
 * @property h265 Whether the iPhone may send H.265 (needed for 4K).
 * @property hls Whether videos may be handed over as a stream instead of mirrored.
 * @property pinRequired Whether every connection needs the four-digit code shown on screen.
 * @property languages Preferred audio and subtitle languages for streams, colon separated (`tr:en`).
 */
data class ReceiverConfig(
    val name: String,
    val deviceId: String,
    val keyFile: String,
    val width: Int,
    val height: Int,
    val refreshRate: Int,
    val maxFps: Int,
    val h265: Boolean,
    val hls: Boolean,
    val pinRequired: Boolean,
    val languages: String,
)

/**
 * What the native receiver reports. Every method is called on one of the receiver's own threads,
 * so implementations must hand UI work to the main thread themselves.
 */
interface ReceiverListener {
    /** Advertises a DNS-SD service; returns false when NsdManager refused it. */
    fun registerService(
        type: String,
        name: String,
        port: Int,
        attributes: Map<String, String>,
    ): Boolean

    /** Withdraws a service registered with [registerService]. */
    fun unregisterService(type: String)

    /** An iPhone asked to connect. */
    fun onClientRequest(
        deviceId: String,
        model: String,
        name: String,
    )

    /** The number of open connections changed; zero means every client left. */
    fun onConnectionsChanged(count: Int)

    /** The connection dropped without a goodbye (network problem or a stream the TV cannot play). */
    fun onConnectionLost(reason: Int)

    /** Asks whether the decoder can play this codec; false makes the iPhone fall back or give up. */
    fun onVideoCodec(h265: Boolean): Boolean

    /** The iPhone's screen size in pixels. */
    fun onVideoSize(
        width: Int,
        height: Int,
    )

    /** One access unit in Annex B form; key frames start with their parameter sets. */
    fun onVideoFrame(
        data: ByteArray,
        h265: Boolean,
    )

    /** The iPhone paused (locked, or left the mirrored app) or resumed the screen stream. */
    fun onVideoPaused(paused: Boolean)

    /** Drop queued video. */
    fun onVideoFlush()

    /** The video side ended or changed hands; see [VideoResetType]. */
    fun onVideoReset(type: VideoResetType)

    /** Audio is about to start in [format]. */
    fun onAudioFormat(format: AudioFormat)

    /** One audio packet in the format last given to [onAudioFormat]. */
    fun onAudioFrame(
        format: AudioFormat,
        data: ByteArray,
    )

    /** Drop queued audio (pause or seek on the iPhone). */
    fun onAudioFlush()

    /** Stop audio output. */
    fun onAudioStop()

    /** iPhone volume in dB: -30 to 0, or -144 for mute. */
    fun onVolume(volumeDb: Float)

    /** DMAP track information (title, artist, album). */
    fun onMetadata(data: ByteArray)

    /** Cover art of the current track, usually a JPEG. */
    fun onCoverArt(data: ByteArray)

    /** The current track has no cover art. */
    fun onCoverArtCleared()

    /** Track progress as RTP timestamps at 44.1 kHz. */
    fun onProgress(
        start: Long,
        current: Long,
        end: Long,
    )

    /** Show this code; the iPhone user types it to connect. */
    fun onPin(pin: String)

    /** Play a stream from the receiver's local HTTP server, starting at [startPosition] seconds. */
    fun onHlsPlay(
        url: String,
        startPosition: Float,
    )

    /** Seek the stream to [position] seconds. */
    fun onHlsSeek(position: Float)

    /** Playback rate: 0 pauses, 1 plays. */
    fun onHlsRate(rate: Float)

    /** The iPhone stopped the stream. */
    fun onHlsStop()

    /** Pause the stream; the iPhone is swapping the playlist. */
    fun onHlsPause()
}

/** Audio formats AirPlay uses; the native side decodes ALAC itself and passes it on as [Pcm]. */
enum class AudioFormat(
    val code: Int,
) {
    Pcm(1),
    AacLc(4),
    AacEld(8),
    ;

    companion object {
        /** Maps the native compression type (ct) to a format, or null for one Fenpo Cast does not play. */
        fun fromCode(code: Int): AudioFormat? = entries.firstOrNull { it.code == code }
    }
}

/** Mirrors UxPlay's reset_type_t. */
enum class VideoResetType {
    NoHold,
    RtpShutdown,
    HlsShutdown,
    HlsEndOfStream,
    OnVideoPlay,
    RtpToHlsTeardown,
}

/**
 * Kotlin side of receiver.c: starts and stops UxPlay's receiver core and turns its callbacks
 * into [ReceiverListener] calls. Only one receiver runs per process.
 */
class NativeReceiver(
    private val listener: ReceiverListener,
) {
    /**
     * Starts listening and advertises the receiver on the local network.
     *
     * @return The TCP port of the receiver, or a negative native error code.
     */
    fun start(config: ReceiverConfig): Int =
        nativeStart(
            config.name,
            config.deviceId,
            config.keyFile,
            config.width,
            config.height,
            config.refreshRate,
            config.maxFps,
            config.h265,
            config.hls,
            config.pinRequired,
            config.languages,
        )

    /** Closes every connection and withdraws the advertisement. */
    fun stop() = nativeStop()

    /** Drops the connected iPhone; the receiver stays visible for the next one. */
    fun disconnect() = nativeDisconnect()

    /** Reports stream playback state; the iPhone polls it to draw its own scrubber. */
    fun setPlaybackInfo(
        duration: Double,
        position: Double,
        rate: Float,
        bufferEmpty: Boolean,
        bufferFull: Boolean,
        ended: Boolean,
    ) = nativeSetPlaybackInfo(duration, position, rate, bufferEmpty, bufferFull, ended)

    /** Tells the iPhone the stream reached its end. */
    fun hlsEnded() = nativeHlsEnded()

    // Called from receiver.c. Names and signatures are looked up with GetMethodID.

    @Suppress("unused")
    private fun registerService(
        type: String,
        name: String,
        port: Int,
        keys: Array<String>,
        values: Array<String>,
    ) = listener.registerService(type, name, port, keys.zip(values).toMap())

    @Suppress("unused")
    private fun unregisterService(type: String) = listener.unregisterService(type)

    @Suppress("unused")
    private fun onClientRequest(
        deviceId: String,
        model: String,
        name: String,
    ) = listener.onClientRequest(deviceId, model, name)

    @Suppress("unused")
    private fun onConnectionsChanged(count: Int) = listener.onConnectionsChanged(count)

    @Suppress("unused")
    private fun onConnectionLost(reason: Int) = listener.onConnectionLost(reason)

    @Suppress("unused")
    private fun onVideoCodec(h265: Boolean): Boolean = listener.onVideoCodec(h265)

    @Suppress("unused")
    private fun onVideoSize(
        width: Float,
        height: Float,
    ) = listener.onVideoSize(width.toInt(), height.toInt())

    @Suppress("unused")
    private fun onVideoFrame(
        data: ByteArray,
        h265: Boolean,
    ) = listener.onVideoFrame(data, h265)

    @Suppress("unused")
    private fun onVideoPaused(paused: Boolean) = listener.onVideoPaused(paused)

    @Suppress("unused")
    private fun onVideoFlush() = listener.onVideoFlush()

    @Suppress("unused")
    private fun onVideoReset(type: Int) {
        VideoResetType.entries.getOrNull(type)?.let(listener::onVideoReset)
    }

    @Suppress("unused")
    private fun onAudioFormat(code: Int) {
        AudioFormat.fromCode(code)?.let(listener::onAudioFormat)
    }

    @Suppress("unused")
    private fun onAudioFrame(
        code: Int,
        data: ByteArray,
    ) {
        AudioFormat.fromCode(code)?.let { listener.onAudioFrame(it, data) }
    }

    @Suppress("unused")
    private fun onAudioFlush() = listener.onAudioFlush()

    @Suppress("unused")
    private fun onAudioStop() = listener.onAudioStop()

    @Suppress("unused")
    private fun onVolume(volumeDb: Float) = listener.onVolume(volumeDb)

    @Suppress("unused")
    private fun onMetadata(data: ByteArray) = listener.onMetadata(data)

    @Suppress("unused")
    private fun onCoverArt(data: ByteArray) = listener.onCoverArt(data)

    @Suppress("unused")
    private fun onCoverArtCleared() = listener.onCoverArtCleared()

    @Suppress("unused")
    private fun onProgress(
        start: Long,
        current: Long,
        end: Long,
    ) = listener.onProgress(start, current, end)

    @Suppress("unused")
    private fun onPin(pin: String) = listener.onPin(pin)

    @Suppress("unused")
    private fun onHlsPlay(
        url: String,
        startPosition: Float,
    ) = listener.onHlsPlay(url, startPosition)

    @Suppress("unused")
    private fun onHlsSeek(position: Float) = listener.onHlsSeek(position)

    @Suppress("unused")
    private fun onHlsRate(rate: Float) = listener.onHlsRate(rate)

    @Suppress("unused")
    private fun onHlsStop() = listener.onHlsStop()

    @Suppress("unused")
    private fun onHlsPause() = listener.onHlsPause()

    private external fun nativeStart(
        name: String,
        deviceId: String,
        keyFile: String,
        width: Int,
        height: Int,
        refreshRate: Int,
        maxFps: Int,
        h265: Boolean,
        hls: Boolean,
        pinRequired: Boolean,
        languages: String,
    ): Int

    private external fun nativeStop()

    private external fun nativeDisconnect()

    private external fun nativeSetPlaybackInfo(
        duration: Double,
        position: Double,
        rate: Float,
        bufferEmpty: Boolean,
        bufferFull: Boolean,
        ended: Boolean,
    )

    private external fun nativeHlsEnded()

    private companion object {
        init {
            System.loadLibrary("fenpocast")
        }
    }
}
