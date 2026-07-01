package com.dwell.app.ui.widgetconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dwell.app.R
import com.dwell.app.data.widget.WallpaperSample
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetPreset
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DisplayFontFamily
import com.dwell.app.ui.theme.DwellSpacing

private val Cream = Color(0xFFECE7DD)
private val Green = Color(0xFF6E9576)
private val Sand = Color(0xFFE8D9BC)
private val DateMuted = Color(0xFFA89F8E)

private fun textColorOf(c: WidgetColor): Color = when (c) {
    WidgetColor.CREAM -> Cream
    WidgetColor.GREEN -> Green
    WidgetColor.SAND -> Sand
}

/**
 * The widget picker. Free presets apply straight away; premium presets stay tappable so
 * they preview (the tease) but route to the paywall. The open style engine is premium —
 * premium users fine-tune inline, free users get a locked row.
 */
@Composable
fun WidgetConfigScreen(
    style: WidgetStyle,
    selected: WidgetPreset?,
    isPremium: Boolean,
    needsUnlock: Boolean,
    onSelectPreset: (WidgetPreset) -> Unit,
    onColor: (WidgetColor) -> Unit,
    onSize: (WidgetSize) -> Unit,
    onMatchWallpaper: (WallpaperSample) -> Unit,
    onOpenPaywall: () -> Unit,
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
                text = "Clock widget",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(DwellSpacing.lg))

            // Live preview — reflects the selected preset (or an engine edit).
            PreviewCard(style)
            Spacer(Modifier.height(DwellSpacing.xl))

            SectionLabel("Presets")
            Spacer(Modifier.height(DwellSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                WidgetPreset.free.forEach { p ->
                    PresetCell(p, selected == p, locked = false, Modifier.weight(1f), onSelectPreset)
                }
            }
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                WidgetPreset.premium.forEach { p ->
                    PresetCell(p, selected == p, locked = !isPremium, Modifier.weight(1f), onSelectPreset)
                }
            }

            Spacer(Modifier.height(DwellSpacing.xl))
            MatchSection(onMatchWallpaper)

            Spacer(Modifier.height(DwellSpacing.xl))
            if (isPremium) {
                Engine(style, onColor, onSize)
            } else {
                LockedEngineRow(onOpenPaywall)
            }

            Spacer(Modifier.height(DwellSpacing.section))
            if (needsUnlock) {
                DwellPrimaryButton(text = "Unlock everything", onClick = onOpenPaywall)
            } else {
                DwellPrimaryButton(text = "Add widget", onClick = onAdd)
            }
        }
    }
}

@Composable
private fun PresetCell(
    preset: WidgetPreset,
    selected: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: (WidgetPreset) -> Unit,
) {
    val timeSp = when (preset.style.size) {
        WidgetSize.SMALL -> 20.sp
        WidgetSize.MEDIUM -> 23.sp
        WidgetSize.LARGE -> 27.sp
    }
    Column(modifier = modifier.clickable { onClick(preset) }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2E2A23), Color(0xFF15110C))))
                .then(
                    if (selected) Modifier.border(1.5.dp, Cream, RoundedCornerShape(14.dp))
                    else Modifier.border(1.dp, Color(0x14ECE7DD), RoundedCornerShape(14.dp)),
                )
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = "9:41",
                    color = textColorOf(preset.style.color),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = timeSp,
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(width = 22.dp, height = 1.dp).background(Color(0x22ECE7DD)))
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(5.dp))
                    Text(text = "WED", color = DateMuted, fontSize = 7.sp, letterSpacing = 1.2.sp)
                }
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xA612100E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = "Premium",
                        modifier = Modifier.size(11.dp),
                        tint = Sand,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (preset.free) "Free" else "Premium",
                style = MaterialTheme.typography.labelSmall,
                color = if (preset.free) Green else Sand,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The premium style engine — colour + size controls, shown inline to members. */
@Composable
private fun Engine(style: WidgetStyle, onColor: (WidgetColor) -> Unit, onSize: (WidgetSize) -> Unit) {
    SectionLabel("Fine-tune")
    Spacer(Modifier.height(DwellSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.md)) {
        WidgetColor.entries.forEach { c ->
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(textColorOf(c))
                    .then(
                        if (c == style.color) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        else Modifier,
                    )
                    .clickable { onColor(c) },
            )
        }
    }
    Spacer(Modifier.height(DwellSpacing.lg))
    Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
        WidgetSize.entries.forEach { s ->
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (s == style.size) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                        else Modifier,
                    )
                    .clickable { onSize(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (s) {
                        WidgetSize.SMALL -> "S"; WidgetSize.MEDIUM -> "M"; WidgetSize.LARGE -> "L"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Free users see the engine as a locked row that routes to the paywall. */
@Composable
private fun LockedEngineRow(onOpenPaywall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpenPaywall)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF2A2620)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Sand,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Fine-tune it yourself",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Any colour, size & opacity — mix your own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = "›", color = DateMuted, fontSize = 22.sp)
    }
}

/** POC: prove the wallpaper-match pipeline — tap a wallpaper, the widget text recolours to match. */
@Composable
private fun MatchSection(onMatchWallpaper: (WallpaperSample) -> Unit) {
    SectionLabel("Match your wallpaper")
    Spacer(Modifier.height(DwellSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
        WallpaperChip("Dusk", listOf(Color(0xFFC98A6A), Color(0xFF8A5A6E), Color(0xFF3D3550)),
            Modifier.weight(1f)) { onMatchWallpaper(WallpaperSample.DUSK) }
        WallpaperChip("Forest", listOf(Color(0xFF6D8A6F), Color(0xFF3F5F4A), Color(0xFF20302A)),
            Modifier.weight(1f)) { onMatchWallpaper(WallpaperSample.FOREST) }
    }
}

@Composable
private fun WallpaperChip(label: String, colors: List<Color>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(colors))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = label.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun PreviewCard(style: WidgetStyle) {
    val timeColor = style.matchedArgb?.let { Color(it) } ?: textColorOf(style.color)
    val timeSp = when (style.size) {
        WidgetSize.SMALL -> 44.sp
        WidgetSize.MEDIUM -> 56.sp
        WidgetSize.LARGE -> 72.sp
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF322D26), Color(0xFF15110C))))
            .padding(22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = "9:41",
                color = timeColor,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = timeSp,
                letterSpacing = (-0.01).em,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(width = 46.dp, height = 1.dp).background(Color(0x26ECE7DD)))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                Spacer(Modifier.size(9.dp))
                Text(
                    text = "TUE, JUN 30",
                    color = DateMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
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
