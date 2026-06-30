package com.dwell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.dwell.app.ui.theme.warmRadialBrush

/**
 * The warm-radial canvas every neutral screen sits on. Owns the brush (memoized
 * on size), the dark/luminance check, and optional system-bar insets. Use this
 * instead of hand-rolling the BoxWithConstraints + warmRadialBrush dance.
 */
@Composable
fun DwellScaffold(
    modifier: Modifier = Modifier,
    gradient: Boolean = true,
    applyStatusBarPadding: Boolean = false,
    applyNavBarPadding: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        var inner = Modifier.fillMaxSize()
        inner = if (gradient) {
            val brush = remember(dark, wPx, hPx) { warmRadialBrush(dark, wPx, hPx) }
            inner.background(brush)
        } else {
            inner.background(MaterialTheme.colorScheme.background)
        }
        if (applyStatusBarPadding) inner = inner.statusBarsPadding()
        if (applyNavBarPadding) inner = inner.navigationBarsPadding()
        Box(modifier = inner, content = content)
    }
}
