package com.dwell.app.ui.widgetconfig

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.ui.theme.DwellTheme
import com.dwell.app.widget.poster.PosterClockWidgetProvider
import com.dwell.app.widget.poster.PosterPinReceiver
import dagger.hilt.android.AndroidEntryPoint

/** Gallery-mode only — always pins a new poster widget; there is no android:configure
 *  declared on the provider, so this is never launched to reconfigure a placed one. */
@AndroidEntryPoint
class PosterWeatherConfigActivity : ComponentActivity() {

    private val viewModel: PosterWeatherConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DwellTheme {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                PosterWeatherConfigScreen(
                    weather = draft,
                    onSelectCondition = viewModel::setCondition,
                    onIntensity = viewModel::setIntensity,
                    onAdd = ::pinWidget,
                )
            }
        }
    }

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, PosterClockWidgetProvider::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            val callback = Intent(this, PosterPinReceiver::class.java)
                .putExtra(PosterPinReceiver.EXTRA_WEATHER, viewModel.draft.value.encode())
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                this, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            manager.requestPinAppWidget(provider, null, pending)
        }
        finish()
    }
}
