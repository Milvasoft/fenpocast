package com.fenpo.cast

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.fenpo.cast.receiver.ReceiverController
import com.fenpo.cast.settings.ThemeMode
import com.fenpo.cast.ui.CastApp
import com.fenpo.cast.ui.CastAppActions
import com.fenpo.cast.ui.MainViewModel
import com.fenpo.cast.ui.settings.SettingsActions
import com.fenpo.cast.ui.theme.FenpoCastTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The only activity: the home screen, Settings and whatever an iPhone is showing.
 *
 * The receiver runs while this activity is on screen. When the user leaves, it stops at once if
 * nothing is playing, otherwise when the iPhone ends the session (music keeps playing after the
 * user goes back to the TV's home screen).
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var stopWhenIdle: Job? = null

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
                        setTheme = viewModel::setTheme,
                        setLanguage = viewModel::setLanguage,
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
                    actions = actions,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopWhenIdle?.cancel()
        stopWhenIdle = null
        castApplication.receiverController.start()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            val controller = castApplication.receiverController
            stopWhenIdle =
                lifecycleScope.launch {
                    controller.state.first { it.session == null }
                    controller.stop()
                }
        }
        super.onStop()
    }
}
