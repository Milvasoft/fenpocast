package com.fenpo.cast.media

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeMappingTest {
    @Test
    fun `slider ends and mute`() {
        assertEquals(0f, VolumeMapping.gain(-144f))
        assertEquals(0f, VolumeMapping.gain(-30f))
        assertEquals(1f, VolumeMapping.gain(0f))
        assertEquals(1f, VolumeMapping.gain(3f))
    }

    @Test
    fun `in between is a plain dB gain`() {
        assertEquals(0.5012f, VolumeMapping.gain(-6f), 0.0001f)
        assertEquals(0.1f, VolumeMapping.gain(-20f), 0.0001f)
    }
}
