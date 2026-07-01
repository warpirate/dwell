package com.dwell.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dwell.app.R
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.ui.widgetconfig.WidgetConfigActivity

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DwellScaffold(modifier = modifier) {
        Column(Modifier.fillMaxSize().padding(DwellSpacing.screenGutter)) {
            Text(
                text = stringResource(R.string.widgets_clock_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.widgets_clock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(DwellSpacing.lg))
            DwellPrimaryButton(
                text = stringResource(R.string.widgets_add),
                onClick = {
                    // Open the gallery (tease) — pick a style, then it pins the widget.
                    context.startActivity(Intent(context, WidgetConfigActivity::class.java))
                },
            )
            Spacer(Modifier.height(DwellSpacing.xl))
            Text(
                text = stringResource(R.string.widgets_more_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
