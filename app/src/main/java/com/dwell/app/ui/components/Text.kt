package com.dwell.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The tracked-caps micro-label / eyebrow (DWELL, BACKGROUND, LIVE PREVIEW). The
 * single home of the caps-tracked identity — encodes uppercase + 1.5sp tracking
 * once, so no screen hand-rolls .uppercase() + inline letterSpacing again.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
        color = color,
        modifier = modifier,
    )
}

/**
 * Fraunces hero/title routed through the type scale (displayLarge 28sp for screen
 * titles, displayMedium 40sp for the auth hero) so serif sizing lives in the
 * Typography, not as a per-screen magic number.
 */
@Composable
fun DwellDisplayTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(text = text, style = style, color = color, modifier = modifier)
}
