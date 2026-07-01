package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresetTest {

    @Test
    fun `three free and three premium presets`() {
        assertEquals(3, WidgetPreset.free.size)
        assertEquals(3, WidgetPreset.premium.size)
    }

    @Test
    fun `the brand green is available for free`() {
        // Sage (green) must be a free preset — the identity color is never locked.
        assertTrue(WidgetPreset.free.any { it.style.color == WidgetColor.GREEN })
    }

    @Test
    fun `of maps every preset's style back to itself`() {
        WidgetPreset.entries.forEach { p ->
            assertEquals(p, WidgetPreset.of(p.style))
        }
    }

    @Test
    fun `of returns null for a custom engine style`() {
        // A cream/medium at 50% opacity is not any curated preset.
        assertNull(WidgetPreset.of(WidgetStyle(WidgetColor.CREAM, WidgetSize.MEDIUM, opacity = 50)))
    }

    @Test
    fun `default preset is editorial`() {
        assertEquals(WidgetPreset.EDITORIAL, WidgetPreset.Default)
    }
}
