package com.dwell.app.widget.poster

import android.graphics.Canvas
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
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The weather compositors that sit on top of [PosterRenderer]'s time-of-day scene. Ported
 * 1:1 from design/widget-weather-fable.html — each condition owns a bespoke look so no two
 * are ever confusable, and weather always composites over the hour's palette rather than
 * replacing it.
 *
 * Static bake only: RemoteViews can't animate, so precipitation/lightning are painted at a
 * single fixed phase — one representative frame.
 */
internal object WeatherPainter {

    /** Per-render values every compositor needs; computed once in PosterRenderer.render. */
    data class SceneCtx(
        val wF: Float,
        val hF: Float,
        val p: PosterRenderer.Palette,
        val cx: Float,
        val cy: Float,
        val spX: Float,
        val spY: Float,
        val isDay: Boolean,
        val night: Float,
        val horizonY: Float,
        val hgX: Float,
    )

    private const val STATIC_PHASE = 0.35f

    private fun withAlphaF(color: Int, a: Float) = PosterRenderer.withAlpha(color, PosterRenderer.a255(a))

    private fun linearGradientOf(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, stops: FloatArray) =
        LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP)

    /** A soft elliptical radial-gradient patch — the recurring cloud/fog/light unit. */
    private fun blob(cv: Canvas, x: Float, y: Float, rw: Float, rh: Float, color: Int, a: Float, additive: Boolean = false) {
        cv.save()
        cv.translate(x, y)
        cv.scale(rw, rh)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (additive) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(withAlphaF(color, a), withAlphaF(color, a * .5f), withAlphaF(color, 0f)),
                floatArrayOf(0f, .7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawCircle(0f, 0f, 1f, paint)
        cv.restore()
    }

    // ================= PARTLY =================

    private data class CloudSpot(val x: Float, val y: Float, val s: Float)
    private val PARTLY_SPOTS = listOf(
        CloudSpot(.20f, .16f, 1.0f),
        CloudSpot(.60f, .09f, .78f),
        CloudSpot(.86f, .25f, .58f),
        CloudSpot(.40f, .30f, .46f),
        CloudSpot(.05f, .31f, .42f),
    )

    private fun paintPartly(cv: Canvas, ctx: SceneCtx, k: Float) {
        val n = (2 + (k * 3f).roundToInt()).coerceAtMost(PARTLY_SPOTS.size)
        for (i in 0 until n) {
            val spot = PARTLY_SPOTS[i]
            val sunDir = if (ctx.spX > spot.x) 1f else -1f
            cumulus(
                cv, spot.x * ctx.wF, spot.y * ctx.hF,
                ctx.wF * .185f * spot.s, ctx.hF * .07f * spot.s,
                ctx.p, ctx.night, sunDir,
            )
        }
    }

    private val CUMULUS_LOBES = listOf(
        floatArrayOf(-.72f, -.12f, .6f),
        floatArrayOf(-.2f, -.52f, .76f),
        floatArrayOf(.32f, -.44f, .68f),
        floatArrayOf(.78f, -.06f, .56f),
    )

    private fun cumulus(cv: Canvas, x: Float, y: Float, rw: Float, rh: Float, p: PosterRenderer.Palette, night: Float, sunDir: Float) {
        val isNight = night > .4f
        val lit = if (isNight) {
            PosterRenderer.lerp(PosterRenderer.lerp(PosterRenderer.c("#9aa6c2"), p.sky[1], .35f), PosterRenderer.WHITE, .14f)
        } else {
            PosterRenderer.lerp(PosterRenderer.WHITE, p.sun, .16f)
        }
        val bodyC = if (isNight) {
            PosterRenderer.lerp(p.sky[1], PosterRenderer.c("#5d6685"), .55f)
        } else {
            PosterRenderer.lerp(PosterRenderer.c("#e9edf3"), p.sky[3], .12f)
        }
        val baseC = if (isNight) {
            PosterRenderer.lerp(p.sky[1], PosterRenderer.c("#171c29"), .6f)
        } else {
            PosterRenderer.lerp(PosterRenderer.c("#8e9cae"), p.sky[2], .4f)
        }
        blob(cv, x, y + rh * .4f, rw * 1.35f, rh * .95f, bodyC, .92f)
        blob(cv, x, y + rh * 1.1f, rw * 1.22f, rh * .42f, baseC, if (isNight) .5f else .6f)
        for (lobe in CUMULUS_LOBES) {
            val lx = x + lobe[0] * rw
            val ly = y + lobe[1] * rh
            val lr = lobe[2] * rw * .62f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    lx + sunDir * lr * .35f, ly - lr * .45f, lr,
                    intArrayOf(withAlphaF(lit, .95f), withAlphaF(bodyC, .92f), withAlphaF(bodyC, 0f)),
                    floatArrayOf(0f, .5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            cv.drawCircle(lx, ly, lr, paint)
        }
    }

    // ================= OVERCAST =================

    private fun paintOvercast(cv: Canvas, ctx: SceneCtx, k: Float) {
        val isNight = ctx.night > .4f
        val p = ctx.p
        val top = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#bfc5cf"), p.sky[1], .28f),
            PosterRenderer.lerp(PosterRenderer.c("#242a36"), p.sky[1], .38f),
            ctx.night,
        )
        val bot = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#848b97"), p.sky[2], .26f),
            PosterRenderer.lerp(PosterRenderer.c("#10141d"), p.sky[2], .32f),
            ctx.night,
        )
        val a = .85f + .13f * k
        val ceilingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, 0f, 0f, ctx.hF * .68f,
                intArrayOf(withAlphaF(top, a), withAlphaF(PosterRenderer.lerp(top, bot, .65f), a * .94f), withAlphaF(bot, 0f)),
                floatArrayOf(0f, .55f, 1f),
            )
        }
        cv.drawRect(0f, 0f, ctx.wF, ctx.hF * .68f, ceilingPaint)

        val rnd = Random(31)
        repeat(8) {
            val bx = ctx.wF * (rnd.nextFloat() * 1.1f - .05f)
            val by = ctx.hF * (.08f + rnd.nextFloat() * .34f)
            val bw = ctx.wF * (.26f + rnd.nextFloat() * .3f)
            val bh = ctx.hF * (.05f + rnd.nextFloat() * .05f)
            blob(cv, bx, by, bw, bh, PosterRenderer.lerp(bot, PosterRenderer.c("#3f4550"), .25f), (.3f + .24f * k) * (.6f + rnd.nextFloat() * .6f))
        }
        repeat(4) {
            val px = ctx.wF * (rnd.nextFloat() * 1.05f - .03f)
            val py = ctx.hF * (.05f + rnd.nextFloat() * .24f)
            val pw = ctx.wF * (.2f + rnd.nextFloat() * .24f)
            val ph2 = ctx.hF * (.04f + rnd.nextFloat() * .04f)
            blob(cv, px, py, pw, ph2, PosterRenderer.lerp(top, PosterRenderer.WHITE, if (isNight) .05f else .25f), .2f + .14f * rnd.nextFloat())
        }

        if (ctx.isDay) {
            blob(cv, ctx.spX.coerceIn(.18f, .82f) * ctx.wF, ctx.hF * .17f, ctx.wF * .42f, ctx.hF * .3f, PosterRenderer.lerp(p.sun, PosterRenderer.WHITE, .4f), .26f * (1f - k * .35f), additive = true)
        } else {
            blob(cv, ctx.spX.coerceIn(.18f, .82f) * ctx.wF, ctx.hF * .18f, ctx.wF * .3f, ctx.hF * .22f, PosterRenderer.c("#b8c4dc"), .12f, additive = true)
        }
    }

    // ================= FOG =================

    internal fun fogColor(p: PosterRenderer.Palette, night: Float): Int =
        PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#eef0f3"), p.hz, .10f),
            PosterRenderer.c("#6d7994"),
            night * .68f,
        )

    /** Diffuse light source with no crisp rim — used by FOG and HAZE instead of the sharp disc. */
    internal fun softDisc(cv: Canvas, cx: Float, cy: Float, hF: Float, p: PosterRenderer.Palette, tint: Int, amt: Float) {
        val r = hF * .13f
        val c = PosterRenderer.lerp(p.sun, tint, .35f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r * 2.2f,
                intArrayOf(
                    withAlphaF(PosterRenderer.lerp(c, PosterRenderer.WHITE, .5f), .65f * amt),
                    withAlphaF(c, .34f * amt),
                    withAlphaF(c, .12f * amt),
                    withAlphaF(c, 0f),
                ),
                floatArrayOf(0f, .28f, .6f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawCircle(cx, cy, r * 2.2f, paint)
    }

    private data class FogStratum(val y: Float, val a: Float, val lit: Float, val bh: Float, val x: Float)
    private val FOG_STRATA = listOf(
        FogStratum(-.135f, .5f, .55f, .055f, .42f),
        FogStratum(-.05f, .36f, -1f, .032f, .6f),
        FogStratum(.03f, .55f, .4f, .06f, .5f),
    )

    private fun paintFogMid(cv: Canvas, ctx: SceneCtx, k: Float) {
        val fc = fogColor(ctx.p, ctx.night)
        val isNight = ctx.night > .4f
        cv.drawRect(0f, 0f, ctx.wF, ctx.hF, Paint().apply { color = withAlphaF(fc, .03f * k) })
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, ctx.horizonY - ctx.hF * .26f, 0f, ctx.horizonY + ctx.hF * .08f,
                intArrayOf(withAlphaF(fc, 0f), withAlphaF(fc, .45f * k), withAlphaF(fc, .7f * k)),
                floatArrayOf(0f, .7f, 1f),
            )
        }
        cv.drawRect(0f, ctx.horizonY - ctx.hF * .28f, ctx.wF, ctx.horizonY + ctx.hF * .12f, g)
        for (s in FOG_STRATA) {
            val c = if (s.lit < 0f) {
                PosterRenderer.lerp(fc, if (isNight) PosterRenderer.c("#333e55") else PosterRenderer.c("#8b96a8"), .5f)
            } else {
                PosterRenderer.lerp(fc, PosterRenderer.WHITE, if (isNight) .2f else s.lit)
            }
            blob(cv, ctx.wF * s.x, ctx.horizonY + s.y * ctx.hF, ctx.wF * 1.05f, ctx.hF * s.bh, c, s.a * k)
        }
    }

    private fun paintFogFront(cv: Canvas, ctx: SceneCtx, k: Float) {
        val fc = fogColor(ctx.p, ctx.night)
        val isNight = ctx.night > .4f
        blob(cv, ctx.wF * .55f, ctx.horizonY + ctx.hF * .125f, ctx.wF * 1.1f, ctx.hF * .05f, PosterRenderer.lerp(fc, PosterRenderer.WHITE, if (isNight) .15f else .45f), .5f * k)
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, ctx.horizonY + ctx.hF * .02f, 0f, ctx.hF,
                intArrayOf(withAlphaF(fc, .06f * k), withAlphaF(fc, .26f * k), withAlphaF(fc, .42f * k)),
                floatArrayOf(0f, .55f, 1f),
            )
        }
        cv.drawRect(0f, ctx.horizonY, ctx.wF, ctx.hF, g)
        blob(cv, ctx.wF * .4f, ctx.hF * .99f, ctx.wF * .72f, ctx.hF * .085f, PosterRenderer.lerp(fc, PosterRenderer.WHITE, if (isNight) .1f else .3f), .46f * k)
        blob(cv, ctx.wF * .94f, ctx.hF * 1.02f, ctx.wF * .5f, ctx.hF * .07f, fc, .36f * k)
    }

    // ================= RAIN =================

    private fun paintRainDeck(cv: Canvas, ctx: SceneCtx, k: Float) {
        val p = ctx.p
        val top = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#57637a"), p.sky[1], .3f),
            PosterRenderer.lerp(PosterRenderer.c("#151a26"), p.sky[1], .3f),
            ctx.night,
        )
        val bot = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#3c4658"), p.sky[2], .25f),
            PosterRenderer.lerp(PosterRenderer.c("#0c101a"), p.sky[2], .3f),
            ctx.night,
        )
        val a = .72f + .24f * k
        val deckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, 0f, 0f, ctx.hF * .7f,
                intArrayOf(withAlphaF(top, a), withAlphaF(PosterRenderer.lerp(top, bot, .65f), a * .9f), withAlphaF(bot, 0f)),
                floatArrayOf(0f, .6f, 1f),
            )
        }
        cv.drawRect(0f, 0f, ctx.wF, ctx.hF * .7f, deckPaint)

        if (ctx.isDay && k < .55f) {
            blob(cv, ctx.spX.coerceIn(.2f, .8f) * ctx.wF, ctx.hF * .16f, ctx.wF * .34f, ctx.hF * .24f, PosterRenderer.lerp(p.sun, PosterRenderer.c("#cfd9e8"), .6f), .12f * (1f - k), additive = true)
        }

        val rnd = Random(53)
        val scud = PosterRenderer.lerp(bot, PosterRenderer.c("#0d1119"), .35f)
        repeat(4) {
            val bx = ctx.wF * (rnd.nextFloat() * 1.15f - .08f)
            val by = ctx.hF * (.26f + rnd.nextFloat() * .26f)
            val bw = ctx.wF * (.24f + rnd.nextFloat() * .3f)
            val bh = ctx.hF * (.03f + rnd.nextFloat() * .032f)
            blob(cv, bx, by, bw, bh, scud, (.3f + .3f * k) * (.55f + rnd.nextFloat() * .55f))
        }
    }

    /** Wet-ground reflection band. [storm] cools + dims it — RAIN gets a warmer, brighter sheen. */
    private fun wetSheen(cv: Canvas, ctx: SceneCtx, k: Float, storm: Boolean) {
        val c = PosterRenderer.lerp(ctx.p.hz, PosterRenderer.c("#8fa2bc"), if (storm) .78f else .55f)
        val a = (if (storm) .06f + .03f * k else .09f + .05f * k) * (1f - ctx.night * .55f)
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            shader = linearGradientOf(
                0f, ctx.horizonY + ctx.hF * .09f, 0f, ctx.hF,
                intArrayOf(withAlphaF(c, a), withAlphaF(c, 0f)),
                floatArrayOf(0f, 1f),
            )
        }
        cv.drawRect(0f, ctx.horizonY + ctx.hF * .06f, ctx.wF, ctx.hF, g)
        blob(cv, ctx.hgX, ctx.horizonY + ctx.hF * .14f, ctx.wF * .09f, ctx.hF * .16f, c, a * 1.4f, additive = true)
    }

    // ================= STORM =================

    private fun paintStormDeck(cv: Canvas, ctx: SceneCtx, k: Float) {
        val p = ctx.p
        val top = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#2a3044"), p.sky[1], .25f),
            PosterRenderer.c("#10131e"),
            ctx.night * .6f,
        )
        val bot = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#171b28"), p.sky[2], .2f),
            PosterRenderer.c("#0a0d15"),
            ctx.night * .6f,
        )
        val baseY = ctx.hF * (.30f + .06f * k)
        val rnd = Random(17)

        val shelf = Path().apply {
            addRect(0f, -ctx.hF * .02f, ctx.wF, baseY, Path.Direction.CW)
            for (i in 0..9) {
                val cx2 = ctx.wF * (i / 9f)
                val r = ctx.wF * (.09f + rnd.nextFloat() * .05f)
                val cy2 = baseY + sin(i * 1.7f) * ctx.hF * .012f + rnd.nextFloat() * ctx.hF * .015f
                addCircle(cx2, cy2, r, Path.Direction.CW)
            }
            repeat(3) { j ->
                val hx = ctx.wF * (.16f + .3f * j + rnd.nextFloat() * .12f)
                val hr = ctx.wF * (.07f + rnd.nextFloat() * .04f)
                val hy = baseY + ctx.hF * (.015f + rnd.nextFloat() * .025f)
                addCircle(hx, hy, hr, Path.Direction.CW)
            }
        }
        val shelfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, 0f, 0f, baseY + ctx.hF * .12f,
                intArrayOf(withAlphaF(top, .96f), withAlphaF(PosterRenderer.lerp(top, bot, .5f), .96f), withAlphaF(bot, .97f)),
                floatArrayOf(0f, .55f, 1f),
            )
        }
        cv.drawPath(shelf, shelfPaint)

        val underMurk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, baseY - ctx.hF * .01f, 0f, baseY + ctx.hF * .16f,
                intArrayOf(withAlphaF(bot, .55f), withAlphaF(bot, 0f)),
                floatArrayOf(0f, 1f),
            )
        }
        cv.drawRect(0f, baseY - ctx.hF * .02f, ctx.wF, baseY + ctx.hF * .18f, underMurk)

        val fractusColor = PosterRenderer.lerp(bot, PosterRenderer.c("#04060a"), .45f)
        repeat(4) {
            val fx = ctx.wF * (rnd.nextFloat() * 1.1f - .05f)
            val fy = baseY + ctx.hF * (.035f + rnd.nextFloat() * .05f)
            val fw = ctx.wF * (.14f + rnd.nextFloat() * .16f)
            val fh = ctx.hF * (.018f + rnd.nextFloat() * .02f)
            blob(cv, fx, fy, fw, fh, fractusColor, .5f + .3f * rnd.nextFloat())
        }

        cv.drawRect(0f, baseY - ctx.hF * .06f, ctx.wF, baseY + ctx.hF * .08f, Paint().apply { color = withAlphaF(PosterRenderer.c("#3a4640"), .10f) })
        blob(cv, ctx.wF * .5f, baseY - ctx.hF * .07f, ctx.wF * .3f, ctx.hF * .11f, PosterRenderer.c("#3f4a6e"), .22f, additive = true)
        blob(cv, ctx.wF * .5f, ctx.hF * .62f, ctx.wF * .85f, ctx.hF * .032f, PosterRenderer.c("#5f6a85"), .09f, additive = true)
    }

    private fun strokePts(cv: Canvas, pts: List<FloatArray>, paint: Paint) {
        val path = Path().apply {
            moveTo(pts[0][0], pts[0][1])
            for (i in 1 until pts.size) lineTo(pts[i][0], pts[i][1])
        }
        cv.drawPath(path, paint)
    }

    /** Baked bolt: fixed seed + intensity — one stable, always-visible bolt per repaint. */
    private fun paintLightning(cv: Canvas, ctx: SceneCtx, seed: Int = 7, intensity: Float = .85f) {
        val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            shader = RadialGradient(
                ctx.wF * .5f, ctx.hF * .18f, ctx.hF,
                intArrayOf(withAlphaF(PosterRenderer.c("#cdd8ff"), .32f * intensity), withAlphaF(PosterRenderer.c("#cdd8ff"), 0f)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        cv.drawRect(0f, 0f, ctx.wF, ctx.hF, flashPaint)

        val rnd = Random(seed.toLong())
        var x = ctx.wF * (.32f + rnd.nextFloat() * .36f)
        var y = 0f
        val pts = mutableListOf(floatArrayOf(x, y))
        val segs = 8
        repeat(segs) {
            y += ctx.hF * .62f / segs
            x += (rnd.nextFloat() - .5f) * ctx.wF * .13f
            pts.add(floatArrayOf(x, y))
        }
        val boltGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            color = withAlphaF(PosterRenderer.c("#bcd0ff"), .28f * intensity)
            strokeWidth = (ctx.hF * .02f).coerceAtLeast(3f)
        }
        strokePts(cv, pts, boltGlow)
        val boltCore = Paint(boltGlow).apply {
            color = withAlphaF(PosterRenderer.c("#f2f6ff"), .95f * intensity)
            strokeWidth = (ctx.hF * .006f).coerceAtLeast(1.4f)
        }
        strokePts(cv, pts, boltCore)

        val bi = (2 + (rnd.nextFloat() * 3).toInt()).coerceIn(0, pts.size - 1)
        var bx = pts[bi][0]
        var by = pts[bi][1]
        val branch = mutableListOf(floatArrayOf(bx, by))
        repeat(3) {
            bx += (rnd.nextFloat() - .2f) * ctx.wF * .09f
            by += ctx.hF * .05f
            branch.add(floatArrayOf(bx, by))
        }
        val branchPaint = Paint(boltGlow).apply {
            color = withAlphaF(PosterRenderer.c("#dfe7ff"), .5f * intensity)
            strokeWidth = (ctx.hF * .004f).coerceAtLeast(1f)
        }
        strokePts(cv, branch, branchPaint)
    }

    // ================= SNOW =================

    private fun paintSnowSky(cv: Canvas, ctx: SceneCtx, k: Float) {
        val p = ctx.p
        val isNight = ctx.night > .4f
        val top = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#e2e8f0"), p.sky[1], .3f),
            PosterRenderer.lerp(PosterRenderer.c("#39445e"), p.sky[1], .4f),
            ctx.night,
        )
        val bot = PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#cdd6e2"), p.sky[2], .3f),
            PosterRenderer.lerp(PosterRenderer.c("#242e47"), p.sky[2], .4f),
            ctx.night,
        )
        val a = .6f + .3f * k
        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, 0f, 0f, ctx.hF * .62f,
                intArrayOf(withAlphaF(top, a), withAlphaF(PosterRenderer.lerp(top, bot, .6f), a * .85f), withAlphaF(bot, 0f)),
                floatArrayOf(0f, .6f, 1f),
            )
        }
        cv.drawRect(0f, 0f, ctx.wF, ctx.hF * .62f, skyPaint)

        val ambient = if (isNight) PosterRenderer.c("#93a5c8") else PosterRenderer.lerp(p.sun, PosterRenderer.WHITE, .5f)
        blob(cv, ctx.spX.coerceIn(.2f, .8f) * ctx.wF, ctx.hF * .18f, ctx.wF * .44f, ctx.hF * .3f, ambient, if (isNight) .14f else .24f, additive = true)

        val rnd = Random(61)
        repeat(6) {
            val bx = ctx.wF * (rnd.nextFloat() * 1.1f - .05f)
            val by = ctx.hF * (.08f + rnd.nextFloat() * .3f)
            val bw = ctx.wF * (.24f + rnd.nextFloat() * .26f)
            val bh = ctx.hF * (.045f + rnd.nextFloat() * .04f)
            blob(cv, bx, by, bw, bh, bot, .2f + .12f * rnd.nextFloat())
        }
    }

    private fun paintSnowBlanket(cv: Canvas, ctx: SceneCtx, topY: Float) {
        cv.save()
        val clip = PosterRenderer.nearRidgePath(ctx.wF, ctx.hF, topY).apply {
            lineTo(ctx.wF, ctx.hF)
            lineTo(0f, ctx.hF)
            close()
        }
        cv.clipPath(clip)
        val bright = if (ctx.night > .4f) .5f else .9f
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, topY - ctx.hF * .02f, 0f, topY + ctx.hF * .16f,
                intArrayOf(withAlphaF(PosterRenderer.c("#f4f8fc"), bright), withAlphaF(PosterRenderer.c("#dfe8f2"), bright * .5f), withAlphaF(PosterRenderer.c("#dfe8f2"), 0f)),
                floatArrayOf(0f, .45f, 1f),
            )
        }
        cv.drawRect(0f, topY - ctx.hF * .04f, ctx.wF, topY + ctx.hF * .2f, g)
        cv.restore()
    }

    // ================= HAZE =================

    internal fun hazeWarm(p: PosterRenderer.Palette, night: Float): Int =
        PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#eacd9c"), p.hz, .3f),
            PosterRenderer.c("#8a7256"),
            night * .6f,
        )

    private fun paintHazeAtmos(cv: Canvas, ctx: SceneCtx, k: Float) {
        val warm = hazeWarm(ctx.p, ctx.night)
        val isNight = ctx.night > .4f
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = linearGradientOf(
                0f, ctx.hF * .06f, 0f, ctx.horizonY + ctx.hF * .1f,
                intArrayOf(
                    withAlphaF(warm, .05f * k),
                    withAlphaF(warm, .3f * k),
                    withAlphaF(PosterRenderer.lerp(warm, PosterRenderer.WHITE, if (isNight) .1f else .4f), .62f * k),
                ),
                floatArrayOf(0f, .6f, 1f),
            )
        }
        cv.drawRect(0f, 0f, ctx.wF, ctx.horizonY + ctx.hF * .1f, g)
        blob(cv, ctx.cx, ctx.cy, ctx.wF * .5f, ctx.hF * .42f, PosterRenderer.lerp(warm, PosterRenderer.WHITE, .4f), .32f * k * (if (isNight) .4f else 1f), additive = true)
        cv.drawRect(0f, ctx.horizonY, ctx.wF, ctx.hF, Paint().apply { color = withAlphaF(warm, .07f * k) })
    }

    // ================= PRECIPITATION (static bake) =================

    private data class RainLayer(val n: Int, val len: Float, val a: Float, val speed: Float, val lw: Float, val color: Int)
    private val RAIN_LAYERS = listOf(
        RainLayer(90, .045f, .14f, .5f, .0028f, PosterRenderer.c("#a7b6c9")),
        RainLayer(66, .095f, .28f, .9f, .0042f, PosterRenderer.c("#c2d0e2")),
        RainLayer(36, .165f, .5f, 1.35f, .0065f, PosterRenderer.c("#dfe9f7")),
    )

    private fun paintRainFall(cv: Canvas, ctx: SceneCtx, k: Float, wind: Float) {
        val r = .25f + .75f * k
        if (r <= 0f) return
        val lenScale = .6f + .4f * k
        val windX = sin(wind)
        val windY = kotlin.math.cos(wind)
        val dim = if (ctx.night > .4f) .55f else 1f

        if (k > .8f) {
            val mist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = linearGradientOf(
                    0f, ctx.hF * .68f, 0f, ctx.hF * .82f,
                    intArrayOf(withAlphaF(PosterRenderer.c("#b9c6d8"), 0f), withAlphaF(PosterRenderer.c("#b9c6d8"), (k - .8f) * .55f * dim)),
                    floatArrayOf(0f, 1f),
                )
            }
            cv.drawRect(0f, ctx.hF * .66f, ctx.wF, ctx.hF * .86f, mist)
        }

        RAIN_LAYERS.forEachIndexed { layerIdx, layer ->
            val n = (layer.n * r).roundToInt()
            val len = ctx.hF * layer.len * lenScale
            val dx = windX * len
            val dy = windY * len
            val span = ctx.hF * 1.25f
            val rnd = Random(90L + layerIdx * 7)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                color = layer.color
                strokeWidth = (ctx.hF * layer.lw).coerceAtLeast(1f)
            }
            repeat(n) {
                val sx = rnd.nextFloat() * ctx.wF * 1.3f - ctx.wF * .15f
                val y0 = ((rnd.nextFloat() * span) + STATIC_PHASE * span * layer.speed * (.7f + rnd.nextFloat() * .6f)).mod(span) - ctx.hF * .12f
                paint.alpha = PosterRenderer.a255(layer.a * dim * (.6f + rnd.nextFloat() * .5f))
                cv.drawLine(sx, y0, sx + dx, y0 + dy, paint)
            }
        }
    }

    private data class SnowLayer(val n: Int, val r: Float, val a: Float, val speed: Float)
    private val SNOW_LAYERS = listOf(
        SnowLayer(56, .004f, .4f, .15f),
        SnowLayer(40, .01f, .62f, .26f),
        SnowLayer(22, .021f, .88f, .4f),
    )

    private fun paintSnowFall(cv: Canvas, ctx: SceneCtx, k: Float) {
        val r = .25f + .75f * k
        if (r <= 0f) return
        val dim = if (ctx.night > .4f) .7f else 1f
        SNOW_LAYERS.forEachIndexed { layerIdx, layer ->
            val n = (layer.n * r).roundToInt()
            val rnd = Random(70L + layerIdx * 13)
            val span = ctx.hF * 1.2f
            val rr = ctx.hF * layer.r
            repeat(n) { i ->
                val bx = rnd.nextFloat()
                val by = rnd.nextFloat()
                val sway = sin(STATIC_PHASE * 6.28f * (.5f + layer.speed) + i * 1.3f) * ctx.wF * (.02f + .03f * layer.speed)
                val x = bx * ctx.wF + sway
                val y = ((by * span) + STATIC_PHASE * span * layer.speed).mod(span) - ctx.hF * .1f
                if (layerIdx == 2) {
                    val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            x, y, rr,
                            intArrayOf(withAlphaF(PosterRenderer.c("#f6faff"), layer.a * dim), withAlphaF(PosterRenderer.c("#e9f1f9"), layer.a * .5f * dim), withAlphaF(PosterRenderer.c("#e9f1f9"), 0f)),
                            floatArrayOf(0f, .55f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    }
                    cv.drawCircle(x, y, rr, flakePaint)
                } else {
                    cv.drawCircle(x, y, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlphaF(PosterRenderer.c("#f0f6fc"), layer.a * dim) })
                }
            }
        }
    }

    // ================= dispatch (used by PosterRenderer.render) =================

    /** Weather-tinted 5-stop sky, or the unmodified palette sky for CLEAR. */
    fun weatherSky(p: PosterRenderer.Palette, weather: PosterWeather, night: Float): IntArray {
        val amts: FloatArray
        val tint: Int
        when (weather.condition) {
            WeatherCondition.CLEAR -> return p.sky
            WeatherCondition.PARTLY -> { tint = PosterRenderer.c("#dfe6ee"); amts = floatArrayOf(0f, .02f, .03f, .04f, .05f) }
            WeatherCondition.OVERCAST -> {
                tint = PosterRenderer.lerp(PosterRenderer.c("#9aa1ab"), PosterRenderer.c("#232833"), night)
                amts = floatArrayOf(.42f, .4f, .36f, .3f, .24f)
            }
            WeatherCondition.FOG -> { tint = fogColor(p, night); amts = floatArrayOf(.1f, .13f, .2f, .3f, .4f) }
            WeatherCondition.RAIN -> {
                tint = PosterRenderer.lerp(PosterRenderer.c("#55637a"), PosterRenderer.c("#1a212e"), night)
                amts = floatArrayOf(.48f, .46f, .42f, .38f, .32f)
            }
            WeatherCondition.STORM -> { tint = PosterRenderer.c("#252b3a"); amts = floatArrayOf(.62f, .6f, .56f, .52f, .5f) }
            WeatherCondition.SNOW -> {
                tint = PosterRenderer.lerp(PosterRenderer.c("#dbe3ec"), PosterRenderer.c("#31405e"), night)
                amts = floatArrayOf(.48f, .46f, .42f, .38f, .34f)
            }
            WeatherCondition.HAZE -> { tint = hazeWarm(p, night); amts = floatArrayOf(.05f, .08f, .14f, .24f, .34f) }
        }
        return IntArray(5) { PosterRenderer.lerp(p.sky[it], tint, amts[it]) }
    }

    /** Extra darkening overlay strength — only RAIN and STORM. */
    fun darken(weather: PosterWeather): Float {
        val k = weather.intensity / 100f
        return when (weather.condition) {
            WeatherCondition.RAIN -> .1f + .08f * k
            WeatherCondition.STORM -> .3f + .12f * k
            else -> 0f
        }
    }

    fun glowMul(weather: PosterWeather): Float = when (weather.condition) {
        WeatherCondition.CLEAR -> 1f
        WeatherCondition.PARTLY -> .78f
        WeatherCondition.OVERCAST -> .2f
        WeatherCondition.FOG -> .45f
        WeatherCondition.RAIN -> .12f
        WeatherCondition.STORM -> .06f
        WeatherCondition.SNOW -> .3f
        WeatherCondition.HAZE -> .8f
    }

    fun starMul(weather: PosterWeather): Float = when (weather.condition) {
        WeatherCondition.CLEAR -> 1f
        WeatherCondition.PARTLY -> .75f
        WeatherCondition.OVERCAST -> .05f
        WeatherCondition.FOG -> .45f
        WeatherCondition.RAIN -> .03f
        WeatherCondition.STORM -> 0f
        WeatherCondition.SNOW -> .08f
        WeatherCondition.HAZE -> .3f
    }

    /** Weather-tinted far ridge color — prevents a phantom warm band from the raw palette. */
    fun farTint(p: PosterRenderer.Palette, baseFar: Int, weather: PosterWeather, night: Float): Int = when (weather.condition) {
        WeatherCondition.CLEAR, WeatherCondition.PARTLY -> baseFar
        WeatherCondition.OVERCAST -> PosterRenderer.lerp(baseFar, PosterRenderer.lerp(PosterRenderer.c("#9aa1ab"), PosterRenderer.c("#232833"), night), .3f)
        WeatherCondition.FOG -> PosterRenderer.lerp(baseFar, fogColor(p, night), .45f)
        WeatherCondition.RAIN -> PosterRenderer.lerp(baseFar, PosterRenderer.c("#39424f"), .45f)
        WeatherCondition.STORM -> PosterRenderer.lerp(baseFar, PosterRenderer.c("#1f2430"), .72f)
        WeatherCondition.SNOW -> PosterRenderer.lerp(baseFar, if (night > .4f) PosterRenderer.c("#5b6c8c") else PosterRenderer.c("#b3c2d4"), .52f)
        WeatherCondition.HAZE -> PosterRenderer.lerp(baseFar, hazeWarm(p, night), .55f)
    }

    /** Weather-tinted near ridge color. */
    fun nearTint(p: PosterRenderer.Palette, baseNear: Int, weather: PosterWeather, night: Float): Int = when (weather.condition) {
        WeatherCondition.CLEAR, WeatherCondition.PARTLY, WeatherCondition.OVERCAST -> baseNear
        WeatherCondition.STORM -> PosterRenderer.lerp(baseNear, PosterRenderer.c("#000000"), .6f)
        WeatherCondition.FOG -> PosterRenderer.lerp(baseNear, fogColor(p, night), .14f)
        WeatherCondition.RAIN -> PosterRenderer.lerp(baseNear, PosterRenderer.c("#04060a"), .5f)
        WeatherCondition.SNOW -> PosterRenderer.lerp(baseNear, if (night > .4f) PosterRenderer.c("#6d7f9e") else PosterRenderer.c("#dbe5f0"), if (night > .4f) .45f else .7f)
        WeatherCondition.HAZE -> PosterRenderer.lerp(baseNear, hazeWarm(p, night), .12f)
    }

    fun scrimMul(weather: PosterWeather): Float = when (weather.condition) {
        WeatherCondition.FOG -> .72f
        WeatherCondition.SNOW -> .62f
        else -> 1f
    }

    /** The condition-specific sky content + light source. */
    fun paintConditionLayer(cv: Canvas, ctx: SceneCtx, weather: PosterWeather, nearC: Int) {
        val k = weather.intensity / 100f
        when (weather.condition) {
            WeatherCondition.CLEAR -> {
                PosterRenderer.haze(cv, ctx.wF, ctx.hF, ctx.cx, ctx.p, ctx.night, 1f)
                PosterRenderer.bodyPublic(cv, ctx.cx, ctx.cy, ctx.hF, ctx.isDay, ctx.p)
            }
            WeatherCondition.PARTLY -> {
                PosterRenderer.haze(cv, ctx.wF, ctx.hF, ctx.cx, ctx.p, ctx.night, .8f)
                PosterRenderer.bodyPublic(cv, ctx.cx, ctx.cy, ctx.hF, ctx.isDay, ctx.p)
                paintPartly(cv, ctx, k)
            }
            WeatherCondition.OVERCAST -> paintOvercast(cv, ctx, k)
            WeatherCondition.FOG -> {
                softDisc(cv, ctx.cx, ctx.cy, ctx.hF, ctx.p, fogColor(ctx.p, ctx.night), if (ctx.isDay) .8f else .5f)
                paintFogMid(cv, ctx, k)
                val midColor = PosterRenderer.lerp(nearC, fogColor(ctx.p, ctx.night), .38f)
                PosterRenderer.ridge(cv, ctx.wF, ctx.hF, ctx.horizonY + ctx.hF * .045f, .72f, midColor, ctx.p, far = true)
                blob(cv, ctx.wF * .5f, ctx.horizonY + ctx.hF * .075f, ctx.wF * 1.1f, ctx.hF * .045f, PosterRenderer.lerp(fogColor(ctx.p, ctx.night), PosterRenderer.WHITE, if (ctx.night > .4f) .15f else .5f), .44f * k)
            }
            WeatherCondition.RAIN -> paintRainDeck(cv, ctx, k)
            WeatherCondition.STORM -> paintStormDeck(cv, ctx, k)
            WeatherCondition.SNOW -> paintSnowSky(cv, ctx, k)
            WeatherCondition.HAZE -> {
                paintHazeAtmos(cv, ctx, k)
                softDisc(cv, ctx.cx, ctx.cy, ctx.hF, ctx.p, hazeWarm(ctx.p, ctx.night), if (ctx.isDay) .95f else .5f)
            }
        }
    }

    /** Per-condition ground treatment, painted right after the near ridge. */
    fun paintGroundTreatment(cv: Canvas, ctx: SceneCtx, weather: PosterWeather) {
        val k = weather.intensity / 100f
        when (weather.condition) {
            WeatherCondition.SNOW -> paintSnowBlanket(cv, ctx, ctx.horizonY + ctx.hF * .11f)
            WeatherCondition.RAIN -> wetSheen(cv, ctx, k, storm = false)
            WeatherCondition.STORM -> wetSheen(cv, ctx, k, storm = true)
            WeatherCondition.FOG -> paintFogFront(cv, ctx, k)
            else -> Unit
        }
    }

    /** Static-baked precipitation. */
    fun paintPrecip(cv: Canvas, ctx: SceneCtx, weather: PosterWeather) {
        val k = weather.intensity / 100f
        when (weather.condition) {
            WeatherCondition.RAIN -> paintRainFall(cv, ctx, k, wind = .16f + k * .12f)
            WeatherCondition.STORM -> {
                paintRainFall(cv, ctx, .7f + .3f * k, wind = .3f + .12f * k)
                paintLightning(cv, ctx)
            }
            WeatherCondition.SNOW -> paintSnowFall(cv, ctx, k)
            else -> Unit
        }
    }
}
