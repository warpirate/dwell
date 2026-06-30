package com.dwell.app.ui.widgetconfig

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DwellSpacing
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
                DwellScaffold {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(DwellSpacing.screenGutter),
                        verticalArrangement = Arrangement.spacedBy(DwellSpacing.md),
                    ) {
                        Text(text = draft.color.name, style = MaterialTheme.typography.titleMedium)
                        if (isPremium) {
                            DwellPrimaryButton(text = "Cream", onClick = { viewModel.setColor(WidgetColor.CREAM) })
                            DwellPrimaryButton(text = "Green", onClick = { viewModel.setColor(WidgetColor.GREEN) })
                            DwellPrimaryButton(text = "Charcoal", onClick = { viewModel.setColor(WidgetColor.CHARCOAL) })
                        } else {
                            Text(
                                text = "Unlock to customize your widget.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DwellPrimaryButton(text = "Unlock Dwell", onClick = { /* route to unlock flow */ })
                        }
                        DwellPrimaryButton(text = "Save", onClick = ::commit)
                    }
                }
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
