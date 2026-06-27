package com.dwell.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * App theme.
 *
 * [darkTheme] defaults to the system setting. It is a parameter, not read
 * internally, so a later manual Light/Dark/System override (More tab, backed by
 * DataStore) becomes "pass a resolved boolean in" rather than a rewrite.
 *
 * Brand tokens are fixed, so Material You dynamic color is intentionally not
 * used; the accent and neutral ramp must stay exact across devices.
 */
@Composable
fun DwellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DwellDarkColorScheme else DwellLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DwellTypography,
        shapes = DwellShapes,
        content = content,
    )
}
