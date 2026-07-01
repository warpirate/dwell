package com.dwell.app.widget.poster

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.dwell.app.R
import java.util.Calendar

/**
 * The poster clock: a full-bleed painted scene ([PosterRenderer]) whose palette tracks the
 * hour, with live TextClock time + date stacked on top. The scene is repainted on update
 * (and on resize / the ~30-min period in poster_clock_widget_info.xml) — the time itself
 * ticks on its own via TextClock, so no per-minute alarm is needed.
 */
class PosterClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        render(context, manager, id)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        val p = PosterRenderer.paletteFor(hour)

        // Size the bitmap to the widget's current max extent (dp → px); fall back to a
        // sensible wide default before the host reports its options.
        val dm = context.resources.displayMetrics
        val opts = manager.getAppWidgetOptions(id)
        fun px(key: String, fallbackDp: Int): Int {
            val dp = opts.getInt(key, 0).takeIf { it > 0 } ?: fallbackDp
            return (dp * dm.density).toInt()
        }
        var w = px(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320)
        var h = px(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
        // Cap the longest side so the bitmap stays small enough to cross the RemoteViews
        // Binder limit; centerCrop scales it back up over the (smooth-gradient) scene.
        val cap = 512
        if (w > cap) { h = h * cap / w; w = cap }
        if (h > cap) { w = w * cap / h; h = cap }

        try {
            val bmp = PosterRenderer.render(w, h, hour)
            val views = RemoteViews(context.packageName, R.layout.widget_poster).apply {
                setImageViewBitmap(R.id.poster_bg, bmp)
                // Recolor the overlaid chrome to the palette ink so it reads on this sky.
                setTextColor(R.id.poster_time, p.ink)
                setTextColor(R.id.poster_date, withAlpha(p.ink, 235))
                setInt(R.id.poster_rule, "setColorFilter", withAlpha(p.ink, 110))
                // The accent dot stays green — the single brand mark, never recolored.
            }
            manager.updateAppWidget(id, views)
        } catch (t: Throwable) {
            Log.e(TAG, "render/update failed id=$id", t)
        }
    }

    private fun withAlpha(color: Int, a: Int) =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val TAG = "PosterClock"
    }
}
