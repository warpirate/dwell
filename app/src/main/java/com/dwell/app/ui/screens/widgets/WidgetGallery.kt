package com.dwell.app.ui.screens.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.data.widget.CatalogWidget
import com.dwell.app.data.widget.WidgetCategory
import com.dwell.app.data.widget.WidgetSpan
import com.dwell.app.ui.theme.DwellSpacing

/**
 * The Widgets tab: a browsable masonry of the whole catalog. Live widgets are tappable (open
 * config or pin directly); the rest are faithful roadmap previews that light up as they ship.
 */
@Composable
fun WidgetGallery(onSelect: (CatalogWidget) -> Unit, modifier: Modifier = Modifier) {
    var category by remember { mutableStateOf<WidgetCategory?>(null) }
    val widgets = CatalogWidget.inCategory(category)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DwellSpacing.screenGutter,
            end = DwellSpacing.screenGutter,
            top = DwellSpacing.md,
            bottom = DwellSpacing.section,
        ),
        horizontalArrangement = Arrangement.spacedBy(DwellSpacing.md),
        verticalItemSpacing = DwellSpacing.md,
    ) {
        item(span = StaggeredGridItemSpan.FullLine, key = "header") { Header() }
        item(span = StaggeredGridItemSpan.FullLine, key = "chips") {
            CategoryChips(selected = category, onSelect = { category = it })
        }
        widgets.forEach { w ->
            item(
                key = w.name,
                span = if (w.span == WidgetSpan.WIDE) StaggeredGridItemSpan.FullLine
                else StaggeredGridItemSpan.SingleLane,
            ) {
                WidgetPreviewCard(widget = w, onClick = { onSelect(w) })
            }
        }
    }
}

@Composable
private fun Header() = Column {
    Text(
        text = "Widgets",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = "Live looks for your home screen.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(DwellSpacing.lg))
}

@Composable
private fun CategoryChips(selected: WidgetCategory?, onSelect: (WidgetCategory?) -> Unit) = Row(
    modifier = Modifier
        .horizontalScroll(rememberScrollState())
        .padding(bottom = DwellSpacing.sm),
    horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm),
) {
    Chip(label = "All", on = selected == null) { onSelect(null) }
    WidgetCategory.entries.forEach { c ->
        Chip(label = c.label, on = selected == c) { onSelect(c) }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val green = Color(0xFF6E9576)
    Text(
        text = label,
        color = if (on) green else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.5.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                1.dp,
                if (on) green.copy(alpha = 0.5f) else Color(0x1AECE7DD),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}
