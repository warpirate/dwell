package com.dwell.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.dwell.app.ui.theme.dwellSoftShadow

/**
 * Layered warm surface with the centralized soft warm shadow and the single 8dp
 * radius. The one place depth is defined, so every card reads identically and
 * the minSdk-26/27 shadow caveat is handled once (in dwellSoftShadow).
 */
@Composable
fun DwellCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = modifier.dwellSoftShadow(shape),
    ) {
        Column(content = content)
    }
}
