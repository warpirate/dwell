package com.dwell.app.ui.widgetconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val store: WidgetStyleStore,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _draft = MutableStateFlow(WidgetStyle.Default)
    val draft: StateFlow<WidgetStyle> = _draft.asStateFlow()

    fun load(appWidgetId: Int) {
        viewModelScope.launch { _draft.value = store.get(appWidgetId) }
    }

    fun setColor(color: WidgetColor) = _draft.update { it.copy(color = color) }
    fun setSize(size: WidgetSize) = _draft.update { it.copy(size = size) }
    fun setOpacity(opacity: Int) = _draft.update { it.copy(opacity = opacity).coerced() }

    suspend fun save(appWidgetId: Int) = store.save(appWidgetId, _draft.value)
}
