package com.dwell.app.widget.clock

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the successful-pin callback from [AppWidgetManager.requestPinAppWidget] and
 * applies the style the user chose in the gallery to the freshly-placed widget.
 */
@AndroidEntryPoint
class WidgetPinReceiver : BroadcastReceiver() {

    @Inject lateinit var store: WidgetStyleStore
    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val style = WidgetStyle.decode(intent.getStringExtra(EXTRA_STYLE).orEmpty())
        val pending = goAsync()
        scope.launch {
            try {
                store.save(id, style)
                ClockWidgetProvider.refresh(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_STYLE = "com.dwell.app.EXTRA_STYLE"
    }
}
