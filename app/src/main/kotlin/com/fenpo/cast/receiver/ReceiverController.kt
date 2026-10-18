package com.fenpo.cast.receiver

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatDelegate
import com.fenpo.cast.R
import com.fenpo.cast.media.AudioRenderer
import com.fenpo.cast.media.DmapParser
import com.fenpo.cast.media.StreamPlayer
import com.fenpo.cast.media.StreamState
import com.fenpo.cast.media.VideoDecoder
import com.fenpo.cast.media.VolumeMapping
import com.fenpo.cast.settings.CastSettings
import com.fenpo.cast.settings.SettingsRepository
import com.fenpo.cast.settings.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.util.Locale

/**
 * Owns the receiver for the whole process: starts it with the current settings, restarts it when
 * a setting that the iPhone sees changes, and routes what the iPhone sends to the decoders.
 * The UI reads [state]; the foreground service decides when the receiver runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Native start and stop never overlap.
    private val receiverDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val advertiser = ServiceAdvertiser(context, ::onAdvertiseFailed)
    private val nativeReceiver = NativeReceiver(Listener())
    private val audio = AudioRenderer(context)
    private val streamPlayer = StreamPlayer(context, nativeReceiver::setPlaybackInfo, ::onStreamEnded)

    @Volatile
    private var video: VideoDecoder? = null

    @Volatile
    private var profile: DisplayProfile? = null

    @Volatile
    private var clientName: String = ""

    private var videoSurface: Surface? = null
    private var runJob: Job? = null
    private var noticeJob: Job? = null
    private var pinJob: Job? = null
    private var networkCallbackRegistered = false

    private val _state = MutableStateFlow(CastState())

    /** Current receiver and session state. */
    val state: StateFlow<CastState> = _state.asStateFlow()

    /** State of a handed-over video. */
    val streamState: StateFlow<StreamState> = streamPlayer.state

    private val _sessionStarted =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits when an iPhone starts showing something; the service brings the app forward. */
    val sessionStarted: SharedFlow<Unit> = _sessionStarted.asSharedFlow()

    /** True while the receiver should be running. */
    val isRunning: Boolean get() = runJob != null

    /** Starts the receiver and keeps it in line with the settings until [stop]. */
    fun start() {
        if (runJob != null) return
        registerNetworkCallback()
        runJob =
            scope.launch {
                settingsRepository.settings
                    .map { ReceiverInputs.from(it) }
                    .distinctUntilChanged()
                    .collect { inputs -> restart(inputs) }
            }
    }

    /** Stops the receiver; iPhones no longer see the TV. */
    fun stop() {
        runJob?.cancel()
        runJob = null
        scope.launch {
            endSession()
            withContext(receiverDispatcher) { nativeReceiver.stop() }
            advertiser.unregisterAll()
            releaseVideo()
            _state.update { it.copy(status = ReceiverStatus.Stopped) }
        }
    }

    /** Starts again after the user granted local network access. */
    fun retry() {
        if (runJob == null) return
        runJob?.cancel()
        runJob = null
        start()
    }

    /** Drops the connected iPhone. */
    fun disconnect() {
        nativeReceiver.disconnect()
        endSession()
    }

    /** Hides the code once the user dismissed it. */
    fun dismissPin() {
        _state.update { it.copy(pin = null) }
    }

    /** The mirrored screen's surface appeared or went away. */
    fun setVideoSurface(surface: Surface?) {
        videoSurface = surface
        video?.setSurface(surface)
    }

    /** The surface handed-over videos are drawn on. */
    fun setStreamView(view: SurfaceView?) {
        streamPlayer.attach(view)
    }

    private suspend fun restart(inputs: ReceiverInputs) {
        _state.update { it.copy(status = ReceiverStatus.Starting, deviceName = inputs.name(context)) }
        endSession()
        withContext(receiverDispatcher) { nativeReceiver.stop() }
        advertiser.unregisterAll()
        releaseVideo()

        if (needsLocalNetworkPermission()) {
            _state.update { it.copy(status = ReceiverStatus.NeedsLocalNetworkPermission) }
            return
        }
        if (_state.value.networkAddress == null && !hasNetwork()) {
            _state.update { it.copy(status = ReceiverStatus.NoNetwork) }
            return
        }

        val displayProfile = withContext(Dispatchers.Default) { DisplayProfile.detect(context, inputs.quality) }
        profile = displayProfile
        video =
            VideoDecoder(displayProfile.width, displayProfile.height, ::onPictureSize).also {
                it.setSurface(videoSurface)
            }
        val config =
            ReceiverConfig(
                name = inputs.name(context),
                deviceId = settingsRepository.deviceId(),
                keyFile = File(context.filesDir, KEY_FILE).absolutePath,
                width = displayProfile.width,
                height = displayProfile.height,
                refreshRate = displayProfile.refreshRate,
                maxFps = displayProfile.maxFps,
                h265 = displayProfile.h265,
                hls = inputs.directVideo,
                pinRequired = inputs.requireCode,
                languages = preferredLanguages(),
            )
        val port = withContext(receiverDispatcher) { nativeReceiver.start(config) }
        Log.i(TAG, "receiver started: port=$port profile=$displayProfile")
        _state.update {
            it.copy(status = if (port > 0) ReceiverStatus.Ready(port) else ReceiverStatus.Failed(port))
        }
    }

    private fun releaseVideo() {
        video?.release()
        video = null
    }

    private fun endSession() {
        video?.reset()
        audio.stop()
        mainHandler.post { streamPlayer.stop() }
        _state.update { it.copy(session = null, pin = null) }
    }

    private fun updateSession(transform: (Session?) -> Session?) {
        var started = false
        _state.update { current ->
            val next = transform(current.session)
            if (current.session == null && next != null) started = true
            current.copy(session = next, pin = if (next != null) null else current.pin)
        }
        if (started) {
            _sessionStarted.tryEmit(Unit)
            showNotice(Notice.Connected(clientName))
        }
    }

    private fun showNotice(notice: Notice) {
        mainHandler.post {
            noticeJob?.cancel()
            _state.update { it.copy(notice = notice) }
            noticeJob =
                scope.launch {
                    delay(NOTICE_MS)
                    _state.update { if (it.notice == notice) it.copy(notice = null) else it }
                }
        }
    }

    private fun onPictureSize(
        width: Int,
        height: Int,
    ) {
        updateSession { session ->
            (session as? Session.Mirroring ?: Session.Mirroring(clientName)).copy(
                pictureWidth = width,
                pictureHeight = height,
            )
        }
    }

    private fun onStreamEnded() {
        nativeReceiver.hlsEnded()
    }

    private fun onAdvertiseFailed(
        type: String,
        errorCode: Int,
    ) {
        _state.update { it.copy(status = ReceiverStatus.Failed(ADVERTISE_FAILED)) }
    }

    private fun needsLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT >= ANDROID_17 &&
            context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != PackageManager.PERMISSION_GRANTED

    private fun hasNetwork(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.activeNetwork != null
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        networkCallbackRegistered = true
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onLinkPropertiesChanged(
                    network: Network,
                    linkProperties: LinkProperties,
                ) {
                    val address =
                        linkProperties.linkAddresses
                            .map { it.address }
                            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                            ?.hostAddress
                    val wasOffline = _state.value.networkAddress == null
                    _state.update { it.copy(networkAddress = address) }
                    if (wasOffline && address != null && _state.value.status == ReceiverStatus.NoNetwork) {
                        mainHandler.post { retry() }
                    }
                }

                override fun onLost(network: Network) {
                    _state.update { it.copy(networkAddress = null) }
                }
            },
            mainHandler,
        )
    }

    private fun preferredLanguages(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val primary = (if (appLocales.isEmpty) Locale.getDefault() else appLocales[0] ?: Locale.getDefault()).language
        return if (primary == "en") "en" else "$primary:en"
    }

    /** The settings the iPhone sees; changing any of them restarts the receiver. */
    private data class ReceiverInputs(
        val customName: String?,
        val requireCode: Boolean,
        val quality: VideoQuality,
        val directVideo: Boolean,
    ) {
        fun name(context: Context): String = customName ?: defaultDeviceName(context)

        companion object {
            fun from(settings: CastSettings) =
                ReceiverInputs(settings.deviceName, settings.requireCode, settings.quality, settings.directVideo)
        }
    }

    /** Receives the native callbacks on the receiver's threads. */
    private inner class Listener : ReceiverListener {
        override fun registerService(
            type: String,
            name: String,
            port: Int,
            attributes: Map<String, String>,
        ) = advertiser.register(type, name, port, attributes)

        override fun unregisterService(type: String) = advertiser.unregister(type)

        override fun onClientRequest(
            deviceId: String,
            model: String,
            name: String,
        ) {
            clientName = name
        }

        override fun onConnectionsChanged(count: Int) {
            if (count == 0) endSession()
        }

        override fun onConnectionLost(reason: Int) {
            if (_state.value.session != null) showNotice(Notice.ConnectionLost)
            endSession()
        }

        override fun onVideoCodec(h265: Boolean): Boolean {
            if (!h265) return true
            val current = profile ?: return false
            return VideoDecoder.supportsHevc(current.width, current.height)
        }

        override fun onVideoSize(
            width: Int,
            height: Int,
        ) {
            updateSession { session ->
                val mirroring = session as? Session.Mirroring ?: Session.Mirroring(clientName)
                if (mirroring.pictureWidth ==
                    0
                ) {
                    mirroring.copy(pictureWidth = width, pictureHeight = height)
                } else {
                    mirroring
                }
            }
        }

        override fun onVideoFrame(
            data: ByteArray,
            h265: Boolean,
        ) {
            if (_state.value.session !is Session.Mirroring) {
                updateSession { session -> session as? Session.Mirroring ?: Session.Mirroring(clientName) }
            }
            video?.submit(data, h265)
        }

        override fun onVideoPaused(paused: Boolean) {
            updateSession { session -> (session as? Session.Mirroring)?.copy(paused = paused) ?: session }
        }

        override fun onVideoFlush() {
            video?.flush()
        }

        override fun onVideoReset(type: VideoResetType) {
            when (type) {
                VideoResetType.NoHold -> {
                    endSession()
                }

                VideoResetType.RtpShutdown, VideoResetType.RtpToHlsTeardown -> {
                    video?.reset()
                    updateSession { session -> if (session is Session.Mirroring) null else session }
                }

                VideoResetType.HlsShutdown, VideoResetType.HlsEndOfStream -> {
                    mainHandler.post { streamPlayer.stop() }
                    updateSession { session -> if (session is Session.Stream) null else session }
                }

                VideoResetType.OnVideoPlay -> {}
            }
        }

        override fun onAudioFormat(format: AudioFormat) {
            audio.start(format)
            if (format != AudioFormat.AacEld) {
                updateSession { session -> session ?: Session.Music(clientName) }
            }
        }

        override fun onAudioFrame(
            format: AudioFormat,
            data: ByteArray,
        ) = audio.submit(format, data)

        override fun onAudioFlush() = audio.flush()

        override fun onAudioStop() {
            audio.stop()
            updateSession { session -> if (session is Session.Music) null else session }
        }

        override fun onVolume(volumeDb: Float) {
            audio.setVolumeDb(volumeDb)
            val gain = VolumeMapping.gain(volumeDb)
            mainHandler.post { streamPlayer.setVolume(gain) }
        }

        override fun onMetadata(data: ByteArray) {
            val track = DmapParser.parse(data) ?: return
            updateMusic { it.copy(title = track.title, artist = track.artist, album = track.album) }
        }

        override fun onCoverArt(data: ByteArray) {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            updateMusic { it.copy(coverArt = bitmap) }
        }

        override fun onCoverArtCleared() {
            updateMusic { it.copy(coverArt = null) }
        }

        override fun onProgress(
            start: Long,
            current: Long,
            end: Long,
        ) {
            val durationMs = ((end - start).coerceAtLeast(0) * 1000) / AUDIO_CLOCK_HZ
            val positionMs = ((current - start).coerceAtLeast(0) * 1000) / AUDIO_CLOCK_HZ
            updateMusic {
                it.copy(durationMs = durationMs, positionMs = positionMs, positionAtMs = SystemClock.elapsedRealtime())
            }
        }

        override fun onPin(pin: String) {
            mainHandler.post {
                pinJob?.cancel()
                _state.update { it.copy(pin = pin) }
                pinJob =
                    scope.launch {
                        delay(PIN_MS)
                        _state.update { if (it.pin == pin) it.copy(pin = null) else it }
                    }
                _sessionStarted.tryEmit(Unit)
            }
        }

        override fun onHlsPlay(
            url: String,
            startPosition: Float,
        ) {
            mainHandler.post { streamPlayer.play(url, startPosition) }
            updateSession { Session.Stream(clientName) }
        }

        override fun onHlsSeek(position: Float) {
            mainHandler.post { streamPlayer.seek(position) }
        }

        override fun onHlsRate(rate: Float) {
            mainHandler.post { streamPlayer.setRate(rate) }
        }

        override fun onHlsStop() {
            mainHandler.post { streamPlayer.stop() }
            updateSession { session -> if (session is Session.Stream) null else session }
        }

        override fun onHlsPause() {
            mainHandler.post { streamPlayer.pause() }
        }

        private fun updateMusic(transform: (NowPlaying) -> NowPlaying) {
            updateSession { session ->
                val music = session as? Session.Music ?: return@updateSession session
                music.copy(nowPlaying = transform(music.nowPlaying))
            }
        }
    }

    companion object {
        private const val TAG = "ReceiverController"
        private const val KEY_FILE = "receiver_key.pem"
        private const val NOTICE_MS = 4_000L
        private const val PIN_MS = 60_000L
        private const val AUDIO_CLOCK_HZ = 44_100L
        private const val ADVERTISE_FAILED = -100
        private const val ANDROID_17 = 37

        /** Android 17's runtime permission for mDNS and local sockets. */
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

        /** The name iPhones see when the user has not chosen one: the TV's own device name. */
        fun defaultDeviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
                ?: Build.MODEL?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_name)
    }
}
