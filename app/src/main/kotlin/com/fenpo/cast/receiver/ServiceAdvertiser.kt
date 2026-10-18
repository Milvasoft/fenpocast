package com.fenpo.cast.receiver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Advertises the receiver's `_airplay._tcp` and `_raop._tcp` services through the system mDNS
 * responder, so the TV shows up in the iPhone's screen mirroring and audio lists.
 *
 * @param onFailure Called when the system refuses a registration (for example without local
 *   network access); the receiver is then invisible to iPhones.
 */
class ServiceAdvertiser(
    context: Context,
    private val onFailure: (type: String, errorCode: Int) -> Unit,
) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val registrations = mutableMapOf<String, NsdManager.RegistrationListener>()

    /**
     * Starts advertising a service. Returns as soon as the request is queued; failures arrive
     * through [onFailure].
     */
    @Synchronized
    fun register(
        type: String,
        name: String,
        port: Int,
        attributes: Map<String, String>,
    ): Boolean {
        unregister(type)
        val info =
            NsdServiceInfo().apply {
                serviceName = name
                serviceType = type
                this.port = port
                attributes.forEach { (key, value) -> setAttribute(key, value) }
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "registered ${serviceInfo.serviceName} ($type) on port $port")
                }

                override fun onRegistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "registration of $type failed: $errorCode")
                    synchronized(this@ServiceAdvertiser) { registrations.remove(type) }
                    onFailure(type, errorCode)
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "unregistered ${serviceInfo.serviceName} ($type)")
                }

                override fun onUnregistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "unregistration of $type failed: $errorCode")
                }
            }
        return try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            registrations[type] = listener
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "registerService($type) threw", e)
            false
        }
    }

    /** Stops advertising a service registered with [register]. */
    @Synchronized
    fun unregister(type: String) {
        val listener = registrations.remove(type) ?: return
        try {
            nsdManager.unregisterService(listener)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unregisterService($type): ${e.message}")
        }
    }

    /** Stops advertising everything. */
    @Synchronized
    fun unregisterAll() {
        registrations.keys.toList().forEach(::unregister)
    }

    private companion object {
        const val TAG = "ServiceAdvertiser"
    }
}
