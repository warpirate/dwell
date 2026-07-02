package com.dwell.app.data.widget

/** The browsable widget catalog behind the Widgets gallery. */

enum class WidgetCategory(val label: String) {
    TIME("Time"),
    WEATHER("Weather"),
    LIFE("Life"),
    PHOTOS("Photos"),
    FUN("Fun"),
}

/** Masonry footprint. WIDE cards span the full row; SQUARE cards take one column. */
enum class WidgetSpan { SQUARE, WIDE }

/** LIVE widgets open the configurator; SOON widgets are roadmap previews. */
enum class WidgetStatus { LIVE, SOON }

/**
 * One entry in the Widgets gallery. Clock is [WidgetStatus.LIVE] today; the rest are faithful
 * previews of what's shipping next (the P1 core suite + delight lane), so the tab reads as a full
 * catalog and fills in as each widget lands rather than staying a single button.
 */
enum class CatalogWidget(
    val label: String,
    val category: WidgetCategory,
    val span: WidgetSpan,
    val status: WidgetStatus,
) {
    CLOCK("Clock", WidgetCategory.TIME, WidgetSpan.WIDE, WidgetStatus.LIVE),
    WEATHER("Weather", WidgetCategory.WEATHER, WidgetSpan.SQUARE, WidgetStatus.SOON),
    DATE("Date", WidgetCategory.TIME, WidgetSpan.SQUARE, WidgetStatus.LIVE),
    BATTERY("Battery", WidgetCategory.LIFE, WidgetSpan.SQUARE, WidgetStatus.SOON),
    STEPS("Steps", WidgetCategory.LIFE, WidgetSpan.SQUARE, WidgetStatus.SOON),
    AGENDA("Agenda", WidgetCategory.LIFE, WidgetSpan.WIDE, WidgetStatus.SOON),
    PHOTO("Photo", WidgetCategory.PHOTOS, WidgetSpan.SQUARE, WidgetStatus.SOON),
    COUNTDOWN("Countdown", WidgetCategory.FUN, WidgetSpan.SQUARE, WidgetStatus.SOON),
    QUOTE("Quote", WidgetCategory.FUN, WidgetSpan.WIDE, WidgetStatus.SOON),
    MOON("Moon", WidgetCategory.FUN, WidgetSpan.SQUARE, WidgetStatus.SOON),
    WORLD_CLOCK("World clock", WidgetCategory.TIME, WidgetSpan.SQUARE, WidgetStatus.SOON),
    MUSIC("Now playing", WidgetCategory.FUN, WidgetSpan.WIDE, WidgetStatus.SOON),
    MONTH("Month", WidgetCategory.TIME, WidgetSpan.WIDE, WidgetStatus.SOON);

    companion object {
        /** All entries, or just those in [category] when one is selected. */
        fun inCategory(category: WidgetCategory?): List<CatalogWidget> =
            if (category == null) entries else entries.filter { it.category == category }
    }
}
