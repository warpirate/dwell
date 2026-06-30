package com.dwell.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Long, eased, warm vertical scrim for text over imagery: transparent at top,
 * warm near-black at the bottom. Multi-stop (not a 2-stop linear) so there is no
 * visible band, with the midpoint biased toward the dark end per Material's
 * scrim guidance. Guarantees readable contrast over arbitrary wallpapers.
 */
fun warmScrimVertical(): Brush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.45f to ScrimWarm.copy(alpha = 0.20f),
    0.72f to ScrimWarm.copy(alpha = 0.55f),
    1.0f to ScrimWarm.copy(alpha = 0.88f),
)

/** Shorter warm top-down scrim, for a control (back button) over imagery. */
fun topScrim(): Brush = Brush.verticalGradient(
    0.0f to ScrimWarm.copy(alpha = 0.55f),
    1.0f to Color.Transparent,
)
