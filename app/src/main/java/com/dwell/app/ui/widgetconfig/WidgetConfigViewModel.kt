package com.dwell.app.ui.widgetconfig

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetPreset
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.data.widget.WallpaperPaletteExtractor
import com.dwell.app.data.widget.WallpaperSample
import com.dwell.app.data.widget.SampleWallpaper
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.data.widget.WidgetStyleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    private val store: WidgetStyleStore,
    private val billing: BillingRepository,
    entitlements: EntitlementRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _draft = MutableStateFlow(WidgetPreset.Default.style)
    val draft: StateFlow<WidgetStyle> = _draft.asStateFlow()

    /** The preset the draft currently matches, or null for a custom (engine) style. */
    val selected: StateFlow<WidgetPreset?> = _draft
        .map { WidgetPreset.of(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetPreset.Default)

    /** True when the current selection is a premium option and the user hasn't unlocked. */
    val needsUnlock: StateFlow<Boolean> = combine(_draft, isPremium) { style, premium ->
        !premium && WidgetPreset.of(style)?.free != true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _priceLabel = MutableStateFlow(FALLBACK_PRICE)
    val priceLabel: StateFlow<String> = _priceLabel.asStateFlow()

    init {
        viewModelScope.launch { _priceLabel.value = billing.formattedPrice() ?: FALLBACK_PRICE }
    }

    fun load(appWidgetId: Int) {
        viewModelScope.launch { _draft.value = store.get(appWidgetId) }
    }

    /** Pick a curated preset. Free presets apply; premium presets preview (the tease). */
    fun selectPreset(preset: WidgetPreset) = _draft.update { preset.style }

    /**
     * The moat, demonstrated: sample a wallpaper we own, extract its palette, and recolour the
     * widget text to match. (POC uses a generated stand-in wallpaper; the real path samples the
     * bitmap the user applied from Dwell.)
     */
    fun matchWallpaper(sample: WallpaperSample) {
        viewModelScope.launch {
            val argb = WallpaperPaletteExtractor.match(SampleWallpaper.of(sample))
            _draft.update { it.copy(matchedArgb = argb) }
        }
    }

    // The open style engine — premium only, used by the inline controls.
    fun setColor(color: WidgetColor) = _draft.update { it.copy(color = color) }
    fun setSize(size: WidgetSize) = _draft.update { it.copy(size = size) }
    fun setOpacity(opacity: Int) = _draft.update { it.copy(opacity = opacity).coerced() }

    /** Launch the one-time unlock; premium flips reactively via [isPremium] on success. */
    fun unlock(activity: Activity) {
        viewModelScope.launch { billing.launchPurchase(activity) }
    }

    suspend fun save(appWidgetId: Int) = store.save(appWidgetId, _draft.value)

    private companion object {
        const val FALLBACK_PRICE = "₹299"
    }
}
