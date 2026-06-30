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
import com.dwell.app.data.widget.WidgetColor
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

/** The free defaults — everything else is part of the premium style engine. */
private fun WidgetColor.isFree() = this == WidgetColor.CREAM
private fun WidgetSize.isFree() = this == WidgetSize.MEDIUM

/** Does the currently-chosen style need the unlock (a premium option, and not yet bought)? */
private fun needsUnlock(style: WidgetStyle, isPremium: Boolean): Boolean =
    !isPremium && (!style.color.isFree() || !style.size.isFree())

@Composable
fun WidgetConfigScreen(
    style: WidgetStyle,
    isPremium: Boolean,
    onColor: (WidgetColor) -> Unit,
    onSize: (WidgetSize) -> Unit,
    onUnlock: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locked = needsUnlock(style, isPremium)
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

            // Live preview — updates as you tap any option (the tease).
            PreviewCard(style)
            Spacer(Modifier.height(DwellSpacing.xl))

            SectionLabel("Color")
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.md)) {
                WidgetColor.entries.forEach { c ->
                    ColorSwatch(
                        color = textColorOf(c),
                        selected = c == style.color,
                        locked = !isPremium && !c.isFree(),
                        onClick = { onColor(c) },
                    )
                }
            }

            Spacer(Modifier.height(DwellSpacing.lg))
            SectionLabel("Size")
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                WidgetSize.entries.forEach { s ->
                    SizeChip(
                        label = when (s) {
                            WidgetSize.SMALL -> "S"
                            WidgetSize.MEDIUM -> "M"
                            WidgetSize.LARGE -> "L"
                        },
                        selected = s == style.size,
                        locked = !isPremium && !s.isFree(),
                        onClick = { onSize(s) },
                    )
                }
            }

            Spacer(Modifier.height(DwellSpacing.section))
            if (locked) {
                Text(
                    text = "This style is part of the premium unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DwellSpacing.sm))
                DwellPrimaryButton(text = "Unlock Dwell", onClick = onUnlock)
            } else {
                DwellPrimaryButton(text = "Add widget", onClick = onSave)
            }
        }
    }
}

@Composable
private fun PreviewCard(style: WidgetStyle) {
    val timeColor = textColorOf(style.color)
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

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "Premium",
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF17140F),
            )
        }
    }
}

@Composable
private fun SizeChip(label: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (locked) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "Premium",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
