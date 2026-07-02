package com.dwell.app.widget.poster

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Time-of-day *landscape* painter for the poster clock. The scene is a generated
 * poster whose palette, sun/moon and light all track the real hour: dawn peach →
 * noon blue → golden amber → night indigo with stars + a faint milky way.
 *
 * It paints only the scene (no text) onto a Bitmap the widget shows full-bleed in an
 * ImageView; the time + date ride on top as live TextClock views, so nothing here
 * redraws per minute. Ported 1:1 from design/widget-poster.html — that mock is a real
 * <canvas> built from the same primitives (LinearGradient/RadialGradient/Path/arc/
 * BitmapShader/xfermode) this maps onto, so what ships matches the approved art.
 *
 * Depth comes from layering, not blur: a horizon glow, haze bands, and two ridgeline
 * silhouettes with aerial perspective. The near ridge doubles as the text bed, which is
 * why the overlaid ink can stay a constant cream and always read.
 */
object PosterRenderer {

    /** Resolved look for an hour. [ink] is the cream the overlaid text uses (constant —
     *  the text always sits over the dark near ridge). */
    data class Palette(
        val sky: IntArray,
        val hz: Int,
        val sun: Int,
        val glow: Float,
        val hillFar: Int,
        val hillNear: Int,
        val ink: Int,
        val phase: String,
    )

    private data class Key(
        val h: Float,
        val sky: IntArray,
        val hz: Int,
        val sun: Int,
        val glow: Float,
        val hillFar: Int,
        val hillNear: Int,
        val phase: String,
    )

    internal fun c(hex: String) = Color.parseColor(hex)

    internal val CREAM = c("#F1ECE2")
    internal val WHITE = Color.rgb(255, 255, 255)
    internal val PALE = c("#EEF2FF")   // moon / star / disc core at night
    internal val MOON_MID = c("#DBE4F6")
    internal val MOON_EDGE = c("#C6D0EA")
    private val MW = c("#AEB9E6")     // milky way haze

    // Keyframes by hour (0..24). Sky is 5 vertical stops (top→base); interpolated
    // between the two surrounding keys. Values are the approved mock's palette table.
    private val KEYS = listOf(
        Key(0f,    intArrayOf(c("#04061a"), c("#080d29"), c("#0f1740"), c("#161f4e"), c("#1d2758")), c("#26305f"), c("#e7ecff"), .50f, c("#0d1330"), c("#05060f"), "NIGHT"),
        Key(5f,    intArrayOf(c("#0f1330"), c("#1e1c42"), c("#3a2a4e"), c("#5c3a54"), c("#814a52")), c("#b06a54"), c("#ffc194"), .55f, c("#28203e"), c("#0b0a18"), "PRE-DAWN"),
        Key(7f,    intArrayOf(c("#5c86c4"), c("#87a0cb"), c("#c6a7ba"), c("#f0a079"), c("#f6885f")), c("#ffcf95"), c("#fff2d6"), .95f, c("#8a86ab"), c("#241b30"), "DAWN"),
        Key(9.5f,  intArrayOf(c("#5aa6e4"), c("#8fc4ec"), c("#bfdcf1"), c("#e4eef0"), c("#f3f0e2")), c("#fbf4dc"), c("#fff8e6"), .80f, c("#8fb0cb"), c("#243444"), "MORNING"),
        Key(12f,   intArrayOf(c("#2f81cf"), c("#5ea3e0"), c("#8fc4ec"), c("#bfe0f4"), c("#e6f2fb")), c("#eff8fc"), c("#ffffff"), .68f, c("#6f99bf"), c("#20303f"), "NOON"),
        Key(15f,   intArrayOf(c("#4f8fce"), c("#84b1d8"), c("#bdc6c2"), c("#e6cfa0"), c("#f0d59e")), c("#f9e0ad"), c("#fff0cd"), .76f, c("#9d9184"), c("#2b2119"), "AFTERNOON"),
        Key(17.6f, intArrayOf(c("#42539c"), c("#7a5f9e"), c("#c07a70"), c("#ec7f4d"), c("#f66a34")), c("#ffb463"), c("#ffd89b"), 1.0f, c("#b0745e"), c("#2a1620"), "GOLDEN HOUR"),
        Key(19f,   intArrayOf(c("#232a63"), c("#46356f"), c("#75466e"), c("#a1525f"), c("#c15e50")), c("#df7350"), c("#ffc78f"), .75f, c("#4a3550"), c("#140f24"), "DUSK"),
        Key(21f,   intArrayOf(c("#11163f"), c("#1d1c4a"), c("#2c2456"), c("#3a2b5a"), c("#48345d")), c("#5c3d62"), c("#ecd6f2"), .60f, c("#241b40"), c("#0a0818"), "NIGHTFALL"),
        Key(24f,   intArrayOf(c("#04061a"), c("#080d29"), c("#0f1740"), c("#161f4e"), c("#1d2758")), c("#26305f"), c("#e7ecff"), .50f, c("#0d1330"), c("#05060f"), "NIGHT"),
    )

