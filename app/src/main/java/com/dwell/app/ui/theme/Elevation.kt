package com.dwell.app.ui.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The brand soft warm shadow — the only elevation in the system. Spec from the
 * design pass: a long, soft, warm-tinted drop shadow over neutral surfaces only
 * (never over imagery).
 *
 * The warm [DwellShadow] tint via spotColor/ambientColor is only honored on
 * API 28+. On 26/27 a tinted shadow falls back to a hard black halo, so we skip
 * the colored shadow there (the warm surface on the warm ground still reads
 * layered). Centralized here so the minSdk caveat is handled once.
 */
fun Modifier.dwellSoftShadow(shape: Shape): Modifier =
    if (Build.VERSION.SDK_INT >= 28) {
        this.shadow(
            elevation = 30.dp,
            shape = shape,
            ambientColor = DwellShadow,
            spotColor = DwellShadow,
            clip = false,
        )
    } else {
        this
    }
