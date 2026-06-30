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
        val textColor = WidgetStyleResolver.textColorArgb(style)
        views.setTextColor(R.id.widget_time, textColor)
        views.setTextColor(R.id.widget_date, textColor)
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, WidgetStyleResolver.timeSizeSp(style))
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, WidgetStyleResolver.dateSizeSp(style))
        // NB: do not setBackgroundColor here — it replaces the rounded @drawable/widget_bg
        // with a flat rect. Background opacity for the RemoteViews clock is deferred
        // (needs API 31 background tint, or the Glance widgets).
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
