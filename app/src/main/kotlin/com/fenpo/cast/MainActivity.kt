package com.fenpo.cast

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fenpo.cast.receiver.ReceiverController
import com.fenpo.cast.service.ReceiverService
import com.fenpo.cast.settings.ThemeMode
import com.fenpo.cast.ui.CastApp
import com.fenpo.cast.ui.CastAppActions
import com.fenpo.cast.ui.MainViewModel
import com.fenpo.cast.ui.settings.OverlayPermission
import com.fenpo.cast.ui.settings.SettingsActions
import com.fenpo.cast.ui.theme.FenpoCastTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The only activity: the home screen, Settings and whatever an iPhone is showing. */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var overlayPermission by mutableStateOf(OverlayPermission(granted = false, canOpenSettings = false))

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = castApplication.receiverController
        val actions =
            CastAppActions(
                settings =
                    SettingsActions(
                        setDeviceName = viewModel::setDeviceName,
                        setRequireCode = viewModel::setRequireCode,
                        setQuality = viewModel::setQuality,
                        setDirectVideo = viewModel::setDirectVideo,
                        setKeepRunning = viewModel::setKeepRunning,
                        setTheme = viewModel::setTheme,
                        setLanguage = viewModel::setLanguage,
                        openOverlaySettings = ::openOverlaySettings,
                    ),
                grantLocalNetwork = { localNetworkPermission.launch(ReceiverController.LOCAL_NETWORK_PERMISSION) },
                retry = viewModel::retry,
                disconnect = viewModel::disconnect,
                setVideoSurface = controller::setVideoSurface,
                setStreamView = controller::setStreamView,
            )

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val state by viewModel.castState.collectAsStateWithLifecycle()
            val streamState by viewModel.streamState.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark =
                when (current.theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            FenpoCastTheme(darkTheme = dark) {
                CastApp(
                    state = state,
                    streamState = streamState,
                    settings = current,
                    language = language,
                    overlayPermission = overlayPermission,
                    actions = actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
        ReceiverService.start(this)
    }

    override fun onResume() {
        super.onResume()
        overlayPermission =
            OverlayPermission(
                granted = Settings.canDrawOverlays(this),
                canOpenSettings = overlaySettingsIntent().resolveActivity(packageManager) != null,
            )
    }

    override fun onStop() {
        isVisible = false
        val controller = castApplication.receiverController
        lifecycleScope.launch {
            val keepRunning =
                castApplication.settingsRepository.settings
                    .first()
                    .keepRunning
            if (!keepRunning && controller.state.value.session == null && !isChangingConfigurations) {
                ReceiverService.stop(this@MainActivity)
            }
        }
        super.onStop()
    }

    private fun overlaySettingsIntent() =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())

    private fun openOverlaySettings() {
        runCatching { startActivity(overlaySettingsIntent()) }
    }

    companion object {
        /** True while the activity is on screen; the service uses it to decide whether to bring it forward. */
        @Volatile
        var isVisible: Boolean = false
            private set
    }
}
