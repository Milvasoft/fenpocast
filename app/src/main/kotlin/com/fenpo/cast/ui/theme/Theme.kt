package com.fenpo.cast.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme

private val LocalSuccessColor = staticCompositionLocalOf { LightSuccess }

/**
 * Applies the Fenpo colours to the TV Material components.
 *
 * @param darkTheme Whether to use the dark scheme; follows the system unless the user chose
 *   Light or Dark in Settings.
 * @param content The content to theme.
 */
@Composable
fun FenpoCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSuccessColor provides if (darkTheme) DarkSuccess else LightSuccess) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}

/** Fenpo colours outside the Material scheme. */
object FenpoCastColors {
    /** Yaprak, for the "ready" state. */
    val success: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSuccessColor.current

    /** Behind mirrored and streamed video, in both themes. */
    val videoBackground: Color get() = VideoBackground

    /** Veil behind messages shown over video. */
    val videoScrim: Color get() = VideoScrim

    /** Text over [videoScrim]. */
    val onVideo: Color get() = OnVideo
}
