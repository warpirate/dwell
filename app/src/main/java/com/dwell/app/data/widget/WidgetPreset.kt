package com.dwell.app.data.widget

/**
 * A curated, finished widget look. The monetization line runs between *presets*
 * (complete looks; the free ones are genuinely usable) and the open *style engine*
 * (arbitrary color/size/opacity mixing, premium-only).
 *
 * Every preset here renders on the existing editorial `widget_clock.xml` — a preset is
 * just a named [WidgetStyle], so there is nothing new to render and no broken promise.
 * Free deliberately includes the brand green ([SAGE]); the identity color is never locked.
 */
enum class WidgetPreset(
    val id: String,
    val label: String,
    val style: WidgetStyle,
    val free: Boolean,
) {
    EDITORIAL("editorial", "Editorial", WidgetStyle(WidgetColor.CREAM, WidgetSize.MEDIUM), free = true),
    SAGE("sage", "Sage", WidgetStyle(WidgetColor.GREEN, WidgetSize.MEDIUM), free = true),
    BOLD("bold", "Bold", WidgetStyle(WidgetColor.CREAM, WidgetSize.LARGE), free = true),
    GOLD("gold", "Gold", WidgetStyle(WidgetColor.SAND, WidgetSize.MEDIUM), free = false),
    QUIET("quiet", "Quiet", WidgetStyle(WidgetColor.CREAM, WidgetSize.SMALL), free = false),
    FOREST("forest", "Forest", WidgetStyle(WidgetColor.GREEN, WidgetSize.LARGE), free = false);

    companion object {
        val Default = EDITORIAL

        val free: List<WidgetPreset> get() = entries.filter { it.free }
        val premium: List<WidgetPreset> get() = entries.filter { !it.free }

        /** The preset whose style matches [style] exactly, or null for a custom (engine) style. */
        fun of(style: WidgetStyle): WidgetPreset? = entries.firstOrNull { it.style == style }
    }
}
