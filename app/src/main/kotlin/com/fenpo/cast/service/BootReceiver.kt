package com.fenpo.cast.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fenpo.cast.castApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Makes the TV ready for iPhones after it starts, when "Stay ready in the background" is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (context.castApplication.settingsRepository.settings
                        .first()
                        .keepRunning
                ) {
                    ReceiverService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
