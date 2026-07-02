package com.dwell.app.ui.screens.widgets

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.data.widget.CatalogWidget
import com.dwell.app.data.widget.WidgetStatus
import com.dwell.app.ui.theme.DisplayFontFamily

private val Cream = Color(0xFFECE7DD)
private val Green = Color(0xFF6E9576)
private val Sand = Color(0xFFE8D9BC)
private val Muted = Color(0xFFA89F8E)
private val Ink = Color(0xFF1B1712)
private val Hairline = Color(0x1AECE7DD)
private val CardHi = Color(0xFF322D26)
private val CardLo = Color(0xFF15110C)
private val cardGradient = listOf(CardHi, CardLo)

private const val SQUARE_MIN = 150

/**
 * One catalog card in the masonry. Clock (LIVE) is full-colour and tappable; the rest are
 * faithful roadmap previews, gently dimmed with a "Soon" pill so the tab reads as a full,
 * honest catalog that fills in as each widget ships.
 */
@Composable
fun WidgetPreviewCard(
    widget: CatalogWidget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = widget.status == WidgetStatus.LIVE
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(cardGradient))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .then(if (live) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (live) 1f else 0.82f),
    ) {
        WidgetPreviewContent(widget)
        Text(
            text = widget.label.uppercase(),
            color = Muted,
            fontSize = 9.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 13.dp),
        )
        if (!live) {
            Text(
                text = "SOON",
                color = Muted,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB312100E))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WidgetPreviewContent(widget: CatalogWidget) = when (widget) {
    CatalogWidget.CLOCK -> ClockPreview()
    CatalogWidget.WEATHER -> WeatherPreview()
    CatalogWidget.DATE -> DatePreview()
    CatalogWidget.BATTERY -> RingCardPreview(0.82f, Green, "82%", "PHONE")
    CatalogWidget.STEPS -> RingCardPreview(0.61f, Sand, "7,204", "STEPS TODAY", centerSp = 15)
    CatalogWidget.AGENDA -> AgendaPreview()
    CatalogWidget.PHOTO -> PhotoPreview()
    CatalogWidget.COUNTDOWN -> CountdownPreview()
    CatalogWidget.QUOTE -> QuotePreview()
    CatalogWidget.MOON -> MoonPreview()
    CatalogWidget.WORLD_CLOCK -> WorldClockPreview()
    CatalogWidget.MUSIC -> MusicPreview()
    CatalogWidget.MONTH -> MonthPreview()
}

// ---- shared bits -------------------------------------------------------------

@Composable
private fun Serif(text: String, sp: Int, color: Color, weight: FontWeight = FontWeight.Normal) =
    Text(text = text, color = color, fontFamily = DisplayFontFamily, fontWeight = weight, fontSize = sp.sp)

@Composable
private fun Hairrule(width: Int = 46) =
    Box(Modifier.padding(vertical = 11.dp).size(width = width.dp, height = 1.dp).background(Color(0x28ECE7DD)))

@Composable
private fun GreenDotDate(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
    Spacer(Modifier.width(9.dp))
    Text(text, color = Muted, fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Medium)
}

/** Body column for a square card: the tag lives above, so start content below it. */
@Composable
private fun SquareBody(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().heightIn(min = SQUARE_MIN.dp).padding(16.dp).padding(top = 16.dp),
    horizontalAlignment = horizontalAlignment,
    content = content,
)

// ---- previews ----------------------------------------------------------------

@Composable
private fun ClockPreview() = Column(Modifier.fillMaxWidth().padding(18.dp).padding(top = 12.dp)) {
    Serif("9:41", 52, Cream)
    Hairrule()
    GreenDotDate("TUE, JUN 30")
}

@Composable
private fun WeatherPreview() = Box(Modifier.fillMaxWidth().heightIn(min = SQUARE_MIN.dp)) {
    // Centre-right so it never collides with the top-corner tag / "Soon" pill.
    Box(
        Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(30.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Sand, Color(0xFFC9A15E)))),
    )
    Column(Modifier.padding(16.dp).padding(top = 30.dp)) {
        Serif("24°", 44, Cream)
        Spacer(Modifier.height(4.dp))
        Text("Bengaluru", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text("H:27°  L:19°", color = Sand, fontSize = 11.sp)
    }
}

@Composable
private fun DatePreview() = SquareBody {
    Serif("30", 60, Cream)
    Text("TUESDAY", color = Green, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(3.dp))
    Text("JUNE 2026", color = Muted, fontSize = 12.sp, letterSpacing = 1.6.sp)
}

