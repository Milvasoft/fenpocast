package com.fenpo.cast.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Decodes the mirrored screen (H.264 or H.265) straight onto a [Surface] with as little delay as
 * MediaCodec allows: every decoded frame is shown the moment it comes out.
 *
 * Frames can arrive before the screen exists (the app is opening). Until a surface is attached,
 * the decoder keeps the frames since the last key frame, so the picture appears without waiting
 * for the iPhone's next key frame, which on a still screen may take a long time.
 *
 * @param maxWidth Largest picture the decoder must be ready for (the advertised display size).
 * @param maxHeight Largest picture height.
 * @param onPictureSize Called with the decoded picture size whenever it changes.
 */
class VideoDecoder(
    private val maxWidth: Int,
    private val maxHeight: Int,
    private val onPictureSize: (width: Int, height: Int) -> Unit,
) {
    // Display priority: a frame waiting on this thread is a frame late on screen.
    private val thread = HandlerThread("VideoDecoder", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    // Everything below is touched on [thread] only.
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var codecMime: String? = null
    private var parameterSets: ParameterSets? = null
    private val pending = ArrayDeque<ByteArray>()
    private var pendingBytes = 0L
    private val freeInputs = ArrayDeque<Int>()
    private val queued = ArrayDeque<ByteArray>()
    private var waitingForKeyFrame = true
    private var released = false

    /** Attaches or detaches the screen; blocks until the decoder no longer uses the old one. */
    fun setSurface(newSurface: Surface?) {
        runAndWait {
            if (surface === newSurface) return@runAndWait
            releaseCodec()
            surface = newSurface
            if (newSurface != null) {
                replayPending()
            }
        }
    }

    /** Queues one access unit from the receiver. */
    fun submit(
        frame: ByteArray,
        h265: Boolean,
    ) {
        handler.post { if (!released) onFrame(frame, h265) }
    }

    /** Drops everything queued; decoding resumes at the next key frame. */
    fun flush() {
        handler.post {
            if (released) return@post
            releaseCodec()
            clearPending()
            waitingForKeyFrame = true
        }
    }

    /** Ends the stream: the next stream starts from scratch. */
    fun reset() {
        handler.post {
            if (released) return@post
            releaseCodec()
            clearPending()
            parameterSets = null
            codecMime = null
            waitingForKeyFrame = true
        }
    }

    /** Stops the decoder thread. The decoder cannot be used afterwards. */
    fun release() {
        runAndWait {
            released = true
            releaseCodec()
            clearPending()
        }
        thread.quitSafely()
    }

    private fun onFrame(
        frame: ByteArray,
        h265: Boolean,
    ) {
        // Only the first NAL unit is looked at for ordinary frames; the whole access unit is split
        // only for key frames, which are rare while mirroring.
        val keyFrame = NalUnits.startsKeyFrame(frame, h265)
        val mime = if (h265) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
        if (keyFrame) {
            val units = NalUnits.split(frame, h265)
            val sets = NalUnits.parameterSets(frame, units, h265)
            // New parameter sets mean a new picture size (the iPhone rotated) or codec: start over.
            if ((sets != null && sets != parameterSets) || mime != codecMime) {
                releaseCodec()
                if (sets != null) parameterSets = sets
                codecMime = mime
            }
            clearPending()
            waitingForKeyFrame = false
        } else if (waitingForKeyFrame) {
            return
        }
        remember(frame)
        if (surface == null) return
        if (codec == null && !startCodec()) return
        enqueue(frame)
    }

    private fun remember(frame: ByteArray) {
        pending.addLast(frame)
        pendingBytes += frame.size
        // A screen that never sends another key frame must not grow without bound.
        if (pending.size > MAX_PENDING_FRAMES || pendingBytes > MAX_PENDING_BYTES) {
            clearPending()
            waitingForKeyFrame = true
        }
    }

    private fun clearPending() {
        pending.clear()
        pendingBytes = 0
    }

    private fun replayPending() {
        if (pending.isEmpty() || waitingForKeyFrame) return
        if (!startCodec()) return
        pending.forEach(::enqueue)
    }

    private fun startCodec(): Boolean {
        val target = surface ?: return false
        val mime = codecMime ?: return false
        val sets = parameterSets
        val format =
            MediaFormat.createVideoFormat(mime, maxWidth, maxHeight).apply {
                setInteger(MediaFormat.KEY_MAX_WIDTH, maxWidth)
                setInteger(MediaFormat.KEY_MAX_HEIGHT, maxHeight)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (sets != null) {
                    setByteBuffer("csd-0", ByteBuffer.wrap(sets.csd0))
                    sets.csd1?.let { setByteBuffer("csd-1", ByteBuffer.wrap(it)) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }
        val name =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
                ?: MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(
                    MediaFormat.createVideoFormat(mime, maxWidth, maxHeight),
                )
        val created =
            try {
                (if (name != null) MediaCodec.createByCodecName(name) else MediaCodec.createDecoderByType(mime)).also {
                    it.setCallback(callback, handler)
                    it.configure(format, target, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "could not start $mime decoder", e)
                null
            }
        codec = created
        freeInputs.clear()
        queued.clear()
        return created != null
    }

    private fun enqueue(frame: ByteArray) {
        queued.addLast(frame)
        feed()
    }

    private fun feed() {
        val current = codec ?: return
        while (queued.isNotEmpty() && freeInputs.isNotEmpty()) {
            val index = freeInputs.removeFirst()
            val frame = queued.removeFirst()
            try {
                val buffer = current.getInputBuffer(index) ?: continue
                if (frame.size > buffer.capacity()) {
                    Log.w(TAG, "frame of ${frame.size} bytes does not fit the input buffer")
                    current.queueInputBuffer(index, 0, 0, presentationTimeUs(), 0)
                    continue
                }
                buffer.clear()
                buffer.put(frame)
                current.queueInputBuffer(index, 0, frame.size, presentationTimeUs(), 0)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "queueInputBuffer failed", e)
                return
            }
        }
    }

    private fun releaseCodec() {
        val current = codec ?: return
        codec = null
        freeInputs.clear()
        queued.clear()
        try {
            current.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop failed", e)
        }
        current.release()
    }

    private fun presentationTimeUs(): Long = System.nanoTime() / 1000

    private val callback =
        object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(
                mediaCodec: MediaCodec,
                index: Int,
            ) {
                if (mediaCodec !== codec) return
                freeInputs.addLast(index)
                feed()
            }

            override fun onOutputBufferAvailable(
                mediaCodec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                if (mediaCodec !== codec) return
                try {
                    mediaCodec.releaseOutputBuffer(index, info.size > 0)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "releaseOutputBuffer failed", e)
                }
            }

            override fun onError(
                mediaCodec: MediaCodec,
                e: MediaCodec.CodecException,
            ) {
                if (mediaCodec !== codec) return
                Log.e(TAG, "decoder error, waiting for the next key frame", e)
                releaseCodec()
                clearPending()
                waitingForKeyFrame = true
            }

            override fun onOutputFormatChanged(
                mediaCodec: MediaCodec,
                format: MediaFormat,
            ) {
                if (mediaCodec !== codec) return
                val width =
                    if (format.containsKey("crop-right")) {
                        format.getInteger("crop-right") - format.getInteger("crop-left") + 1
                    } else {
                        format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                val height =
                    if (format.containsKey("crop-bottom")) {
                        format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1
                    } else {
                        format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                onPictureSize(width, height)
            }
        }

    private fun runAndWait(block: () -> Unit) {
        if (Thread.currentThread() === thread) {
            block()
            return
        }
        val done = CountDownLatch(1)
        val posted =
            handler.post {
                try {
                    block()
                } finally {
                    done.countDown()
                }
            }
        if (posted) done.await(WAIT_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val TAG = "VideoDecoder"
        private const val MAX_PENDING_FRAMES = 600
        private const val MAX_PENDING_BYTES = 48L * 1024 * 1024
        private const val WAIT_SECONDS = 2L

        /** True when the TV has a decoder for H.265 at [width] x [height] (4K mirroring). */
        fun supportsHevc(
            width: Int,
            height: Int,
        ): Boolean {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            return list.codecInfos.any { info ->
                !info.isEncoder &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) } &&
                    runCatching {
                        info
                            .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                            .videoCapabilities
                            ?.isSizeSupported(width, height) == true
                    }.getOrDefault(false) &&
                    isHardware(info)
            }
        }

        private fun isHardware(info: MediaCodecInfo): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
            }
    }
}
