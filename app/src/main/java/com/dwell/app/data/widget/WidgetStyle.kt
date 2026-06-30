package com.dwell.app.data.widget

/** Text color options for a widget. Keys map to locked design tokens in WidgetStyleResolver. */
enum class WidgetColor { CREAM, GREEN, CHARCOAL }

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
) {
    fun coerced(): WidgetStyle = copy(opacity = opacity.coerceIn(0, 100))

    /** Pipe-delimited, no extra serialization dependency. */
    fun encode(): String = "${color.name}|${size.name}|$opacity"

    companion object {
        val Default = WidgetStyle()

        fun decode(raw: String): WidgetStyle = runCatching {
            val (c, s, o) = raw.split("|").also { require(it.size == 3) }
            WidgetStyle(
                color = WidgetColor.valueOf(c),
                size = WidgetSize.valueOf(s),
                opacity = o.toInt(),
            ).coerced()
        }.getOrDefault(Default)
    }
}
