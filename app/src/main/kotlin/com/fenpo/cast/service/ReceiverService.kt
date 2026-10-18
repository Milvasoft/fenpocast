package com.fenpo.cast.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fenpo.cast.MainActivity
import com.fenpo.cast.R
import com.fenpo.cast.castApplication
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Keeps the receiver running while the app is in the background, so the TV stays in the
 * iPhone's list. When an iPhone starts showing something it brings the app to the front
 * (Android only allows that with the "display over other apps" permission).
 */
class ReceiverService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val controller = castApplication.receiverController
        controller.start()

        lifecycleScope.launch {
            controller.sessionStarted.collect {
                if (!MainActivity.isVisible) bringAppForward()
            }
        }
        lifecycleScope.launch {
            controller.state.distinctUntilChangedBy { it.deviceName }.collect { state ->
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(state.deviceName))
            }
        }
    }

    override fun onDestroy() {
        castApplication.receiverController.stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        createChannel()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(""), type)
    }

    private fun bringAppForward() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                ),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not bring Fenpo Cast forward", e)
        }
    }

    private fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_receiver),
                NotificationManager.IMPORTANCE_MIN,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(deviceName: String): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (deviceName.isEmpty()) {
                    getString(R.string.notification_starting)
                } else {
                    getString(R.string.notification_ready, deviceName)
                },
            ).setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "ReceiverService"
        private const val CHANNEL_ID = "receiver"
        private const val NOTIFICATION_ID = 1

        /** Starts the service; allowed from the foreground and from boot. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ReceiverService::class.java))
        }

        /** Stops the service and with it the receiver. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ReceiverService::class.java))
        }
    }
}
