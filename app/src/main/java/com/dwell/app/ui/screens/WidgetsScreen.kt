package com.dwell.app.ui.screens

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.screens.widgets.WidgetGallery
import com.dwell.app.ui.widgetconfig.WidgetConfigActivity

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DwellScaffold(modifier = modifier) {
        WidgetGallery(
            // Tapping the live Clock card opens the configurator (tease → pin).
            onOpenClock = { context.startActivity(Intent(context, WidgetConfigActivity::class.java)) },
        )
    }
}
