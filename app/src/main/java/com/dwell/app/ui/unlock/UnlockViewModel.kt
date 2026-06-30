package com.dwell.app.ui.unlock

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    entitlements: EntitlementRepository,
    private val billing: BillingRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Last purchase outcome, for a toast/snackbar. Null until a purchase is attempted. */
    var lastResult: PurchaseResult? = null
        private set

    fun unlock(activity: Activity) {
        viewModelScope.launch { lastResult = billing.launchPurchase(activity) }
    }
}
