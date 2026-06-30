package com.dwell.app.widget.clock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import com.dwell.app.R
import com.dwell.app.data.widget.WidgetStyleResolver
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var store: WidgetStyleStore

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { id -> renderOne(context, manager, id) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun renderOne(context: Context, manager: AppWidgetManager, id: Int) {
        val style = store.get(id)
        val views = RemoteViews(context.packageName, R.layout.widget_clock)
        // The premium style recolors and resizes only the Fraunces time. The tracked
        // date and the green accent dot are fixed editorial chrome (set in the layout).
        // Never setBackgroundColor here — it would replace the rounded gradient drawable.
        views.setTextColor(R.id.widget_time, WidgetStyleResolver.textColorArgb(style))
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, WidgetStyleResolver.timeSizeSp(style))
        manager.updateAppWidget(id, views)
    }

    companion object {
        /** Re-render a single widget after its config changes. */
        fun refresh(context: Context, id: Int) {
            val intent = Intent(context, ClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            }
            context.sendBroadcast(intent)
        }
    }
}
