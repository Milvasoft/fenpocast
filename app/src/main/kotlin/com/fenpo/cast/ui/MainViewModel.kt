package com.fenpo.cast.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenpo.cast.castApplication
import com.fenpo.cast.receiver.CastState
import com.fenpo.cast.settings.CastSettings
import com.fenpo.cast.settings.ThemeMode
import com.fenpo.cast.settings.VideoQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dil setting: the system's language, or Turkish or English for Fenpo Cast only. */
enum class AppLanguage(
    val tag: String?,
) {
    System(null),
    Turkish("tr"),
    English("en"),
}

/** Connects the screens to the receiver and the settings. */
class MainViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsRepository = application.castApplication.settingsRepository
    private val controller = application.castApplication.receiverController

    /** Receiver and session state. */
    val castState: StateFlow<CastState> = controller.state

    /** State of a handed-over video. */
    val streamState = controller.streamState

    /** Settings; null until the first read, so the first frame already has the right theme. */
    val settings: StateFlow<CastSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _language = MutableStateFlow(currentLanguage())

    /** Dil setting. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setDeviceName(name: String) = launch { settingsRepository.setDeviceName(name) }

    fun setRequireCode(enabled: Boolean) = launch { settingsRepository.setRequireCode(enabled) }

    fun setQuality(quality: VideoQuality) = launch { settingsRepository.setQuality(quality) }

    fun setDirectVideo(enabled: Boolean) = launch { settingsRepository.setDirectVideo(enabled) }

    fun setTheme(theme: ThemeMode) = launch { settingsRepository.setTheme(theme) }

    /** Switches the app language without a restart (AppCompat recreates the activity). */
    fun setLanguage(language: AppLanguage) {
        _language.value = language
        AppCompatDelegate.setApplicationLocales(
            language.tag?.let(LocaleListCompat::forLanguageTags) ?: LocaleListCompat.getEmptyLocaleList(),
        )
    }

    /** Drops the connected iPhone. */
    fun disconnect() = controller.disconnect()

    /** Starts the receiver again (after a failure or a granted permission). */
    fun retry() = controller.retry()

    /** Hides the connection code. */
    fun dismissPin() = controller.dismissPin()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun currentLanguage(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.System
        return when (locales[0]?.language) {
            "tr" -> AppLanguage.Turkish
            "en" -> AppLanguage.English
            else -> AppLanguage.System
        }
    }
}
