package com.fenpo.cast.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.fenpo.cast.R
import com.fenpo.cast.receiver.ReceiverStatus
import com.fenpo.cast.ui.theme.FenpoCastColors
import com.fenpo.cast.ui.theme.fenpoButtonColors
import com.fenpo.cast.ui.theme.fenpoOutlinedButtonColors

/**
 * What the TV shows while it waits: its name, whether iPhones can see it, and how to connect.
 *
 * @param deviceName Name the iPhone sees.
 * @param status Receiver status.
 * @param onOpenSettings Opens Settings.
 * @param onGrantPermission Asks for local network access (Android 17).
 * @param onRetry Restarts the receiver.
 */
@Composable
fun HomeScreen(
    deviceName: String,
    status: ReceiverStatus,
    onOpenSettings: () -> Unit,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val hasAction = status is ReceiverStatus.NeedsLocalNetworkPermission || status is ReceiverStatus.Failed

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(onOpenSettings = onOpenSettings, settingsFocus = settingsFocus)
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = deviceName,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatusPill(status)
            Spacer(modifier = Modifier.height(40.dp))
            when (status) {
                is ReceiverStatus.Ready, ReceiverStatus.Starting -> {
                    Instructions(deviceName)
                }

                ReceiverStatus.NoNetwork -> {
                    Message(R.drawable.ic_wifi_off, stringResource(R.string.home_no_network_body))
                }

                ReceiverStatus.NeedsLocalNetworkPermission -> {
                    Message(R.drawable.ic_phone, stringResource(R.string.home_permission_body))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onGrantPermission,
                        colors = fenpoButtonColors(),
                        modifier = Modifier.focusRequester(actionFocus),
                    ) {
                        Text(stringResource(R.string.home_permission_action))
                    }
                }

                is ReceiverStatus.Failed -> {
                    Message(R.drawable.ic_wifi_off, stringResource(R.string.home_failed_body))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRetry,
                        colors = fenpoButtonColors(),
                        modifier = Modifier.focusRequester(actionFocus),
                    ) {
                        Text(stringResource(R.string.home_retry))
                    }
                }

                ReceiverStatus.Stopped -> {}
            }
        }
    }

    LaunchedEffect(hasAction) {
        // Wait for the first frame: right after Settings closes, its rows still hold focus.
        withFrameNanos { }
        runCatching { if (hasAction) actionFocus.requestFocus() else settingsFocus.requestFocus() }
    }
}

@Composable
private fun Header(
    onOpenSettings: () -> Unit,
    settingsFocus: FocusRequester,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = stringResource(R.string.logo_description),
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onOpenSettings,
            colors = fenpoOutlinedButtonColors(),
            modifier = Modifier.focusRequester(settingsFocus),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.home_settings))
        }
    }
}

@Composable
private fun StatusPill(status: ReceiverStatus) {
    val (label, color) =
        when (status) {
            is ReceiverStatus.Ready -> {
                stringResource(R.string.status_ready) to FenpoCastColors.success
            }

            ReceiverStatus.Starting -> {
                stringResource(R.string.status_starting) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }

            ReceiverStatus.NoNetwork -> {
                stringResource(R.string.status_no_network) to MaterialTheme.colorScheme.error
            }

            ReceiverStatus.NeedsLocalNetworkPermission -> {
                stringResource(R.string.status_needs_permission) to MaterialTheme.colorScheme.primary
            }

            is ReceiverStatus.Failed -> {
                stringResource(R.string.status_failed) to MaterialTheme.colorScheme.error
            }

            ReceiverStatus.Stopped -> {
                stringResource(R.string.status_stopped) to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(color, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun Instructions(deviceName: String) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            InstructionCard(
                icon = R.drawable.ic_mirror,
                title = stringResource(R.string.home_mirror_title),
                steps =
                    listOf(
                        stringResource(R.string.home_mirror_step_1),
                        stringResource(R.string.home_mirror_step_2),
                        stringResource(R.string.home_mirror_step_3, deviceName),
                    ),
                modifier = Modifier.weight(1f),
            )
            InstructionCard(
                icon = R.drawable.ic_music,
                title = stringResource(R.string.home_media_title),
                steps =
                    listOf(
                        stringResource(R.string.home_media_step_1),
                        stringResource(R.string.home_media_step_2, deviceName),
                    ),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.home_same_network),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InstructionCard(
    @DrawableRes icon: Int,
    title: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors =
            SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.height(20.dp))
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(text = step, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Message(
    @DrawableRes icon: Int,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 760.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
