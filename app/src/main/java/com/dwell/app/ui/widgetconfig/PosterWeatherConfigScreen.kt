package com.dwell.app.ui.widgetconfig

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.AccentFill
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.widget.poster.PosterRenderer
import java.util.Calendar

private const val PREVIEW_WIDTH = 320
private const val PREVIEW_HEIGHT = 190
private const val POSTER_ASPECT = 357f / 210f

/** The 8-condition + intensity picker, reached from the Widgets tab before pinning the
 *  poster clock. The live preview calls the real [PosterRenderer] so what's shown is
 *  exactly what ships on the placed widget. */
@Composable
fun PosterWeatherConfigScreen(
    weather: PosterWeather,
    onSelectCondition: (WeatherCondition) -> Unit,
    onIntensity: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DwellScaffold(modifier = modifier, applyStatusBarPadding = true, applyNavBarPadding = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DwellSpacing.screenGutter, vertical = DwellSpacing.xl),
        ) {
            Text(
                text = "Weather",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(DwellSpacing.lg))

            LivePreview(weather)
            Spacer(Modifier.height(DwellSpacing.xl))

            SectionLabel("Condition")
            Spacer(Modifier.height(DwellSpacing.md))
            val row1 = WeatherCondition.entries.take(4)
            val row2 = WeatherCondition.entries.drop(4)
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                row1.forEach { ConditionPill(it, weather.condition == it, Modifier.weight(1f), onSelectCondition) }
            }
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                row2.forEach { ConditionPill(it, weather.condition == it, Modifier.weight(1f), onSelectCondition) }
            }

            if (weather.condition != WeatherCondition.CLEAR) {
                Spacer(Modifier.height(DwellSpacing.xl))
                SectionLabel(intensityLabel(weather.intensity))
                Spacer(Modifier.height(DwellSpacing.sm))
                Slider(
                    value = weather.intensity.toFloat(),
                    onValueChange = { onIntensity(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = AccentFill, activeTrackColor = AccentFill),
                )
            }

            Spacer(Modifier.height(DwellSpacing.section))
            DwellPrimaryButton(text = "Add widget", onClick = onAdd)
        }
    }
}

private fun intensityLabel(intensity: Int): String {
    val word = when {
        intensity < 40 -> "LIGHT"
        intensity > 85 -> "HEAVY"
        else -> "MODERATE"
    }
    return "INTENSITY — $word ($intensity%)"
}

@Composable
private fun LivePreview(weather: PosterWeather) {
    val hour = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    }
    val bitmap = remember(weather) {
        PosterRenderer.render(PREVIEW_WIDTH, PREVIEW_HEIGHT, hour, weather).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(POSTER_ASPECT)
            .clip(RoundedCornerShape(26.dp)),
    )
}

@Composable
private fun ConditionPill(
    condition: WeatherCondition,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (WeatherCondition) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentFill else Color(0xFF1B1815))
            .border(1.dp, if (selected) AccentFill else Color(0x14ECE7DD), RoundedCornerShape(14.dp))
            .clickable { onClick(condition) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = condition.label,
            color = if (selected) Color(0xFF12100E) else Color(0xFFA89F8E),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
    )
}
