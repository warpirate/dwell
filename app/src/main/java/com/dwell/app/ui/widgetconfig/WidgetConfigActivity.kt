package com.dwell.app.ui.widgetconfig

import android.app.Activity
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
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dwell.app.ui.paywall.PaywallActivity
import com.dwell.app.ui.theme.DwellTheme
import com.dwell.app.widget.clock.ClockWidgetProvider
import com.dwell.app.widget.clock.WidgetPinReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Two entry points:
 *  - **Gallery mode** (no appWidgetId): opened from the Widgets tab. The user teases
 *    styles; "Add widget" pins a new widget carrying the chosen style, which
 *    [WidgetPinReceiver] applies once the launcher reports the new id.
 *  - **Configure mode** (real appWidgetId): reconfigures an existing widget in place.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private val viewModel: WidgetConfigViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val galleryMode get() = appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure-mode contract: default to CANCELED until the user commits.
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (!galleryMode) viewModel.load(appWidgetId)

        setContent {
            DwellTheme {
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                val selected by viewModel.selected.collectAsStateWithLifecycle()
                val needsUnlock by viewModel.needsUnlock.collectAsStateWithLifecycle()
                WidgetConfigScreen(
                    style = draft,
                    selected = selected,
                    isPremium = isPremium,
                    needsUnlock = needsUnlock,
                    onSelectPreset = viewModel::selectPreset,
                    onColor = viewModel::setColor,
                    onSize = viewModel::setSize,
                    onOpenPaywall = { startActivity(Intent(this, PaywallActivity::class.java)) },
                    onAdd = ::commit,
                )
            }
        }
    }

    private fun commit() {
        if (galleryMode) {
            pinNewWidget()
        } else {
            lifecycleScope.launch {
                viewModel.save(appWidgetId)
                ClockWidgetProvider.refresh(this@WidgetConfigActivity, appWidgetId)
                setResult(
                    Activity.RESULT_OK,
                    Intent().putExtras(bundleOf(AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId)),
                )
                finish()
            }
        }
    }

    /** Pin a new clock widget carrying the chosen style; [WidgetPinReceiver] applies it. */
    private fun pinNewWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, ClockWidgetProvider::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            val callback = Intent(this, WidgetPinReceiver::class.java)
                .putExtra(WidgetPinReceiver.EXTRA_STYLE, viewModel.draft.value.encode())
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                this, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            manager.requestPinAppWidget(provider, null, pending)
        }
        finish()
    }
}
