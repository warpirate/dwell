package com.dwell.app.widget.date

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.dwell.app.R

/**
 * The editorial date widget. Its three [android.widget.TextClock] rows update themselves, so
 * there's no per-widget style to resolve yet — onUpdate just pushes the layout. (When the style
 * engine grows to cover Date, this gains a store like [com.dwell.app.widget.clock].)
 */
class DateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_date)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }
}
