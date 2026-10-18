package com.fenpo.cast.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NalUnitsTest {
    private val start = byteArrayOf(0, 0, 0, 1)
    private val sps = byteArrayOf(0x67, 0x64, 0x00, 0x28)
    private val pps = byteArrayOf(0x68, 0xEE.toByte(), 0x3C, 0x80.toByte())
    private val idr = byteArrayOf(0x65, 0x88.toByte(), 0x84.toByte(), 0x00, 0x21)
    private val slice = byteArrayOf(0x41, 0x9A.toByte(), 0x02)

    @Test
    fun `key frame with parameter sets gives csd-0 and csd-1`() {
        val frame = start + sps + start + pps + start + idr
        val units = NalUnits.split(frame, h265 = false)
        assertEquals(listOf(7, 8, 5), units.map { it.type })
        assertTrue(NalUnits.isKeyFrame(units, h265 = false))
        val sets = assertNotNull(NalUnits.parameterSets(frame, units, h265 = false))
        assertContentEquals(start + sps, sets.csd0)
        assertContentEquals(start + pps, sets.csd1)
    }

    @Test
    fun `plain slice is not a key frame and has no parameter sets`() {
        val frame = start + slice
        val units = NalUnits.split(frame, h265 = false)
        assertFalse(NalUnits.isKeyFrame(units, h265 = false))
        assertNull(NalUnits.parameterSets(frame, units, h265 = false))
    }

    @Test
    fun `h265 parameter sets go into csd-0 together`() {
        val vps = byteArrayOf(0x40, 0x01, 0x0C)
        val hevcSps = byteArrayOf(0x42, 0x01, 0x01)
        val hevcPps = byteArrayOf(0x44, 0x01, 0xC1.toByte())
        val hevcIdr = byteArrayOf(0x26, 0x01, 0xAF.toByte())
        val frame = start + vps + start + hevcSps + start + hevcPps + start + hevcIdr
        val units = NalUnits.split(frame, h265 = true)
        assertEquals(listOf(32, 33, 34, 19), units.map { it.type })
        assertTrue(NalUnits.isKeyFrame(units, h265 = true))
        val sets = assertNotNull(NalUnits.parameterSets(frame, units, h265 = true))
        assertContentEquals(start + vps + start + hevcSps + start + hevcPps, sets.csd0)
        assertNull(sets.csd1)
    }

    @Test
    fun `equal parameter sets compare equal`() {
        assertEquals(ParameterSets(start + sps, start + pps), ParameterSets(start + sps, start + pps))
    }
}
