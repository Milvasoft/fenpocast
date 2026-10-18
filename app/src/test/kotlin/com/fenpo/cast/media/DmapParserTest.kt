package com.fenpo.cast.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DmapParserTest {
    private fun item(
        tag: String,
        value: String,
    ): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return tag.toByteArray(Charsets.US_ASCII) + intBytes(bytes.size) + bytes
    }

    private fun intBytes(value: Int) =
        byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )

    private fun listing(vararg items: ByteArray): ByteArray {
        val body = items.fold(ByteArray(0)) { acc, it -> acc + it }
        return "mlit".toByteArray(Charsets.US_ASCII) + intBytes(body.size) + body
    }

    @Test
    fun `reads title, artist and album`() {
        val data =
            listing(
                item("asal", "Kayıp Albüm"),
                item("minm", "Şarkı"),
                item("asar", "Sanatçı"),
                item("caps", "\u0001"),
            )
        assertEquals(TrackInfo("Şarkı", "Sanatçı", "Kayıp Albüm"), DmapParser.parse(data))
    }

    @Test
    fun `empty values become null`() {
        assertEquals(
            TrackInfo(null, "Artist", null),
            DmapParser.parse(listing(item("minm", " "), item("asar", "Artist"))),
        )
    }

    @Test
    fun `rejects other containers and broken lengths`() {
        assertNull(DmapParser.parse("mlix".toByteArray() + intBytes(0)))
        val broken = listing(item("minm", "Title")).also { it[12] = 0x7F }
        assertNull(DmapParser.parse(broken))
        assertNull(DmapParser.parse(ByteArray(3)))
    }
}
