package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperMatchTest {

    private val surface = WallpaperMatch.WIDGET_SURFACE

    private fun hueDelta(a: Float, b: Float): Float {
        val d = Math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    private fun assertLegible(seed: Int) {
        val c = WallpaperMatch.coerceTextColor(seed)
        val ratio = WallpaperMatch.contrastBetween(c, surface)
        assertTrue("contrast $ratio must clear WCAG AA 4.5 for seed ${Integer.toHexString(seed)}", ratio >= 4.5)
    }

    @Test
    fun `every wallpaper seed coerces to a legible widget text color`() {
        listOf(
            0xFF14595E.toInt(), // deep teal
            0xFFD2691E.toInt(), // burnt orange
            0xFF3A5A40.toInt(), // Dwell deep green
            0xFF6A4C93.toInt(), // dusk purple
            0xFF101010.toInt(), // near-black wallpaper
            0xFFECE7DD.toInt(), // already-light cream
            0xFF8B0000.toInt(), // dark red
        ).forEach { assertLegible(it) }
    }

    @Test
    fun `coercion preserves the wallpaper hue for chromatic seeds`() {
        // Teal in -> teal-ish text out (hue kept; only lightness/saturation move for legibility).
        val teal = 0xFF14595E.toInt()
        val out = WallpaperMatch.coerceTextColor(teal)
        assertTrue(
            "hue drift ${hueDelta(WallpaperMatch.hueOf(out), WallpaperMatch.hueOf(teal))} too large",
            hueDelta(WallpaperMatch.hueOf(out), WallpaperMatch.hueOf(teal)) < 12f,
        )

        val orange = 0xFFD2691E.toInt()
        val outO = WallpaperMatch.coerceTextColor(orange)
        assertTrue(hueDelta(WallpaperMatch.hueOf(outO), WallpaperMatch.hueOf(orange)) < 12f)
    }

    @Test
    fun `a near-black wallpaper still yields light readable text`() {
        val out = WallpaperMatch.coerceTextColor(0xFF0A0A0A.toInt())
        // Must be lifted well off black.
        val lum = WallpaperMatch.contrastBetween(out, 0xFF000000.toInt())
        assertTrue("near-black seed should lift to light text", lum > 4.5)
    }

    @Test
    fun `alpha channel is always opaque`() {
        val out = WallpaperMatch.coerceTextColor(0xFF14595E.toInt())
        assertEquals(0xFF, (out ushr 24) and 0xFF)
    }
}
