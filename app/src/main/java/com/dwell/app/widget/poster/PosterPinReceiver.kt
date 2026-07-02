package com.dwell.app.widget.poster

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.PosterWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the successful-pin callback from [AppWidgetManager.requestPinAppWidget] and
 * applies the weather condition the user chose in [com.dwell.app.ui.widgetconfig.PosterWeatherConfigActivity]
 * to the freshly-placed poster widget.
 */
@AndroidEntryPoint
class PosterPinReceiver : BroadcastReceiver() {

    @Inject lateinit var store: PosterWeatherStore
    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val weather = PosterWeather.decode(intent.getStringExtra(EXTRA_WEATHER).orEmpty())
        val pending = goAsync()
        scope.launch {
            try {
                store.save(id, weather)
                PosterClockWidgetProvider.refresh(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_WEATHER = "com.dwell.app.EXTRA_POSTER_WEATHER"
    }
}
