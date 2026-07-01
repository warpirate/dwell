package com.dwell.app.widget.poster

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.roundToInt

/**
 * Time-of-day scene painter for the poster clock. Draws only the *scene* — sky
 * gradient, sun/moon glow, a bottom scrim for legibility, and a grain wash — onto a
 * Bitmap that the widget shows full-bleed in an ImageView. The time + date are live
 * TextClock views the layout stacks on top, so nothing here needs redrawing per minute.
 *
 * Palette + sun position track the real hour: dawn peach → noon blue → dusk amber →
 * night indigo. This is the whole idea — the widget lives with the day. Ported 1:1 from
 * design/widget-poster.html so what shipped matches the approved mock.
 */
object PosterRenderer {

    /** Resolved look for an hour: 3-stop sky, sun tint + glow strength, and the ink the
     *  overlaid text should use so it reads on that sky. */
    data class Palette(
        val sky: IntArray,
        val sun: Int,
        val glow: Float,
        val ink: Int,
        val phase: String,
    )

    private data class Key(
        val h: Float,
        val sky: IntArray,
        val sun: Int,
        val glow: Float,
        val ink: Int,
        val phase: String,
    )

    private fun c(hex: String) = Color.parseColor(hex)

    // Keyframes by hour (0..24). Interpolated between the two surrounding stops.
    private val KEYS = listOf(
        Key(0f,  intArrayOf(c("#0c1130"), c("#141a3e"), c("#0a0e22")), c("#c7d2f5"), .50f, c("#EAF0FF"), "NIGHT"),
        Key(5f,  intArrayOf(c("#20223f"), c("#4a3552"), c("#171225")), c("#e9b48a"), .55f, c("#F1ECE2"), "PRE-DAWN"),
        Key(7f,  intArrayOf(c("#f6b284"), c("#e77e63"), c("#5c3556")), c("#ffd9a0"), .90f, c("#FFF6EC"), "DAWN"),
        Key(9f,  intArrayOf(c("#c7e6f4"), c("#9cc7ea"), c("#5f83b8")), c("#fff0cf"), .85f, c("#12283c"), "MORNING"),
        Key(12f, intArrayOf(c("#dff1fb"), c("#b6dcf3"), c("#7fb0e0")), c("#fff7e6"), .80f, c("#12283c"), "NOON"),
        Key(16f, intArrayOf(c("#f2e0bf"), c("#e3c193"), c("#b07d5c")), c("#ffe6b8"), .85f, c("#2b1d12"), "AFTERNOON"),
        Key(18f, intArrayOf(c("#f6a15c"), c("#df5a3c"), c("#732a52")), c("#ffce93"), .95f, c("#FFF3E6"), "GOLDEN HOUR"),
        Key(20f, intArrayOf(c("#7c4a72"), c("#3d2c5c"), c("#14122c")), c("#f0bd84"), .70f, c("#F6ECDF"), "DUSK"),
        Key(22f, intArrayOf(c("#241a44"), c("#181436"), c("#0b0a1e")), c("#cfd6f0"), .55f, c("#EAF0FF"), "NIGHT"),
        Key(24f, intArrayOf(c("#0c1130"), c("#141a3e"), c("#0a0e22")), c("#c7d2f5"), .50f, c("#EAF0FF"), "NIGHT"),
    )

    private fun lerp(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).roundToInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).roundToInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).roundToInt(),
    )

    /** The interpolated palette for [hour] (0..24, fractional). */
    fun paletteFor(hour: Float): Palette {
        val hr = hour.coerceIn(0f, 24f)
        var i = 0
        while (i < KEYS.size - 2 && !(hr >= KEYS[i].h && hr <= KEYS[i + 1].h)) i++
        val a = KEYS[i]
        val b = KEYS[i + 1]
        val span = (b.h - a.h).let { if (it == 0f) 1f else it }
        val t = ((hr - a.h) / span).coerceIn(0f, 1f)
        return Palette(
            sky = intArrayOf(lerp(a.sky[0], b.sky[0], t), lerp(a.sky[1], b.sky[1], t), lerp(a.sky[2], b.sky[2], t)),
            sun = lerp(a.sun, b.sun, t),
            glow = a.glow + (b.glow - a.glow) * t,
            ink = lerp(a.ink, b.ink, t),
            phase = if (t < .5f) a.phase else b.phase,
        )
    }

    // Sun rides an arc: 6→18h across x .06→.94, parabolic height (high at noon). At night
    // the moon takes the same arc from 18h→06h.
    private fun sunPos(hour: Float): Pair<Float, Float> = if (hour in 6f..18f) {
        val x = .06f + (hour - 6f) / 12f * .88f
        val u = (hour - 12f) / 6f
        x to (.14f + u * u * .64f)
    } else {
        val mh = if (hour < 6f) hour + 24f else hour
        val x = .06f + (mh - 18f) / 12f * .88f
        val u = (mh - 24f) / 6f
        x to (.18f + u * u * .58f)
    }

    // One small deterministic noise tile, reused for the grain wash.
    private val noise: Bitmap by lazy {
        val n = 112
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        val px = IntArray(n * n) { val v = rnd.nextInt(256); Color.argb(255, v, v, v) }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        bmp
    }

    private fun withAlpha(color: Int, a: Int) =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Paint the scene (no text) into a [w]×[h] px bitmap for [hour] (0..24).
     *
     * RGB_565 (not ARGB_8888): the scene is fully opaque — the sky covers every pixel and
     * the host rounds the corners — so we need no alpha, and halving the bytes keeps the
     * bitmap well under the ~1MB Binder limit that RemoteViews.setImageViewBitmap ships it
     * across. The provider also caps the dimensions; centerCrop scales it up on screen.
     */
    fun render(w: Int, h: Int, hour: Float): Bitmap {
        val ww = w.coerceAtLeast(1)
        val hh = h.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(ww, hh, Bitmap.Config.RGB_565)
        val cv = Canvas(bmp)
        val p = paletteFor(hour)
        val wF = ww.toFloat()
        val hF = hh.toFloat()

        // Sky — 3-stop vertical gradient.
        val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, hF,
                intArrayOf(p.sky[0], p.sky[1], p.sky[2]),
                floatArrayOf(0f, .52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(0f, 0f, wF, hF, sky)

        // Sun / moon — soft radial glow at its arc position.
        val (sx, sy) = sunPos(hour)
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sx * wF, sy * hF, hF * .58f,
                intArrayOf(
                    withAlpha(p.sun, (p.glow * 255).toInt()),
                    withAlpha(p.sun, (p.glow * 90).toInt()),
                    withAlpha(p.sun, 0),
                ),
                floatArrayOf(0f, .35f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(0f, 0f, wF, hF, sun)

        // Bottom scrim — deepens toward the base so the overlaid time stays legible.
        val base = p.sky[2]
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, hF * .35f, 0f, hF,
                withAlpha(base, 0), withAlpha(base, 184),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(0f, hF * .35f, wF, hF, scrim)

        // Grain — a faint tiled noise wash for texture (depth without a shadow pass).
        val grain = Paint().apply {
            alpha = 16
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        cv.drawRect(0f, 0f, wF, hF, grain)

        return bmp
    }
}
