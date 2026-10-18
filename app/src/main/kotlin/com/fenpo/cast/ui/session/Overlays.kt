package com.fenpo.cast.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.fenpo.cast.R
import com.fenpo.cast.receiver.Notice
import com.fenpo.cast.ui.theme.fenpoButtonColors
import com.fenpo.cast.ui.theme.fenpoOutlinedButtonColors

/** A short message at the bottom of the screen: who connected, or that the connection dropped. */
@Composable
fun NoticeBanner(
    notice: Notice?,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.BottomCenter, modifier = modifier.fillMaxSize().padding(bottom = 48.dp)) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            val text =
                when (notice) {
                    is Notice.Connected -> {
                        stringResource(
                            R.string.notice_connected,
                            notice.clientName.ifEmpty { stringResource(R.string.client_unknown) },
                        )
                    }

                    Notice.ConnectionLost -> {
                        stringResource(R.string.notice_connection_lost)
                    }

                    Notice.StreamFailed -> {
                        stringResource(R.string.notice_stream_failed)
                    }

                    null -> {
                        ""
                    }
                }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50))
                        .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

/** The four-digit code the iPhone user has to type. */
@Composable
fun PinOverlay(
    pin: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            colors =
                SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 64.dp, vertical = 48.dp),
            ) {
                Text(text = stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pin.forEach { digit ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(width = 96.dp, height = 120.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                        ) {
                            Text(
                                text = digit.toString(),
                                fontSize = 64.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.pin_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Asks before the TV drops the iPhone (the Back button during a session). */
@Composable
fun DisconnectDialog(
    clientName: String,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            colors =
                SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Column(modifier = Modifier.padding(36.dp)) {
                Text(
                    text = stringResource(R.string.disconnect_title, clientName),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = fenpoOutlinedButtonColors(),
                        modifier = Modifier.focusRequester(cancelFocus),
                    ) {
                        Text(stringResource(R.string.disconnect_cancel))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = onDisconnect, colors = fenpoButtonColors()) {
                        Text(stringResource(R.string.disconnect_action))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancelFocus.requestFocus() }
    }
}
