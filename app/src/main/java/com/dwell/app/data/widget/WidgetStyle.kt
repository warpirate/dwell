package com.dwell.app.data.widget

/** Text color options for a widget. Keys map to locked design tokens in WidgetStyleResolver. */
enum class WidgetColor { CREAM, GREEN, SAND }

/** Text size buckets. */
enum class WidgetSize { SMALL, MEDIUM, LARGE }

/**
 * A widget instance's visual style. v1 engine: color, size, background opacity.
 * Font and corner radius arrive with the Glance widgets.
 */
data class WidgetStyle(
    val color: WidgetColor = WidgetColor.CREAM,
    val size: WidgetSize = WidgetSize.MEDIUM,
    val opacity: Int = 100, // 0..100, background alpha
    /**
     * A wallpaper-matched text colour (already legibility-coerced by [WallpaperMatch]). When
     * set, it overrides [color]. Null for the curated presets and the free defaults.
     */
    val matchedArgb: Int? = null,
) {
    fun coerced(): WidgetStyle = copy(opacity = opacity.coerceIn(0, 100))

    /** Pipe-delimited, no extra serialization dependency. Matched colour is an optional 4th field. */
    fun encode(): String = buildString {
        append(color.name); append('|'); append(size.name); append('|'); append(opacity)
        if (matchedArgb != null) { append('|'); append(matchedArgb) }
    }

    companion object {
        val Default = WidgetStyle()

        fun decode(raw: String): WidgetStyle = runCatching {
            val p = raw.split("|").also { require(it.size == 3 || it.size == 4) }
            WidgetStyle(
                color = WidgetColor.valueOf(p[0]),
                size = WidgetSize.valueOf(p[1]),
                opacity = p[2].toInt(),
                matchedArgb = p.getOrNull(3)?.toInt(),
            ).coerced()
        }.getOrDefault(Default)
    }
}
