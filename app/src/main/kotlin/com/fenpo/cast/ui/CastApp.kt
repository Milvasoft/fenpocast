package com.fenpo.cast.ui

import android.view.Surface
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fenpo.cast.R
import com.fenpo.cast.media.StreamState
import com.fenpo.cast.receiver.CastState
import com.fenpo.cast.receiver.Session
import com.fenpo.cast.settings.CastSettings
import com.fenpo.cast.ui.home.HomeScreen
import com.fenpo.cast.ui.session.DisconnectDialog
import com.fenpo.cast.ui.session.MirrorScreen
import com.fenpo.cast.ui.session.MusicScreen
import com.fenpo.cast.ui.session.NoticeBanner
import com.fenpo.cast.ui.session.PinOverlay
import com.fenpo.cast.ui.session.StreamScreen
import com.fenpo.cast.ui.settings.OverlayPermission
import com.fenpo.cast.ui.settings.SettingsActions
import com.fenpo.cast.ui.settings.SettingsScreen

/** Callbacks the root screen hands down; the activity owns the Android side of each. */
data class CastAppActions(
    val settings: SettingsActions,
    val grantLocalNetwork: () -> Unit,
    val retry: () -> Unit,
    val disconnect: () -> Unit,
    val setVideoSurface: (Surface?) -> Unit,
    val setStreamView: (SurfaceView?) -> Unit,
)

/**
 * Root of the UI: a session (mirroring, music or video) takes the whole screen; otherwise the
 * home screen or Settings. The connection code and short notices sit on top of everything.
 */
@Composable
fun CastApp(
    state: CastState,
    streamState: StreamState,
    settings: CastSettings,
    language: AppLanguage,
    overlayPermission: OverlayPermission,
    actions: CastAppActions,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    val session = state.session

    Box(modifier = modifier.fillMaxSize()) {
        when (session) {
            is Session.Mirroring -> {
                MirrorScreen(session = session, onSurface = actions.setVideoSurface)
            }

            is Session.Music -> {
                MusicScreen(session = session)
            }

            is Session.Stream -> {
                StreamScreen(state = streamState, onView = actions.setStreamView)
            }

            null -> {
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        deviceName = state.deviceName,
                        language = language,
                        overlayPermission = overlayPermission,
                        networkAddress = state.networkAddress,
                        actions = actions.settings,
                    )
                } else {
                    HomeScreen(
                        deviceName = state.deviceName,
                        status = state.status,
                        onOpenSettings = { showSettings = true },
                        onGrantPermission = actions.grantLocalNetwork,
                        onRetry = actions.retry,
                    )
                }
            }
        }

        state.pin?.let { PinOverlay(pin = it) }
        NoticeBanner(notice = state.notice)
    }

    BackHandler(enabled = session != null) { confirmDisconnect = true }
    BackHandler(enabled = session == null && showSettings) { showSettings = false }

    if (confirmDisconnect && session != null) {
        DisconnectDialog(
            clientName = session.clientName.ifEmpty { stringResource(R.string.client_unknown) },
            onDisconnect = {
                confirmDisconnect = false
                actions.disconnect()
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
    LaunchedEffect(session == null) {
        if (session == null) confirmDisconnect = false
    }
}
