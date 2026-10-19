package com.fenpo.cast.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Process
import android.util.Log
import com.fenpo.cast.receiver.AudioFormat
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Plays the iPhone's audio: AAC-ELD while mirroring, ALAC (already decoded to PCM by the native
 * side) or AAC-LC for music. All AirPlay audio is 44.1 kHz stereo.
 *
 * Packets go through a queue to one playback thread; AudioTrack's blocking write paces them, the
 * same way GStreamer's clock does in UxPlay.
 */
class AudioRenderer(
    context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val queue = LinkedBlockingDeque<Command>()
    private val thread = Thread(::run, "AudioRenderer").apply { start() }

    @Volatile
    private var gain = 1f

    // Touched on [thread] only.
    private var format: AudioFormat? = null
    private var decoder: MediaCodec? = null
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private val decodeInfo = MediaCodec.BufferInfo()

    /** Prepares output for [newFormat]; the next packets are expected in it. */
    fun start(newFormat: AudioFormat) {
        queue.add(Command.Start(newFormat))
    }

    /** Queues one packet. Old packets are dropped if the queue holds more than about two seconds. */
    fun submit(
        packetFormat: AudioFormat,
        data: ByteArray,
    ) {
        if (queue.size > MAX_QUEUED_PACKETS) {
            queue.pollFirst()
        }
        queue.add(Command.Packet(packetFormat, data))
    }

    /** Drops queued audio, for example when the iPhone pauses or seeks. */
    fun flush() {
        queue.removeIf { it is Command.Packet }
        queue.add(Command.Flush)
    }

    /** Stops output and gives audio focus back. */
    fun stop() {
        queue.removeIf { it is Command.Packet }
        queue.add(Command.Stop)
    }

    /** Applies the iPhone's volume slider (dB, see [VolumeMapping]). */
    fun setVolumeDb(volumeDb: Float) {
        gain = VolumeMapping.gain(volumeDb)
        queue.add(Command.ApplyVolume)
    }

    /** Ends the playback thread. */
    fun release() {
        queue.clear()
        queue.add(Command.Quit)
        thread.join(TimeUnit.SECONDS.toMillis(2))
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (true) {
            val command = queue.take()
            try {
                when (command) {
                    is Command.Start -> {
                        open(command.format)
                    }

                    is Command.Packet -> {
                        play(command)
                    }

                    Command.Flush -> {
                        track?.pause()
                        track?.flush()
                        decoder?.flush()
                        track?.play()
                    }

                    Command.Stop -> {
                        close()
                    }

                    Command.ApplyVolume -> {
                        track?.setVolume(gain)
                    }

                    Command.Quit -> {
                        close()
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "audio command $command failed", e)
                close()
            }
        }
    }

    private fun open(newFormat: AudioFormat) {
        if (newFormat == format && track != null) return
        close()
        format = newFormat
        track =
            createTrack(newFormat).also {
                it.setVolume(gain)
                it.play()
            }
        decoder =
            when (newFormat) {
                AudioFormat.Pcm -> null
                AudioFormat.AacLc -> createAacDecoder(MediaCodecInfo.CodecProfileLevel.AACObjectLC, AAC_LC_CONFIG)
                AudioFormat.AacEld -> createAacDecoder(MediaCodecInfo.CodecProfileLevel.AACObjectELD, AAC_ELD_CONFIG)
            }
        requestFocus()
    }

    private fun play(packet: Command.Packet) {
        if (packet.format != format || track == null) open(packet.format)
        val output = track ?: return
        val aac = decoder
        if (aac == null) {
            output.write(packet.data, 0, packet.data.size)
            return
        }
        drain(aac, output)
        val inputIndex = aac.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex >= 0) {
            val input = aac.getInputBuffer(inputIndex) ?: return
            input.clear()
            input.put(packet.data)
            aac.queueInputBuffer(inputIndex, 0, packet.data.size, System.nanoTime() / 1000, 0)
        }
        drain(aac, output)
    }

    private fun drain(
        aac: MediaCodec,
        output: AudioTrack,
    ) {
        while (true) {
            val index = aac.dequeueOutputBuffer(decodeInfo, 0)
            if (index < 0) return
            val buffer: ByteBuffer = aac.getOutputBuffer(index) ?: return
            if (decodeInfo.size > 0) {
                buffer.position(decodeInfo.offset)
                buffer.limit(decodeInfo.offset + decodeInfo.size)
                output.write(buffer, decodeInfo.size, AudioTrack.WRITE_BLOCKING)
            }
            aac.releaseOutputBuffer(index, false)
        }
    }

    private fun createTrack(newFormat: AudioFormat): AudioTrack {
        val channelMask = android.media.AudioFormat.CHANNEL_OUT_STEREO
        val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelMask, encoding)
        // Mirroring needs lip sync, music only needs to never stutter.
        val bufferSize = if (newFormat == AudioFormat.AacEld) minBuffer * 2 else minBuffer * 4
        return AudioTrack
            .Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        if (newFormat == AudioFormat.AacEld) {
                            AudioAttributes.CONTENT_TYPE_MOVIE
                        } else {
                            AudioAttributes.CONTENT_TYPE_MUSIC
                        },
                    ).build(),
            ).setAudioFormat(
                android.media.AudioFormat
                    .Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(channelMask)
                    .setEncoding(encoding)
                    .build(),
            ).setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(
                // Lip sync while mirroring; music keeps the power-saving default.
                if (newFormat == AudioFormat.AacEld) {
                    AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
                } else {
                    AudioTrack.PERFORMANCE_MODE_NONE
                },
            ).build()
    }

    private fun createAacDecoder(
        profile: Int,
        config: ByteArray,
    ): MediaCodec {
        val mediaFormat =
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
                setInteger(MediaFormat.KEY_IS_ADTS, 0)
                setByteBuffer("csd-0", ByteBuffer.wrap(config))
            }
        return MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(mediaFormat, null, null, 0)
            start()
        }
    }

    private fun close() {
        decoder?.let {
            runCatching { it.stop() }
            it.release()
        }
        decoder = null
        track?.let {
            runCatching {
                it.pause()
                it.flush()
            }
            it.release()
        }
        track = null
        format = null
        abandonFocus()
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setOnAudioFocusChangeListener { }
                .build()
        audioManager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private sealed interface Command {
        data class Start(
            val format: AudioFormat,
        ) : Command

        class Packet(
            val format: AudioFormat,
            val data: ByteArray,
        ) : Command

        data object Flush : Command

        data object Stop : Command

        data object ApplyVolume : Command

        data object Quit : Command
    }

    private companion object {
        const val TAG = "AudioRenderer"
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val CODEC_TIMEOUT_US = 10_000L

        // About two seconds of ALAC (352 frames per packet) or AAC-ELD (480).
        const val MAX_QUEUED_PACKETS = 250

        // AudioSpecificConfig from ISO 14496-3 for 44.1 kHz stereo, as UxPlay passes them to GStreamer.
        val AAC_LC_CONFIG = byteArrayOf(0x12, 0x10)
        val AAC_ELD_CONFIG = byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00)
    }
}

/** Converts the iPhone's volume slider to an output gain the way UxPlay does by default. */
object VolumeMapping {
    /** AirPlay's mute value. */
    const val MUTE_DB = -144f

    /**
     * The slider runs from -30 dB (bottom, silent) to 0 dB (top); in between it is applied as a
     * plain dB gain, UxPlay's default `-db -30:0` range. -144 dB is AirPlay's mute.
     */
    fun gain(volumeDb: Float): Float =
        when {
            volumeDb <= -30f -> 0f
            volumeDb >= 0f -> 1f
            else -> 10.0.pow(volumeDb / 20.0).toFloat()
        }
}
