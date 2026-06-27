package com.dwell.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dwell.app.R

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.nav_widgets),
        subtitle = stringResource(R.string.placeholder_subtitle),
        modifier = modifier,
    )
}