    internal fun lerp(a: Int, b: Int, t: Float): Int = Color.rgb(
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
            sky = IntArray(5) { lerp(a.sky[it], b.sky[it], t) },
            hz = lerp(a.hz, b.hz, t),
            sun = lerp(a.sun, b.sun, t),
            glow = a.glow + (b.glow - a.glow) * t,
            hillFar = lerp(a.hillFar, b.hillFar, t),
            hillNear = lerp(a.hillNear, b.hillNear, t),
            ink = CREAM,
            phase = if (t < .5f) a.phase else b.phase,
        )
    }

    // Sun rides an arc: 6→18h across x .18→.82, parabolic height (high at noon). At night
    // the moon takes the same arc from 18h→06h. The x range is kept off the edges so the
    // body survives the host's centerCrop and never scrolls out of the frame.
    internal fun sunPos(hour: Float): Pair<Float, Float> = if (hour in 6f..18f) {
        val x = .18f + (hour - 6f) / 12f * .64f
        val u = (hour - 12f) / 6f
        x to (.16f + u * u * .42f)
    } else {
        val mh = if (hour < 6f) hour + 24f else hour
        val x = .18f + (mh - 18f) / 12f * .64f
        val u = (mh - 24f) / 6f
        x to (.20f + u * u * .40f)
    }

    // One small deterministic noise tile, reused for the grain wash (BitmapShader REPEAT).
    private val noise: Bitmap by lazy {
        val n = 64
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(7)
        val px = IntArray(n * n) { val v = 175 + rnd.nextInt(80); Color.argb(255, v, v, v) }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        bmp
    }

    internal fun withAlpha(color: Int, a: Int) =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    internal fun a255(f: Float) = (f * 255f).toInt().coerceIn(0, 255)

    /** Luminance 0..1 of the top sky stop — gates how "night" the scene is (stars/milky way). */
    internal fun luma(color: Int) =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f

    /**
     * Paint the scene (no text) into a [w]×[h] px bitmap for [hour] (0..24).
     *
     * ARGB_8888, not RGB_565: the sky's smooth gradients band visibly at 565's 64/32/32
     * levels-per-channel (obvious comb-stripe artifacts on device, absent in the browser
     * mock's 24-bit canvas) — the quality loss was worse than the extra bytes cost. The
     * provider sizes the bitmap by a total pixel *byte budget*, not a fixed dimension cap,
     * so it stays under the ~1MB Binder limit RemoteViews.setImageViewBitmap ships it across
     * while accounting for the larger per-pixel cost. The scene itself is fully opaque — the
     * alpha channel goes unused on screen (the host rounds the corners) but costs little
     * next to eliminating the banding.
     */
    fun render(w: Int, h: Int, hour: Float, weather: PosterWeather = PosterWeather.Default): Bitmap {
        val ww = w.coerceAtLeast(1)
        val hh = h.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(ww, hh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = paletteFor(hour)
        val wF = ww.toFloat()
        val hF = hh.toFloat()

        val sp = sunPos(hour)
        val isDay = hour in 6f..18f
        val horizonY = hF * .66f
        val cx = sp.first * wF
        val cy = sp.second * hF
        val night = ((0.34f - luma(p.sky[0])) / 0.30f).coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val weatherSky = WeatherPainter.weatherSky(p, weather, night)
        val glowMul = WeatherPainter.glowMul(weather)
        val starMul = WeatherPainter.starMul(weather)
        val darken = WeatherPainter.darken(weather)
        val hgX = sp.first.coerceIn(.15f, .85f) * wF
        val sceneCtx = WeatherPainter.SceneCtx(wF, hF, p, cx, cy, sp.first, sp.second, isDay, night, horizonY, hgX)

        // 1. SKY — weather-tinted 5-stop vertical gradient.
        paint.shader = LinearGradient(
            0f, 0f, 0f, hF,
            weatherSky,
            floatArrayOf(0f, .28f, .5f, .72f, 1f),
            Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, wF, hF, paint)

        // 1b. DARKEN — rain/storm only.
        if (darken > 0f) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, hF,
                intArrayOf(withAlpha(c("#0b0e15"), a255(darken)), withAlpha(c("#0b0e15"), a255(darken * .75f)), withAlpha(c("#0b0e15"), a255(darken * .35f))),
                floatArrayOf(0f, .6f, 1f),
                Shader.TileMode.CLAMP,
            )
            cv.drawRect(0f, 0f, wF, hF, paint)
        }

        // 2. HORIZON GLOW — scaled by glowMul.
        paint.shader = RadialGradient(
            hgX, horizonY, wF * .95f,
            intArrayOf(withAlpha(p.hz, a255((.5f * p.glow + .22f) * glowMul)), withAlpha(p.hz, a255(.16f * glowMul)), withAlpha(p.hz, 0)),
            floatArrayOf(0f, .4f, 1f),
            Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, wF, horizonY + 2f, paint)

        // 3. NIGHT SKY — scaled by starMul.
        if (night * starMul > .05f) {
            milkyWay(cv, wF, hF, night * starMul)
            stars(cv, wF, horizonY, night * starMul)
        }

        // 4. SUN / MOON GLOW — scaled by glowMul.
        if (glowMul > .04f) {
            paint.shader = RadialGradient(
                cx, cy, hF * .98f,
                intArrayOf(withAlpha(p.sun, a255(.9f * p.glow * glowMul)), withAlpha(p.sun, a255(.42f * p.glow * glowMul)), withAlpha(p.sun, a255(.1f * p.glow * glowMul)), withAlpha(p.sun, 0)),
                floatArrayOf(0f, .16f, .5f, 1f),
                Shader.TileMode.CLAMP,
            )
            cv.drawRect(0f, 0f, wF, hF, paint)
        }

        // 5. GOD-RAYS — CLEAR and PARTLY only.
        if (p.glow > .82f && sp.second > .30f &&
            (weather.condition == WeatherCondition.CLEAR || weather.condition == WeatherCondition.PARTLY)
        ) {
            val strength = (p.glow - .82f) * 3.2f * (if (weather.condition == WeatherCondition.PARTLY) .6f else 1f)
            godRays(cv, hF, cx, cy, p.sun, strength)
        }

        // 6. FAR RIDGE — weather-tinted.
        val baseFar = lerp(p.hillFar, p.sky[4], .12f)
        val farColor = WeatherPainter.farTint(p, baseFar, weather, night)
        ridge(cv, wF, hF, horizonY, .62f, farColor, p, far = true)

        // 7. CONDITION LAYER — the per-weather sky content + light source.
        WeatherPainter.paintConditionLayer(cv, sceneCtx, weather, p.hillNear)

        // 8. NEAR RIDGE — weather-tinted; the text bed.
        val nearColor = WeatherPainter.nearTint(p, p.hillNear, weather, night)
        ridge(cv, wF, hF, horizonY + hF * .11f, .96f, nearColor, p, far = false)

        // 8b. GROUND TREATMENT — snow blanket / wet sheen / fog front.
        WeatherPainter.paintGroundTreatment(cv, sceneCtx, weather)

        // 9. PRECIP — static-baked rain/snow/lightning.
        WeatherPainter.paintPrecip(cv, sceneCtx, weather)

        // 10. BOTTOM SCRIM — scaled by scrimMul.
        val scrimMul = WeatherPainter.scrimMul(weather)
        paint.shader = LinearGradient(
            0f, hF * .42f, 0f, hF,
            withAlpha(nearColor, 0), withAlpha(nearColor, a255(.88f * scrimMul)),
            Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, hF * .42f, wF, hF, paint)

        // 11. GRAIN.
        grain(cv, wF, hF)

        return bmp
    }

    internal fun bodyPublic(cv: Canvas, cx: Float, cy: Float, hF: Float, isDay: Boolean, p: Palette) {
        val r = hF * (if (isDay) .12f else .095f)
        // Halo — soft ring of light around the body (brighter/tighter so it separates the
        // disc from a warm horizon).
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r * 2.3f,
                intArrayOf(withAlpha(p.sun, a255(.6f)), withAlpha(p.sun, a255(.12f)), withAlpha(p.sun, 0)),
                floatArrayOf(.3f, .6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawCircle(cx, cy, r * 2.3f, halo)
        // Disc — near-white solid core to a crisp edge, so it pops against any sky.
        val core = if (isDay) WHITE else PALE
        val mid = if (isDay) lerp(p.sun, WHITE, .4f) else MOON_MID
        val edge = if (isDay) lerp(p.sun, WHITE, .12f) else MOON_EDGE
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(core, mid, edge, withAlpha(if (isDay) p.sun else MOON_EDGE, 0)),
                floatArrayOf(0f, .75f, .96f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawCircle(cx, cy, r, disc)
        // Moon — carve a gibbous terminator with an offset shadow disc clipped to the body.
        if (!isDay) {
            cv.save()
            val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            cv.clipPath(clip)
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(lerp(p.sky[1], c("#000012"), .35f), a255(.5f))
            }
            cv.drawCircle(cx + r * .55f, cy - r * .32f, r * 1.05f, shadow)
            cv.restore()
        }
    }

    /** The near-ridge silhouette curve, shared with WeatherPainter's snow blanket clip. */
    internal fun nearRidgePath(wF: Float, hF: Float, topY: Float): Path {
        val amp = hF * .075f
        val seed = 3.3f
        val steps = 40
        return Path().apply {
            moveTo(0f, hF)
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = wF * t
                val y = topY + sin(t * 3.1f + seed) * amp * .6f + sin(t * 7f + seed * 2f) * amp * .4f
                lineTo(x, y)
            }
            lineTo(wF, hF)
            close()
        }
    }

    internal fun ridge(cv: Canvas, wF: Float, hF: Float, topY: Float, alpha: Float, color: Int, p: Palette, far: Boolean) {
        val amp = if (far) hF * .05f else hF * .075f
        val seed = if (far) 1.7f else 3.3f
        val steps = 40
        val path = if (!far) {
            nearRidgePath(wF, hF, topY)
        } else {
            Path().apply {
                moveTo(0f, hF)
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val x = wF * t
                    val y = topY + sin(t * 3.1f + seed) * amp * .6f + sin(t * 7f + seed * 2f) * amp * .4f
                    lineTo(x, y)
                }
                lineTo(wF, hF)
                close()
            }
        }
        val topColor = if (far) lerp(color, p.sky[4], .28f) else lerp(color, p.hz, .05f)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, topY - amp, 0f, hF, intArrayOf(topColor, color), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            this.alpha = a255(alpha)
        }
        cv.drawPath(path, pt)
    }

    internal fun haze(cv: Canvas, wF: Float, hF: Float, cx: Float, p: Palette, night: Float, mul: Float = 1f) {
        // y (of height), rw, rh (ellipse radii of w/h), base alpha, x offset from center.
        val bands = arrayOf(
            floatArrayOf(.30f, .9f, .055f, .16f, -.16f),
            floatArrayOf(.45f, 1.1f, .075f, .22f, .13f),
            floatArrayOf(.57f, .72f, .05f, .17f, .36f),
        )
        val under = lerp(p.hz, p.sun, .4f)
        val top = lerp(p.sky[1], WHITE, if (night > .4f) .05f else .14f)
        for (b in bands) {
            val bx = wF * (.5f + b[4])
            val by = hF * b[0]
            val a = b[3] * (1f - night * .55f) * mul
            if (a < .02f) continue
            val d = (kotlin.math.abs(bx - cx) / wF).coerceIn(0f, 1f)
            val lit = lerp(top, under, (1f - d) * .7f)
            cv.save()
            cv.translate(bx, by)
            cv.scale(wF * b[1], hF * b[2])
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    0f, 0f, 1f,
                    intArrayOf(withAlpha(lit, a255(a)), withAlpha(lit, a255(a * .5f)), withAlpha(lit, 0)),
                    floatArrayOf(0f, .7f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            cv.drawCircle(0f, 0f, 1f, pt)
            cv.restore()
        }
    }

    private fun godRays(cv: Canvas, hF: Float, cx: Float, cy: Float, color: Int, strength: Float) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
        val n = 7
        val spread = Math.PI.toFloat() * .95f
        val base = Math.PI.toFloat() * .5f
        val len = hF * 1.15f
        val wdt = .05f
        for (i in 0 until n) {
            val a = base - spread / 2f + spread * (i / (n - 1f)) + (if (i % 2 == 1) .03f else -.03f)
            val x1 = cx + cos(a - wdt) * len
            val y1 = cy + sin(a - wdt) * len
            val x2 = cx + cos(a + wdt) * len
            val y2 = cy + sin(a + wdt) * len
            pt.shader = LinearGradient(
                cx, cy, (x1 + x2) / 2f, (y1 + y2) / 2f,
                intArrayOf(withAlpha(color, a255(.09f * strength)), withAlpha(color, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            val path = Path().apply { moveTo(cx, cy); lineTo(x1, y1); lineTo(x2, y2); close() }
            cv.drawPath(path, pt)
        }
    }

    private fun stars(cv: Canvas, wF: Float, horizonY: Float, night: Float) {
        val rnd = Random(42)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(90) {
            val x = rnd.nextFloat() * wF
            val y = rnd.nextFloat() * horizonY * .95f
            val big = rnd.nextFloat() > .9f
            val r = (if (big) 1.5f else .8f) + rnd.nextFloat() * .4f
            val tw = .4f + rnd.nextFloat() * .6f
            val a = (if (big) .95f else tw) * night
            if (a < .03f) return@repeat
            pt.color = withAlpha(PALE, a255(a))
            cv.drawCircle(x, y, r, pt)
            if (big) {
                pt.color = withAlpha(PALE, a255(a * .22f))
                cv.drawCircle(x, y, r * 2.6f, pt)
            }
        }
    }

    private fun milkyWay(cv: Canvas, wF: Float, hF: Float, night: Float) {
        cv.save()
        cv.translate(wF * .62f, hF * .24f)
        cv.rotate(Math.toDegrees(-0.6).toFloat())
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, -hF * .42f, 0f, hF * .42f,
                intArrayOf(withAlpha(MW, 0), withAlpha(MW, a255(.1f * night)), withAlpha(MW, 0)),
                floatArrayOf(0f, .5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(-wF * .5f, -hF * .45f, wF * .5f, hF * .45f, pt)
        cv.restore()
    }

    private fun grain(cv: Canvas, wF: Float, hF: Float) {
        val pt = Paint().apply {
            alpha = 10
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        cv.drawRect(0f, 0f, wF, hF, pt)
    }
}
