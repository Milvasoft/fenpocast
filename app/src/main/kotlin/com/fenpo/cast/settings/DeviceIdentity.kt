package com.fenpo.cast.settings

import java.util.Locale
import java.util.Random

/** Makes up the MAC-style identity the receiver advertises. */
object DeviceIdentity {
    /**
     * Returns six random bytes as `aa:bb:cc:dd:ee:ff`, marked locally administered and unicast so
     * it can never collide with a real network card.
     */
    fun generate(random: Random): String {
        val bytes = ByteArray(6).also(random::nextBytes)
        bytes[0] = ((bytes[0].toInt() or 0x02) and 0xFE.toInt()).toByte()
        return bytes.joinToString(":") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
    }

    /** True for an identity in the form [generate] produces. */
    fun isValid(id: String): Boolean = Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$").matches(id)
}
