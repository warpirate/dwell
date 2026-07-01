package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleTest {

    @Test
    fun `default style has expected values`() {
        val d = WidgetStyle.Default
        assertEquals(WidgetColor.CREAM, d.color)
        assertEquals(WidgetSize.MEDIUM, d.size)
        assertEquals(100, d.opacity)
    }

    @Test
    fun `opacity is clamped to 0 to 100`() {
        assertEquals(0, WidgetStyle.Default.copy(opacity = -10).coerced().opacity)
        assertEquals(100, WidgetStyle.Default.copy(opacity = 250).coerced().opacity)
    }

    @Test
    fun `encode then decode round-trips`() {
        val style = WidgetStyle(WidgetColor.GREEN, WidgetSize.LARGE, 60)
        assertEquals(style, WidgetStyle.decode(style.encode()))
    }

    @Test
    fun `decode of garbage returns default`() {
        assertEquals(WidgetStyle.Default, WidgetStyle.decode("not-a-style"))
        assertEquals(WidgetStyle.Default, WidgetStyle.decode(""))
    }

    @Test
    fun `matched color round-trips as an optional fourth field`() {
        val style = WidgetStyle(WidgetColor.CREAM, WidgetSize.MEDIUM, 100, matchedArgb = 0xFFD9A38C.toInt())
        assertEquals(style, WidgetStyle.decode(style.encode()))
    }

    @Test
    fun `legacy three-field encoding still decodes with no matched color`() {
        val decoded = WidgetStyle.decode("GREEN|LARGE|80")
        assertEquals(WidgetStyle(WidgetColor.GREEN, WidgetSize.LARGE, 80, matchedArgb = null), decoded)
    }
}
