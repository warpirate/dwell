package com.dwell.app.data.widget

/** Pure mapping of [WidgetStyle] to RemoteViews-ready values. Mirrors ui/theme/Color.kt tokens. */
object WidgetStyleResolver {

    private const val CREAM = 0xFFECE7DD.toInt()
    private const val GREEN = 0xFF6E9576.toInt()    // AccentDark (AA over warm surfaces)
    private const val SAND = 0xFFE8D9BC.toInt()     // warm gold — premium on-dark alt
    private const val SURFACE = 0x221F1A            // warm surface, alpha applied from opacity

    fun textColorArgb(style: WidgetStyle): Int = style.matchedArgb ?: when (style.color) {
        WidgetColor.CREAM -> CREAM
        WidgetColor.GREEN -> GREEN
        WidgetColor.SAND -> SAND
    }

    fun timeSizeSp(style: WidgetStyle): Float = when (style.size) {
        WidgetSize.SMALL -> 44f
        WidgetSize.MEDIUM -> 56f
        WidgetSize.LARGE -> 72f
    }

    /** The date is a tracked-out caption, kept small and constant regardless of time size. */
    fun dateSizeSp(style: WidgetStyle): Float = 12f

    fun backgroundArgb(style: WidgetStyle): Int {
        val alpha = (style.opacity.coerceIn(0, 100) * 255 + 50) / 100 // rounded
        return (alpha shl 24) or SURFACE
    }
}
