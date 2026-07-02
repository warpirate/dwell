package com.dwell.app.ui.widgetconfig

import androidx.lifecycle.ViewModel
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Gallery-mode only — the poster widget has no android:configure, so there is no
 *  existing appWidgetId to load; this always drafts a weather choice for a new pin. */
@HiltViewModel
class PosterWeatherConfigViewModel @Inject constructor() : ViewModel() {

    private val _draft = MutableStateFlow(PosterWeather.Default)
    val draft: StateFlow<PosterWeather> = _draft.asStateFlow()

    fun setCondition(condition: WeatherCondition) = _draft.update { it.copy(condition = condition) }

    fun setIntensity(intensity: Int) = _draft.update { it.copy(intensity = intensity).coerced() }
}
