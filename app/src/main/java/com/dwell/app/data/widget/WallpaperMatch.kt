package com.dwell.app.data.widget

/**
 * The wallpaper-match moat, distilled to its one risky part: turning a colour sampled from
 * the user's wallpaper into a widget *text* colour that (a) still reads as that wallpaper's
 * hue and (b) stays legible on Dwell's warm dark widget card.
 *
 * Pure colour math — no Android graphics — so the legibility guarantee is unit-tested. The
 * AndroidX Palette extraction that feeds this is a thin, device-verified wrapper.
 *
 * On a dark surface, text must be *light*; we lift the seed's lightness (keeping its hue) until
 * it clears WCAG AA, and hold saturation modest so matched widgets stay editorial, not neon.
 */
object WallpaperMatch {

    /** The warm widget surface the text sits on (mirrors WidgetStyleResolver.SURFACE-ish gradient). */
    const val WIDGET_SURFACE = 0xFF1E1A15.toInt()

    private const val MIN_CONTRAST = 4.5      // WCAG AA for normal text
    private const val MAX_SATURATION = 0.55f  // keep it warm/calm, never neon
    private const val MIN_LIGHTNESS = 0.62f   // text on a dark card is clearly light

    /**
     * Coerce [seedArgb] (a wallpaper swatch) into a legible widget text colour on [surfaceArgb].
     * Preserves hue, tames saturation, and raises lightness until contrast >= [minContrast].
     */
    fun coerceTextColor(
        seedArgb: Int,
        surfaceArgb: Int = WIDGET_SURFACE,
        minContrast: Double = MIN_CONTRAST,
    ): Int {
        val (h, s0, _) = rgbToHsl(seedArgb)
        val s = s0.coerceAtMost(MAX_SATURATION)
        val surfaceL = relativeLuminance(surfaceArgb)

        // Walk lightness up from the floor; return the first level that clears contrast.
        var l = MIN_LIGHTNESS
        while (l <= 0.97f) {
            val candidate = hslToRgb(h, s, l)
            if (contrast(relativeLuminance(candidate), surfaceL) >= minContrast) return candidate
            l += 0.02f
        }
        // Fallback: hue-tinted near-white (always clears on a dark surface).
        return hslToRgb(h, (s * 0.5f), 0.97f)
    }

    /** Public for tests/telemetry: WCAG contrast ratio between two ARGB colours (order-free). */
    fun contrastBetween(a: Int, b: Int): Double = contrast(relativeLuminance(a), relativeLuminance(b))

    /** Public for tests: hue in degrees [0,360). */
    fun hueOf(argb: Int): Float = rgbToHsl(argb).first

    // ---- colour math (plain ints/floats, JVM-testable) --------------------------------------

    private fun contrast(l1: Double, l2: Double): Double {
        val hi = maxOf(l1, l2); val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun lin(c: Int): Double {
            val cs = c / 255.0
            return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /** Returns Triple(hueDegrees, saturation[0..1], lightness[0..1]). */
    private fun rgbToHsl(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l) // achromatic
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        var h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        h *= 60f
        return Triple(h, s, l)
    }

    private fun hslToRgb(hDeg: Float, s: Float, l: Float): Int {
        val h = ((hDeg % 360f) + 360f) % 360f / 360f
        if (s == 0f) {
            val v = (l * 255f).toInt().coerceIn(0, 255)
            return argb(v, v, v)
        }
        val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val r = hue2rgb(p, q, h + 1f / 3f)
        val g = hue2rgb(p, q, h)
        val b = hue2rgb(p, q, h - 1f / 3f)
        return argb((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
    }

    private fun hue2rgb(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
