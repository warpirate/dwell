package com.dwell.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.ui.theme.DwellTheme

/**
 * Empty themed screen used for every Phase 0 destination. Shows the screen name
 * in the display face and a quiet subtitle, centered with generous whitespace.
 * Replaced by the real screen as each phase builds it.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    DwellScaffold(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DwellSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Preview(name = "Placeholder light")
@Composable
private fun PlaceholderLightPreview() {
    DwellTheme(darkTheme = false) {
        PlaceholderScreen(title = "Wallpapers", subtitle = "Coming soon")
    }
}

@Preview(name = "Placeholder dark")
@Composable
private fun PlaceholderDarkPreview() {
    DwellTheme(darkTheme = true) {
        PlaceholderScreen(title = "Wallpapers", subtitle = "Coming soon")
    }
}
