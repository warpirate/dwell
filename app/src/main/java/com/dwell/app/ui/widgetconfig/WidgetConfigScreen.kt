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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.R
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.data.widget.WidgetStyle
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.components.DwellSecondaryButton
import com.dwell.app.ui.theme.DwellSpacing

private val Cream = Color(0xFFECE7DD)
private val Green = Color(0xFF6E9576)
private val Charcoal = Color(0xFF221F1A)
private val DateMuted = Color(0xFFA89F8E)

private fun textColorOf(c: WidgetColor): Color = when (c) {
    WidgetColor.CREAM -> Cream
    WidgetColor.GREEN -> Green
    WidgetColor.CHARCOAL -> Charcoal
}

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

            PreviewCard(style)
            Spacer(Modifier.height(DwellSpacing.xl))

            if (!isPremium) {
                LockedNotice()
                Spacer(Modifier.height(DwellSpacing.lg))
            }

            SectionLabel("Color")
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.md)) {
                WidgetColor.entries.forEach { c ->
                    ColorSwatch(
                        color = textColorOf(c),
                        selected = c == style.color,
                        enabled = isPremium,
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
                        enabled = isPremium,
                        onClick = { onSize(s) },
                    )
                }
            }

            Spacer(Modifier.height(DwellSpacing.section))
            if (isPremium) {
                DwellPrimaryButton(text = "Save", onClick = onSave)
            } else {
                DwellPrimaryButton(text = "Unlock Dwell", onClick = onUnlock)
                Spacer(Modifier.height(DwellSpacing.sm))
                DwellSecondaryButton(text = "Save with default", onClick = onSave)
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
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2620), Color(0xFF17140F))))
            .padding(22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = "9:41",
                color = timeColor,
                fontWeight = FontWeight.Light,
                fontSize = timeSp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
                Spacer(Modifier.size(9.dp))
                Text(
                    text = "TUE, JUN 30",
                    color = DateMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
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
private fun LockedNotice() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Customization is a premium feature. Unlock to make it yours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "Locked",
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF17140F),
            )
        }
    }
}

@Composable
private fun SizeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = "Locked",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
