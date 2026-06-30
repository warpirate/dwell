package com.dwell.app.data.widget

/** Pure mapping of [WidgetStyle] to RemoteViews-ready values. Mirrors ui/theme/Color.kt tokens. */
object WidgetStyleResolver {

    private const val CREAM = 0xFFECE7DD.toInt()
    private const val GREEN = 0xFF6E9576.toInt()    // AccentDark (AA over warm surfaces)
    private const val CHARCOAL = 0xFF221F1A.toInt()
    private const val SURFACE = 0x221F1A            // warm surface, alpha applied from opacity

    fun textColorArgb(style: WidgetStyle): Int = when (style.color) {
        WidgetColor.CREAM -> CREAM
        WidgetColor.GREEN -> GREEN
        WidgetColor.CHARCOAL -> CHARCOAL
    }

    fun timeSizeSp(style: WidgetStyle): Float = when (style.size) {
        WidgetSize.SMALL -> 28f
        WidgetSize.MEDIUM -> 40f
        WidgetSize.LARGE -> 56f
    }

    fun dateSizeSp(style: WidgetStyle): Float = timeSizeSp(style) * 0.4f

    fun backgroundArgb(style: WidgetStyle): Int {
        val alpha = (style.opacity.coerceIn(0, 100) * 255 + 50) / 100 // rounded
        return (alpha shl 24) or SURFACE
    }
}
