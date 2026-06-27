package com.dwell.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Raw design tokens. Exact values from the UI/UX doc, section 2.
// The neutral ramp is warm-gray, not pure gray, so the chrome reads calm.
// ---------------------------------------------------------------------------

// Light
private val LightBg = Color(0xFFFAFAF8)
private val LightSurface = Color(0xFFFFFFFF)
private val LightTextPrimary = Color(0xFF1A1A18)
private val LightTextSecondary = Color(0xFF6B6B66)
private val LightDivider = Color(0xFFEAEAE5)

// Dark
private val DarkBg = Color(0xFF0E0E0D)
private val DarkSurface = Color(0xFF1A1A18)
private val DarkTextPrimary = Color(0xFFF5F5F2)
private val DarkTextSecondary = Color(0xFF9A9A94)
private val DarkDivider = Color(0xFF2A2A27)

// Accent. Brand value #3A5A40 on light. On dark, #3A5A40 as a foreground color
// only reaches ~2.25:1 against the dark surface, which fails WCAG AA. So dark
// uses a lighter sibling of the same green (~5.2:1) to keep the accent legible.
// This is the one place the exact-hex token and the AA-contrast requirement
// conflict; see the handoff notes. The accent stays a frame color either way.
val AccentLight = Color(0xFF3A5A40)
val AccentDark = Color(0xFF6E9576)

val DwellLightColorScheme: ColorScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = LightBg,
    primaryContainer = AccentLight,
    onPrimaryContainer = LightBg,
    secondary = AccentLight,
    onSecondary = LightBg,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = LightDivider,
    scrim = Color(0xFF000000),
)

val DwellDarkColorScheme: ColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = DarkBg,
    primaryContainer = AccentDark,
    onPrimaryContainer = DarkBg,
    secondary = AccentDark,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    scrim = Color(0xFF000000),
)