@Composable
private fun RingCardPreview(fraction: Float, color: Color, center: String, caption: String, centerSp: Int = 19) =
    SquareBody(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = Color(0x1FECE7DD), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Serif(center, centerSp, Cream)
        }
        Spacer(Modifier.height(12.dp))
        Text(caption, color = Muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
    }

@Composable
private fun AgendaPreview() = Column(Modifier.fillMaxWidth().padding(16.dp).padding(top = 18.dp)) {
    AgendaRow("09:30", Green, "Standup")
    AgendaRow("11:00", Sand, "Design review")
    AgendaRow("18:30", Color(0xFF8A5A6E), "Gym")
}

@Composable
private fun AgendaRow(time: String, dot: Color, title: String) =
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 7.dp)) {
        Text(time, color = Muted, fontSize = 12.sp, modifier = Modifier.width(46.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(11.dp))
        Text(title, color = Cream, fontSize = 14.sp)
    }

@Composable
private fun PhotoPreview() = Box(
    // Explicit height: a staggered-grid item is unbounded vertically, so fillMaxSize would
    // collapse to zero and the image would never draw.
    Modifier.fillMaxWidth().height(SQUARE_MIN.dp)
        .background(Brush.linearGradient(listOf(Color(0xFF9DBE97), Color(0xFF6C8E6F), Color(0xFF3E5744)))),
) {
    Box(Modifier.fillMaxSize().padding(10.dp).border(1.dp, Color(0x80ECE7DD), RoundedCornerShape(12.dp)))
}

@Composable
private fun CountdownPreview() = SquareBody {
    Spacer(Modifier.height(4.dp))
    Serif("12", 52, Sand)
    Text("days until", color = Muted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text("Goa trip", color = Cream, fontSize = 14.sp)
}

@Composable
private fun QuotePreview() = Column(Modifier.fillMaxWidth().padding(18.dp).padding(top = 18.dp)) {
    Text(
        text = "“Almost everything will work again if you unplug it for a few minutes.”",
        color = Cream,
        fontFamily = DisplayFontFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text("— ANNE LAMOTT", color = Muted, fontSize = 11.sp, letterSpacing = 0.7.sp)
}

@Composable
private fun MoonPreview() = SquareBody(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(
            Brush.horizontalGradient(
                0.0f to Color(0xFF2A2620), 0.44f to Color(0xFF2A2620),
                0.44f to Sand, 1.0f to Sand,
            ),
        ),
    )
    Spacer(Modifier.height(14.dp))
    Text("WAXING GIBBOUS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
}

@Composable
private fun WorldClockPreview() = SquareBody {
    Spacer(Modifier.height(6.dp))
    WorldRow("9:41", "SF")
    Hairrule(width = 999)
    WorldRow("22:11", "TOKYO")
}

@Composable
private fun WorldRow(time: String, zone: String) = Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Serif(time, 24, Cream)
    Text(zone, color = Muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
}

@Composable
private fun MusicPreview() = Row(
    modifier = Modifier.fillMaxWidth().padding(16.dp).padding(top = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(Color(0xFF8A5A6E), Color(0xFF3D3550)))))
    Spacer(Modifier.width(13.dp))
    Column(Modifier.weight(1f)) {
        Text("Weightless", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("Marconi Union", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x28ECE7DD))) {
            Box(Modifier.fillMaxWidth(0.38f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Green))
        }
    }
    Spacer(Modifier.width(12.dp))
    // play glyph
    Canvas(Modifier.size(16.dp)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close()
        }
        drawPath(p, Cream)
    }
}

@Composable
private fun MonthPreview() = Column(Modifier.fillMaxWidth().padding(16.dp).padding(top = 18.dp)) {
    val heads = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        heads.forEach { Text(it, color = Sand, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    Spacer(Modifier.height(6.dp))
    var day = 1
    val weeks = 5
    repeat(weeks) { w ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(7) {
                val d = day
                if (d in 1..30) {
                    MonthCell(d.toString(), highlighted = d == 30)
                } else {
                    Box(Modifier.weight(1f))
                }
                day++
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MonthCell(label: String, highlighted: Boolean) = Box(
    modifier = Modifier
        .weight(1f)
        .padding(2.dp)
        .height(20.dp)
        .clip(RoundedCornerShape(5.dp))
        .background(if (highlighted) Color(0xFF3A5A40) else Color(0x0FECE7DD)),
    contentAlignment = Alignment.Center,
) {
    Text(label, color = if (highlighted) Color(0xFFF3EEE4) else Muted, fontSize = 9.sp)
}
