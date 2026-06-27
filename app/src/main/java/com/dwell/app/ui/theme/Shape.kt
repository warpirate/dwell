package com.dwell.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// A single small radius everywhere (UI/UX doc 2: "one small radius (8dp) on
// cards and sheets. Consistent, not mixed."). All M3 size slots map to 8dp.
private val DwellCorner = RoundedCornerShape(8.dp)

val DwellShapes = Shapes(
    extraSmall = DwellCorner,
    small = DwellCorner,
    medium = DwellCorner,
    large = DwellCorner,
    extraLarge = DwellCorner,
)
