package com.dwell.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dwell.app.data.widget.CatalogWidget
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.screens.widgets.WidgetGallery
import com.dwell.app.widget.date.DateWidgetProvider
import com.dwell.app.widget.poster.PosterClockWidgetProvider

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DwellScaffold(modifier = modifier) {
        WidgetGallery(
            onSelect = { widget ->
                when (widget) {
                    // Clock is the poster: a time-of-day scene, pinned straight to home.
                    CatalogWidget.CLOCK -> pinWidget(context, PosterClockWidgetProvider::class.java)
                    // Date has no styling yet — pin it straight to the home screen.
                    CatalogWidget.DATE -> pinWidget(context, DateWidgetProvider::class.java)
                    else -> Unit // Soon cards aren't tappable.
                }
            },
        )
    }
}

/** Ask the launcher to pin a widget for [provider]; no-op on launchers that don't support it. */
private fun pinWidget(context: Context, provider: Class<*>) {
    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
