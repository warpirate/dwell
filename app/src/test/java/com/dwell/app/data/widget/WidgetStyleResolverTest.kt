package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleResolverTest {

    @Test
    fun `text color maps from token`() {
        assertEquals(0xFFECE7DD.toInt(), WidgetStyleResolver.textColorArgb(WidgetStyle(color = WidgetColor.CREAM)))
        assertEquals(0xFF6E9576.toInt(), WidgetStyleResolver.textColorArgb(WidgetStyle(color = WidgetColor.GREEN)))
    }

    @Test
    fun `matched wallpaper color overrides the token`() {
        val matched = 0xFFD9A38C.toInt()
        assertEquals(
            matched,
            WidgetStyleResolver.textColorArgb(WidgetStyle(color = WidgetColor.CREAM, matchedArgb = matched)),
        )
    }

    @Test
    fun `time text size grows with bucket`() {
        val small = WidgetStyleResolver.timeSizeSp(WidgetStyle(size = WidgetSize.SMALL))
        val large = WidgetStyleResolver.timeSizeSp(WidgetStyle(size = WidgetSize.LARGE))
        assert(large > small)
    }

    @Test
    fun `opacity maps to background alpha`() {
        assertEquals(0x00, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 0)) ushr 24)
        assertEquals(0xFF, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 100)) ushr 24)
        assertEquals(0x80, WidgetStyleResolver.backgroundArgb(WidgetStyle(opacity = 50)) ushr 24)
    }
}
