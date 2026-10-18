package com.fenpo.cast.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButtonDefaults

// TV Material derives the focused button colours from roles that, in Fenpo's light scheme, come
// out the same as the page, so the focused button would not stand out. Focus is the only cursor a
// remote has: it is always the inverse surface (Mürekkep in light, near-white in dark).

/** The main action on a screen: Çiçek at rest, inverse when focused. */
@Composable
fun fenpoButtonColors(): ButtonColors =
    ButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    )

/** Secondary actions: outlined at rest, inverse when focused. */
@Composable
fun fenpoOutlinedButtonColors(): ButtonColors =
    OutlinedButtonDefaults.colors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        pressedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        pressedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    )
