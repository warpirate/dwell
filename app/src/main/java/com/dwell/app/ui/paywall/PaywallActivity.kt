package com.dwell.app.ui.paywall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.ui.theme.DwellTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The one-time unlock pitch. Reached from any locked control (a premium preset or the
 * style engine). Closes itself the moment the entitlement flips — the purchase is
 * verified server-side, so [PaywallViewModel.isPremium] is the single source of truth.
 */
@AndroidEntryPoint
class PaywallActivity : ComponentActivity() {

    private val viewModel: PaywallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DwellTheme {
                val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
                val price by viewModel.priceLabel.collectAsStateWithLifecycle()
                val loading by viewModel.loading.collectAsStateWithLifecycle()

                LaunchedEffect(isPremium) { if (isPremium) finish() }

                PaywallScreen(
                    priceLabel = price,
                    loading = loading,
                    onUnlock = { viewModel.unlock(this@PaywallActivity) },
                    onDismiss = ::finish,
                )
            }
        }
    }
}
