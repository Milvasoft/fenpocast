package com.fenpo.cast.settings

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceIdentityTest {
    @Test
    fun `identity is a locally administered unicast address`() {
        repeat(200) { seed ->
            val id = DeviceIdentity.generate(Random(seed.toLong()))
            assertTrue(DeviceIdentity.isValid(id), id)
            val first = id.substring(0, 2).toInt(16)
            assertEquals(0x02, first and 0x02, id)
            assertEquals(0x00, first and 0x01, id)
        }
    }
}
