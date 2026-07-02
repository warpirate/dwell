package com.dwell.app.widget.poster

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.dwell.app.R
import com.dwell.app.data.widget.PosterWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * The poster clock: a full-bleed painted scene ([PosterRenderer]) whose palette tracks the
 * hour — and, when the owner has chosen one via [com.dwell.app.ui.widgetconfig.PosterWeatherConfigActivity],
 * a weather layer on top — with live TextClock time + date stacked on top. The scene is
 * repainted on update (and on resize / the ~30-min period in poster_clock_widget_info.xml) —
 * the time itself ticks on its own via TextClock, so no per-minute alarm is needed.
 */
@AndroidEntryPoint
class PosterClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var weatherStore: PosterWeatherStore

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { render(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                render(context, manager, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        val weather = weatherStore.get(id)
        val p = PosterRenderer.paletteFor(hour)

        // Size the bitmap to the widget's current max extent (dp → px); fall back to a
        // sensible wide default before the host reports its options.
        val dm = context.resources.displayMetrics
        val opts = manager.getAppWidgetOptions(id)
        fun px(key: String, fallbackDp: Int): Int {
            val dp = opts.getInt(key, 0).takeIf { it > 0 } ?: fallbackDp
            return (dp * dm.density).toInt()
        }
        // Portrait aspect: MIN_WIDTH is the width in the current (portrait) orientation and
        // MAX_HEIGHT its height — together they match what's actually on screen, so the
        // host's centerCrop barely crops and the sun/moon never scrolls out of frame.
        var w = px(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
        var h = px(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
        // Cap by total PIXEL BUDGET (not a single-side px cap) so the bitmap stays under the
        // RemoteViews Binder limit without downscaling typical widgets far below native
        // density. ARGB_8888 is 4 bytes/px; 900_000 bytes leaves headroom under the ~1MB
        // Binder transaction limit. A flat "longest side ≤512px" cap was far too aggressive
        // at real densities (a 300dp-wide widget is ~787px at 2.6x density) — it forced a
        // downscale that centerCrop then stretched back up ~1.5x, blurring the whole scene.
        // Only oversized resizes (e.g. maxResizeWidth 400dp at 3x+ density) still hit this cap.
        val maxPixels = 225_000L // 900_000 bytes / 4 bytes-per-pixel (ARGB_8888)
        if (w.toLong() * h > maxPixels) {
            val scale = kotlin.math.sqrt(maxPixels.toDouble() / (w.toLong() * h))
            w = (w * scale).toInt().coerceAtLeast(1)
            h = (h * scale).toInt().coerceAtLeast(1)
        }

        try {
            val bmp = PosterRenderer.render(w, h, hour, weather)
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

    companion object {
        private const val TAG = "PosterClock"

        /** Re-render a single poster widget after its weather config changes. */
        fun refresh(context: Context, id: Int) {
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
            val intent = Intent(context, PosterClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            }
            context.sendBroadcast(intent)
        }
    }
}
