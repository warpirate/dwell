package com.dwell.app.ui.widgetconfig

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dwell.app.ui.theme.DwellTheme
import com.dwell.app.widget.clock.ClockWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private val viewModel: WidgetConfigViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AppWidget config contract: default to CANCELED until the user saves.
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        viewModel.load(appWidgetId)

        setContent {
            DwellTheme {
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                WidgetConfigScreen(
                    style = draft,
                    isPremium = isPremium,
                    onColor = viewModel::setColor,
                    onSize = viewModel::setSize,
                    onUnlock = { viewModel.unlock(this) },
                    onSave = ::commit,
                )
            }
        }
    }

    private fun commit() {
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
