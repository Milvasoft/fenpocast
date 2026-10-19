package com.fenpo.cast.receiver

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Keeps Wi‑Fi out of power saving while an iPhone is showing something. Power saving batches
 * incoming packets, which shows up as stutter in a 60 fps mirrored screen. Does nothing on
 * Ethernet.
 */
class LowLatencyWifi(
    context: Context,
) {
    private val lock: WifiManager.WifiLock? =
        context.applicationContext
            .getSystemService(WifiManager::class.java)
            ?.createWifiLock(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                },
                "FenpoCast:session",
            )?.apply { setReferenceCounted(false) }

    /** Takes or gives back the lock. */
    fun setHeld(held: Boolean) {
        val wifiLock = lock ?: return
        if (held && !wifiLock.isHeld) wifiLock.acquire()
        if (!held && wifiLock.isHeld) wifiLock.release()
    }
}
