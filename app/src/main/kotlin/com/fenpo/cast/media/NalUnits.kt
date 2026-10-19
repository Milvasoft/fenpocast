package com.fenpo.cast.media

/**
 * Reads Annex B access units the way the receiver hands them over: NAL units behind 4-byte
 * start codes, with the parameter sets (SPS and PPS, plus VPS for H.265) in front of key frames.
 */
object NalUnits {
    /** A NAL unit inside an access unit: its type and where its payload starts and ends. */
    data class Unit(
        val type: Int,
        val start: Int,
        val end: Int,
    )

    /** Splits an access unit at its 3- and 4-byte start codes. */
    fun split(
        data: ByteArray,
        h265: Boolean,
    ): List<Unit> {
        val units = mutableListOf<Unit>()
        var payloadStart = -1
        var i = 0
        while (i + 3 <= data.size) {
            val isStartCode = data[i] == ZERO && data[i + 1] == ZERO && data[i + 2] == ONE
            if (isStartCode) {
                if (payloadStart >= 0) {
                    val end = if (i > 0 && data[i - 1] == ZERO) i - 1 else i
                    units += Unit(typeOf(data, payloadStart, h265), payloadStart, end)
                }
                payloadStart = i + 3
                i += 3
            } else {
                i++
            }
        }
        if (payloadStart in 0 until data.size) {
            units += Unit(typeOf(data, payloadStart, h265), payloadStart, data.size)
        }
        return units
    }

    /**
     * Key frame test that reads only the first NAL unit. The receiver puts the parameter sets in
     * front of every key frame, so a key frame starts with SPS (H.264) or VPS/SPS (H.265), or with
     * the IDR slice itself.
     */
    fun startsKeyFrame(
        data: ByteArray,
        h265: Boolean,
    ): Boolean {
        val offset = firstPayload(data)
        if (offset < 0) return false
        val type = typeOf(data, offset, h265)
        return if (h265) {
            type == H265_VPS || type == H265_SPS || type in H265_IDR_W_RADL..H265_CRA
        } else {
            type == H264_SPS || type == H264_IDR
        }
    }

    private fun firstPayload(data: ByteArray): Int =
        when {
            data.size > 4 && data[0] == ZERO && data[1] == ZERO && data[2] == ZERO && data[3] == ONE -> 4
            data.size > 3 && data[0] == ZERO && data[1] == ZERO && data[2] == ONE -> 3
            else -> -1
        }

    /** True when the access unit can start decoding: it carries parameter sets or an IDR slice. */
    fun isKeyFrame(
        units: List<Unit>,
        h265: Boolean,
    ): Boolean =
        units.any { unit ->
            if (h265) {
                unit.type in H265_IDR_W_RADL..H265_CRA || unit.type == H265_SPS
            } else {
                unit.type == H264_IDR ||
                    unit.type == H264_SPS
            }
        }

    /**
     * The parameter sets in front of the first picture, each with a start code, as MediaCodec
     * wants them in csd-0 and csd-1. Null when the access unit has none.
     */
    fun parameterSets(
        data: ByteArray,
        units: List<Unit>,
        h265: Boolean,
    ): ParameterSets? {
        val sets =
            units.filter { unit ->
                if (h265) unit.type in H265_VPS..H265_PPS else unit.type == H264_SPS || unit.type == H264_PPS
            }
        if (sets.isEmpty()) return null
        return if (h265) {
            ParameterSets(csd0 = withStartCodes(data, sets), csd1 = null)
        } else {
            val sps = sets.filter { it.type == H264_SPS }
            val pps = sets.filter { it.type == H264_PPS }
            if (sps.isEmpty() || pps.isEmpty()) return null
            ParameterSets(csd0 = withStartCodes(data, sps), csd1 = withStartCodes(data, pps))
        }
    }

    private fun withStartCodes(
        data: ByteArray,
        units: List<Unit>,
    ): ByteArray {
        val size = units.sumOf { START_CODE.size + it.end - it.start }
        val out = ByteArray(size)
        var offset = 0
        for (unit in units) {
            START_CODE.copyInto(out, offset)
            offset += START_CODE.size
            data.copyInto(out, offset, unit.start, unit.end)
            offset += unit.end - unit.start
        }
        return out
    }

    private fun typeOf(
        data: ByteArray,
        offset: Int,
        h265: Boolean,
    ): Int {
        if (offset >= data.size) return -1
        val header = data[offset].toInt() and 0xFF
        return if (h265) (header shr 1) and 0x3F else header and 0x1F
    }

    private const val ZERO: Byte = 0
    private const val ONE: Byte = 1
    private val START_CODE = byteArrayOf(0, 0, 0, 1)

    private const val H264_IDR = 5
    private const val H264_SPS = 7
    private const val H264_PPS = 8
    private const val H265_IDR_W_RADL = 19
    private const val H265_CRA = 21
    private const val H265_VPS = 32
    private const val H265_SPS = 33
    private const val H265_PPS = 34
}

/** Codec-specific data for MediaCodec: csd-0 (SPS, or VPS+SPS+PPS for H.265) and csd-1 (PPS). */
class ParameterSets(
    val csd0: ByteArray,
    val csd1: ByteArray?,
) {
    override fun equals(other: Any?): Boolean =
        other is ParameterSets && csd0.contentEquals(other.csd0) && csd1.contentEquals(other.csd1)

    override fun hashCode(): Int = 31 * csd0.contentHashCode() + (csd1?.contentHashCode() ?: 0)
}
