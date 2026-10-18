package com.fenpo.cast.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

// Fenpo's Material 3 roles (Fenpo for Android, core/designsystem/theme/Color.kt), mapped onto
// the TV Material scheme. Çiçek is the accent, Mürekkep the text, Yaprak the "ready" state.
// Dark follows the iPhone app: a black page with #1C1C22 cards, which also suits OLED TVs.

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFFC93A63),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E0019),
        inversePrimary = Color(0xFFFF86A6),
        secondary = Color(0xFF7A5260),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF7DCE4),
        onSecondaryContainer = Color(0xFF2B151C),
        tertiary = Color(0xFF4E54C8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E0FF),
        onTertiaryContainer = Color(0xFF0A0E68),
        background = Color(0xFFF2F3F8),
        onBackground = Color(0xFF1B1F3B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B1F3B),
        surfaceVariant = Color(0xFFDCE6F5),
        onSurfaceVariant = Color(0xFF6B7090),
        surfaceTint = Color(0xFFC93A63),
        inverseSurface = Color(0xFF1B1F3B),
        inverseOnSurface = Color(0xFFF2F3F8),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        border = Color(0xFF8A8FA8),
        borderVariant = Color(0xFFE2E3EA),
        scrim = Color(0xFF000000),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFF86A6),
        onPrimary = Color(0xFF5C1130),
        primaryContainer = Color(0xFF7E2545),
        onPrimaryContainer = Color(0xFFFFD9E2),
        inversePrimary = Color(0xFFC93A63),
        secondary = Color(0xFFE3BDC8),
        onSecondary = Color(0xFF432932),
        secondaryContainer = Color(0xFF5A3341),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFBEC2FF),
        onTertiary = Color(0xFF1B2084),
        tertiaryContainer = Color(0xFF353BAF),
        onTertiaryContainer = Color(0xFFE0E0FF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF4F5FA),
        surface = Color(0xFF1C1C22),
        onSurface = Color(0xFFF4F5FA),
        surfaceVariant = Color(0xFF303039),
        onSurfaceVariant = Color(0xFFA2A6BD),
        surfaceTint = Color(0xFFFF86A6),
        inverseSurface = Color(0xFFF4F5FA),
        inverseOnSurface = Color(0xFF1B1F3B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        border = Color(0xFF6D7190),
        borderVariant = Color(0xFF333338),
        scrim = Color(0xFF000000),
    )

/** Yaprak: the "ready" and success colour, which Material has no role for. */
internal val LightSuccess = Color(0xFF2F8F6B)
internal val DarkSuccess = Color(0xFF7DD8B0)

/**
 * Mirrored and streamed video sits on black in both themes: it is the iPhone's picture, and a
 * light frame around a film would glare. Messages on top of video use white on a dark veil.
 */
internal val VideoBackground = Color(0xFF000000)
internal val VideoScrim = Color(0x99000000)
internal val OnVideo = Color(0xFFFFFFFF)
