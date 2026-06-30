package com.dwell.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Warm radial background with a top-left light source, from the Claude Design
 * pass. Light is a cream-to-sand wash; dark is a warm brown-black. Used on
 * full-screen surfaces (sign-in) to give the chrome depth instead of flat fill.
 * Plain function (not @Composable) so it can be built inside remember().
 */
fun warmRadialBrush(dark: Boolean, widthPx: Float, heightPx: Float): Brush {
    val colors = if (dark) {
        listOf(Color(0xFF2C2822), Color(0xFF1B1815), Color(0xFF100E0C))
    } else {
        listOf(Color(0xFFF7F1E6), Color(0xFFEFE1CD), Color(0xFFE3CBAE))
    }
    return Brush.radialGradient(
        colors = colors,
        center = Offset(widthPx * 0.3f, heightPx * 0.08f),
        radius = maxOf(widthPx, heightPx) * 1.25f,
    )
}
