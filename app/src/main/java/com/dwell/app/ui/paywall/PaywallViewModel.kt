package com.dwell.app.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    /** The screen finishes when this flips true (verification wrote the flag). */
    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _priceLabel = MutableStateFlow(FALLBACK_PRICE)
    val priceLabel: StateFlow<String> = _priceLabel.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch { _priceLabel.value = billing.formattedPrice() ?: FALLBACK_PRICE }
    }

    fun unlock(activity: Activity) {
        viewModelScope.launch {
            _loading.value = true
            billing.launchPurchase(activity)
            _loading.value = false // premium then flips via isPremium once verified
        }
    }

    private companion object {
        const val FALLBACK_PRICE = "₹299"
    }
}
