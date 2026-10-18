package com.fenpo.cast.media

/** Track information the iPhone sends with audio. */
data class TrackInfo(
    val title: String?,
    val artist: String?,
    val album: String?,
)

/**
 * Reads the DMAP listing item (`mlit`) AirPlay sends as track metadata: a sequence of
 * 4-byte tag, 4-byte big-endian length and value.
 */
object DmapParser {
    /** Returns the track information in [data], or null if it is not a DMAP listing item. */
    fun parse(data: ByteArray): TrackInfo? {
        if (data.size < HEADER || tag(data, 0) != "mlit") return null
        val length = length(data, 4)
        if (length != data.size - HEADER) return null

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = HEADER
        while (offset + HEADER <= data.size) {
            val itemTag = tag(data, offset)
            val itemLength = length(data, offset + 4)
            val start = offset + HEADER
            if (itemLength < 0 || start + itemLength > data.size) return null
            val value = { String(data, start, itemLength, Charsets.UTF_8).trim().ifEmpty { null } }
            when (itemTag) {
                "minm" -> title = value()
                "asar" -> artist = value()
                "asal" -> album = value()
            }
            offset = start + itemLength
        }
        return TrackInfo(title, artist, album)
    }

    private fun tag(
        data: ByteArray,
        offset: Int,
    ) = String(data, offset, 4, Charsets.US_ASCII)

    private fun length(
        data: ByteArray,
        offset: Int,
    ): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private const val HEADER = 8
}
