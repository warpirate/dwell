package com.dwell.app.ui.paywall

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.data.widget.WidgetColor
import com.dwell.app.data.widget.WidgetPreset
import com.dwell.app.data.widget.WidgetSize
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.DisplayFontFamily
import com.dwell.app.ui.theme.DwellSpacing

private val Cream = Color(0xFFECE7DD)
private val Green = Color(0xFF6E9576)
private val Sand = Color(0xFFE8D9BC)
private val Muted = Color(0xFFA89F8E)
private val Faint = Color(0xFF6F675A)

private fun tint(c: WidgetColor): Color = when (c) {
    WidgetColor.CREAM -> Cream
    WidgetColor.GREEN -> Green
    WidgetColor.SAND -> Sand
}

private fun miniSp(s: WidgetSize) = when (s) {
    WidgetSize.SMALL -> 24.sp
    WidgetSize.MEDIUM -> 28.sp
    WidgetSize.LARGE -> 32.sp
}

/**
 * The honest P0 pitch: sells what the unlock actually delivers today — the open style
 * engine, every preset, cross-device, one-time. Wallpaper-match and new layouts appear
 * as a clearly-marked roadmap line, never as a delivered checkbox.
 */
@Composable
fun PaywallScreen(
    priceLabel: String,
    loading: Boolean,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DwellScaffold(modifier = modifier, applyStatusBarPadding = true, applyNavBarPadding = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DwellSpacing.screenGutter, vertical = DwellSpacing.lg),
        ) {
            // Top row — brand + dismiss.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dwell",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "✕",
                    color = Muted,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(6.dp),
                )
            }

            Spacer(Modifier.height(DwellSpacing.xl))
            Text(
                text = "ONE UNLOCK",
                style = MaterialTheme.typography.labelMedium,
                color = Green,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(DwellSpacing.sm))
            Text(
                text = "Make it truly\nyours.",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.01).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(DwellSpacing.md))
            Text(
                text = "Unlock the full style engine and every preset — mix any colour, " +
                    "size and opacity until each widget is exactly right.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )

            // Tangible: the premium presets.
            Spacer(Modifier.height(DwellSpacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                WidgetPreset.premium.forEach { p ->
                    MiniClock(preset = p, modifier = Modifier.weight(1f))
                }
            }

            // What the unlock delivers today.
            Spacer(Modifier.height(DwellSpacing.xl))
            FeatureRow("The full style engine", "Any colour, size and opacity — mix your own.")
            FeatureRow("Every preset", "Gold, Quiet, Forest — the whole curated set.")
            FeatureRow("Yours across devices", "Sign in anywhere and the unlock follows.")
            FeatureRow("One-time purchase", "No subscription, ever.")

            // Honest roadmap — distinct from the delivered checklist above.
            Spacer(Modifier.height(DwellSpacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14ECE7DD))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Coming to members — wallpaper-matched widgets and new layouts, " +
                        "free when they land.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }

            Spacer(Modifier.height(DwellSpacing.section))
            DwellPrimaryButton(
                text = "Unlock Dwell — $priceLabel, once",
                onClick = onUnlock,
                loading = loading,
            )
            Spacer(Modifier.height(DwellSpacing.xs))
            Text(
                text = "Maybe later",
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun MiniClock(preset: WidgetPreset, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2E2A23), Color(0xFF15110C))))
                .padding(13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = "9:41",
                    color = tint(preset.style.color),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = miniSp(preset.style.size),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "WED",
                        color = Muted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String) {
    Row(modifier = Modifier.padding(vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0x296E9576)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✓", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}
