package com.fenpo.cast

import android.app.Application
import com.fenpo.cast.receiver.ReceiverController
import com.fenpo.cast.settings.SettingsRepository

/** Holds the process-wide objects; Fenpo Cast is small enough not to need a DI framework. */
class FenpoCastApplication : Application() {
    /** User settings. */
    val settingsRepository by lazy { SettingsRepository(this) }

    /** The receiver and its state. */
    val receiverController by lazy { ReceiverController(this, settingsRepository) }
}

/** Shortcut to the [FenpoCastApplication] of any context. */
val android.content.Context.castApplication: FenpoCastApplication
    get() = applicationContext as FenpoCastApplication
