package com.fenpo.cast.ui.session

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fenpo.cast.R
import com.fenpo.cast.media.StreamState
import com.fenpo.cast.receiver.NowPlaying
import com.fenpo.cast.receiver.Session
import com.fenpo.cast.ui.theme.FenpoCastColors
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The mirrored iPhone screen, as large as the TV allows at the iPhone's aspect ratio, on
 * [FenpoCastColors.videoBackground] in both themes.
 *
 * @param onSurface Receives the decoder surface when it appears and null when it goes away.
 */
@Composable
fun MirrorScreen(
    session: Session.Mirroring,
    onSurface: (android.view.Surface?) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val clientName = session.clientName.ifEmpty { stringResource(R.string.client_unknown) }
    val ratio =
        if (session.pictureWidth > 0 && session.pictureHeight > 0) {
            session.pictureWidth.toFloat() / session.pictureHeight
        } else {
            16f / 9f
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(FenpoCastColors.videoBackground),
    ) {
        val surfaceModifier =
            if (ratio >= 16f / 9f) {
                Modifier.fillMaxWidth().aspectRatio(ratio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(ratio)
            }
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = onSurface(holder.surface)

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) = onSurface(null)
                        },
                    )
                }
            },
            modifier = surfaceModifier,
        )
        if (session.pictureWidth == 0) {
            OverlayMessage(stringResource(R.string.mirror_waiting, clientName))
        } else if (session.paused) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(FenpoCastColors.videoScrim),
            ) {
                OverlayMessage(stringResource(R.string.mirror_paused, clientName))
            }
        }
    }
}

/**
 * A video the iPhone handed over (YouTube and similar apps), played by Media3 on the TV.
 *
 * @param onView Receives the view to draw on, and null when the screen goes away.
 */
@Composable
fun StreamScreen(
    state: StreamState,
    onView: (SurfaceView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().background(FenpoCastColors.videoBackground),
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also(onView) },
            onRelease = { onView(null) },
            modifier = Modifier.fillMaxSize(),
        )
        if (state.buffering && !state.playing) {
            OverlayMessage(stringResource(R.string.stream_loading))
        }
    }
}

/** What a music app is playing: cover art, track, artist, album and progress. */
@Composable
fun MusicScreen(
    session: Session.Music,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val nowPlaying = session.nowPlaying
    val clientName = session.clientName.ifEmpty { stringResource(R.string.client_unknown) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 96.dp, vertical = 64.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            CoverArt(nowPlaying)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title ?: stringResource(R.string.now_playing_unknown_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                nowPlaying.artist?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                nowPlaying.album?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (nowPlaying.durationMs > 0) {
                    Spacer(modifier = Modifier.height(36.dp))
                    Progress(nowPlaying)
                }
                Spacer(modifier = Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phone),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.now_playing_from, clientName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverArt(nowPlaying: NowPlaying) {
    val shape = RoundedCornerShape(24.dp)
    val cover = nowPlaying.coverArt
    if (cover != null) {
        Image(
            bitmap = remember(cover) { cover.asImageBitmap() },
            contentDescription = stringResource(R.string.cover_art_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(360.dp).clip(shape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(360.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(96.dp),
            )
        }
    }
}

@Composable
private fun Progress(nowPlaying: NowPlaying) {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying.positionAtMs) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val position = (nowPlaying.positionMs + (now - nowPlaying.positionAtMs)).coerceIn(0, nowPlaying.durationMs)
    val fraction = position.toFloat() / nowPlaying.durationMs
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(nowPlaying.durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverlayMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = FenpoCastColors.onVideo,
        modifier =
            Modifier
                .background(FenpoCastColors.videoScrim, RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

/** Keeps the TV from dimming or starting its screen saver while this is on screen. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Formats milliseconds as m:ss or h:mm:ss. */
internal fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
