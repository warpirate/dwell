# Poster Clock Weather Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the poster Clock widget an optional, manually-chosen weather layer (8 bespoke conditions, ported from `design/widget-weather-fable.html`) composited over its existing time-of-day scene, plus the minimal config surface needed to pick one.

**Architecture:** A new `PosterWeather` model + per-appWidgetId DataStore (mirrors `WidgetStyle`/`WidgetStyleStore`), a new `WeatherPainter` object holding the ported Canvas compositors, `PosterRenderer.render()` gaining a `weather` parameter whose CLEAR path is byte-identical to today, and a new slim pin-time config screen (mirrors `WidgetConfigActivity`/`WidgetPinReceiver`) reached from the Widgets tab.

**Tech Stack:** Kotlin, `android.graphics.Canvas`/`Paint`/`Shader`, Jetpack Compose (Material3), Hilt, AndroidX DataStore Preferences, JUnit4.

**Reference spec:** `docs/superpowers/specs/2026-07-02-poster-clock-weather-design.md`
**Reference art source (ground truth for every formula ported below):** `design/widget-weather-fable.html`

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/dwell/app/data/widget/PosterWeather.kt` | `WeatherCondition` enum + `PosterWeather` data class (encode/decode) |
| `app/src/main/java/com/dwell/app/data/widget/PosterWeatherStore.kt` | Store interface |
| `app/src/main/java/com/dwell/app/data/widget/DataStorePosterWeatherStore.kt` | DataStore impl, per-appWidgetId |
| `app/src/main/java/com/dwell/app/di/RepositoryModule.kt` | *(modify)* bind the new store |
| `app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt` | *(modify)* loosen shared helpers to `internal`, extract `nearRidgePath`, add `mul` param to `haze()`, thread `weather` through `render()` |
| `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt` | New — `SceneCtx`, all 8 condition compositors, precip, `blob` helper |
| `app/src/main/java/com/dwell/app/widget/poster/PosterClockWidgetProvider.kt` | *(modify)* Hilt-inject the store, read weather, pass to `render()` |
| `app/src/main/java/com/dwell/app/widget/poster/PosterPinReceiver.kt` | New — bakes chosen weather into the store once the real appWidgetId exists |
| `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigViewModel.kt` | New |
| `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigActivity.kt` | New |
| `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigScreen.kt` | New |
| `app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt` | *(modify)* CLOCK tap opens the new activity |
| `app/src/main/AndroidManifest.xml` | *(modify)* register activity + receiver |
| `app/src/test/java/com/dwell/app/data/widget/PosterWeatherTest.kt` | New — encode/decode round-trip |
| `app/src/test/java/com/dwell/app/widget/poster/PosterRendererWeatherTest.kt` | New — smoke test, 8 conditions x 4 hours |

---

## Task 1: PosterWeather model

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/widget/PosterWeather.kt`
- Test: `app/src/test/java/com/dwell/app/data/widget/PosterWeatherTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dwell.app.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterWeatherTest {

    @Test
    fun `every condition round-trips through encode and decode`() {
        WeatherCondition.entries.forEach { condition ->
            listOf(0, 1, 37, 70, 100).forEach { intensity ->
                val original = PosterWeather(condition, intensity)
                val decoded = PosterWeather.decode(original.encode())
                assertEquals("condition mismatch for $condition@$intensity", original, decoded)
            }
        }
    }

    @Test
    fun `decode falls back to Default on garbage input`() {
        assertEquals(PosterWeather.Default, PosterWeather.decode(""))
        assertEquals(PosterWeather.Default, PosterWeather.decode("not|even|close|to|valid"))
        assertEquals(PosterWeather.Default, PosterWeather.decode("BOGUS_CONDITION|70"))
        assertEquals(PosterWeather.Default, PosterWeather.decode("RAIN|not-a-number"))
    }

    @Test
    fun `coerced clamps intensity into 0..100`() {
        assertEquals(0, PosterWeather(WeatherCondition.RAIN, -20).coerced().intensity)
        assertEquals(100, PosterWeather(WeatherCondition.RAIN, 500).coerced().intensity)
    }

    @Test
    fun `default is clear at 70 percent`() {
        assertEquals(WeatherCondition.CLEAR, PosterWeather.Default.condition)
        assertEquals(70, PosterWeather.Default.intensity)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.PosterWeatherTest" -q`
Expected: FAIL - `WeatherCondition`/`PosterWeather` unresolved references.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.dwell.app.data.widget

/**
 * The 8 poster-scene weather conditions, each a bespoke compositor in [WeatherPainter] -
 * see design/widget-weather-fable.html for the visually-validated source of truth every
 * formula there was ported 1:1 from.
 */
enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOG("Fog"),
    RAIN("Rain"),
    STORM("Thunderstorm"),
    SNOW("Snow"),
    HAZE("Haze"),
}

/**
 * A poster widget instance's chosen weather. Manually set (no live data source in this
 * slice - see the design spec's "Explicitly out of scope"). [intensity] is 0..100 and is
 * ignored by the renderer when [condition] is CLEAR, but still stored so flipping back to
 * a precip condition remembers the last-chosen strength.
 */
data class PosterWeather(
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val intensity: Int = 70,
) {
    fun coerced(): PosterWeather = copy(intensity = intensity.coerceIn(0, 100))

    fun encode(): String = "${condition.name}|$intensity"

    companion object {
        val Default = PosterWeather()

        fun decode(raw: String): PosterWeather = runCatching {
            val parts = raw.split("|").also { require(it.size == 2) }
            PosterWeather(
                condition = WeatherCondition.valueOf(parts[0]),
                intensity = parts[1].toInt(),
            ).coerced()
        }.getOrDefault(Default)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.widget.PosterWeatherTest" -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/widget/PosterWeather.kt app/src/test/java/com/dwell/app/data/widget/PosterWeatherTest.kt
git commit -m "feat(widget): add PosterWeather model"
```

---

## Task 2: PosterWeatherStore + DataStore impl + DI binding

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/widget/PosterWeatherStore.kt`
- Create: `app/src/main/java/com/dwell/app/data/widget/DataStorePosterWeatherStore.kt`
- Modify: `app/src/main/java/com/dwell/app/di/RepositoryModule.kt`

- [ ] **Step 1: Write the store interface**

```kotlin
package com.dwell.app.data.widget

import kotlinx.coroutines.flow.Flow

/** Persists a [PosterWeather] per appWidgetId, for the poster Clock widget. */
interface PosterWeatherStore {
    fun observe(appWidgetId: Int): Flow<PosterWeather>
    suspend fun get(appWidgetId: Int): PosterWeather
    suspend fun save(appWidgetId: Int, weather: PosterWeather)
    suspend fun clear(appWidgetId: Int)
}
```

- [ ] **Step 2: Write the DataStore implementation**

Reuses the existing `"widget_styles"` `DataStore<Preferences>` bean from `DataModule.provideWidgetDataStore` - a distinct key prefix is enough to namespace it, no new DataStore file needed.

```kotlin
package com.dwell.app.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStorePosterWeatherStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PosterWeatherStore {

    private fun key(id: Int) = stringPreferencesKey("poster_weather_$id")

    override fun observe(appWidgetId: Int): Flow<PosterWeather> =
        dataStore.data.map { prefs ->
            prefs[key(appWidgetId)]?.let { PosterWeather.decode(it) } ?: PosterWeather.Default
        }

    override suspend fun get(appWidgetId: Int): PosterWeather = observe(appWidgetId).first()

    override suspend fun save(appWidgetId: Int, weather: PosterWeather) {
        dataStore.edit { it[key(appWidgetId)] = weather.coerced().encode() }
    }

    override suspend fun clear(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }
}
```

- [ ] **Step 3: Bind it in RepositoryModule**

Add the import and binding next to `bindWidgetStyleStore` in `app/src/main/java/com/dwell/app/di/RepositoryModule.kt`:

```kotlin
import com.dwell.app.data.widget.DataStorePosterWeatherStore
import com.dwell.app.data.widget.PosterWeatherStore
```

```kotlin
    @Binds
    @Singleton
    abstract fun bindPosterWeatherStore(impl: DataStorePosterWeatherStore): PosterWeatherStore
```

- [ ] **Step 4: Compile to verify wiring**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/widget/PosterWeatherStore.kt app/src/main/java/com/dwell/app/data/widget/DataStorePosterWeatherStore.kt app/src/main/java/com/dwell/app/di/RepositoryModule.kt
git commit -m "feat(widget): persist poster weather per widget id"
```

---

## Task 3: Loosen PosterRenderer internals for cross-file reuse

`WeatherPainter` lives in a second file in the same package. Kotlin's `private` is file-scoped, not package-scoped, so the shared helpers `PosterRenderer` already has need to become `internal` (visible package-wide, still invisible outside the module) before `WeatherPainter` can call them. This step also extracts the near-ridge silhouette path (needed unchanged by the new snow-blanket clip) and adds an optional strength multiplier to the existing `haze()` band painter (used at reduced strength for PARTLY).

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt`

- [ ] **Step 1: Widen visibility of the helpers WeatherPainter will need**

In `PosterRenderer.kt`, change these from `private` to `internal` (leave everything else untouched):
- `private fun c(hex: String)` becomes `internal fun c(hex: String)`
- `private fun lerp(...)` becomes `internal fun lerp(...)`
- `private fun withAlpha(...)` becomes `internal fun withAlpha(...)`
- `private fun a255(f: Float)` becomes `internal fun a255(f: Float)`
- `private fun luma(color: Int)` becomes `internal fun luma(color: Int)`
- `private fun sunPos(hour: Float)` becomes `internal fun sunPos(hour: Float)`
- The `WHITE`, `PALE`, `MOON_MID`, `MOON_EDGE` constants become `internal val`

`Palette` is already a public nested data class with public fields - no change needed there.

- [ ] **Step 2: Extract the near-ridge path into its own function**

In `ridge()`, the path built for `far = false` is the exact silhouette `snowBlanket` (Task 10) needs to clip against. Replace the inline `Path()` construction in `ridge()` with a call to a new extracted function:

```kotlin
    /** The near-ridge silhouette curve, shared with [WeatherPainter]'s snow blanket clip. */
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
```

Then simplify `ridge()`'s path branch: when `far` is `false`, call `nearRidgePath(wF, hF, topY)` instead of duplicating the loop; when `far` is `true`, keep its own loop as-is (different `amp`/`seed`, not reused elsewhere).

- [ ] **Step 3: Add an optional strength multiplier to haze()**

Change the signature and apply the multiplier to each band's alpha:

```kotlin
    internal fun haze(cv: Canvas, wF: Float, hF: Float, cx: Float, p: Palette, night: Float, mul: Float = 1f) {
        // ... unchanged body, except:
        val a = b[3] * (1f - night * .55f) * mul
        // ... rest unchanged
    }
```

(Only the `private fun haze` to `internal fun haze` visibility change and the one `* mul` multiplication - every other line in the function body is unchanged.)

- [ ] **Step 4: Compile to verify nothing broke**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. `render()`'s own call sites (`ridge(...)`, `haze(cv, wF, hF, cx, p, night)`) still compile since `mul` defaults to `1f` and `ridge`'s public signature is unchanged.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt
git commit -m "refactor(widget): widen PosterRenderer internals for the weather painter"
```

---

## Task 4: PosterWeatherPainter.kt scaffolding - SceneCtx + blob

**Files:**
- Create: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Create the file with the shared context + the blob primitive**

`blob` is the recurring "soft elliptical radial-gradient patch" used by every condition (translate + scale + unit-circle `RadialGradient`, exactly mirroring the existing `haze()` band-painting idiom already in `PosterRenderer.kt`). It also takes an `additive` flag for the "lighter"/ADD-blend glows used across several conditions.

```kotlin
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
 * 1:1 from design/widget-weather-fable.html - that file is the visually-validated source of
 * truth for every constant below; see its header for the full rationale (each condition owns
 * a bespoke look so no two are ever confusable, weather always composites over the hour's
 * palette rather than replacing it).
 *
 * Static bake only: RemoteViews can't animate, so precipitation/lightning are painted at a
 * single fixed phase - one representative frame, exactly the HTML file's own documented
 * Android caveat.
 */
internal object WeatherPainter {

    /** Per-render values every compositor needs; computed once in [PosterRenderer.render]. */
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

    /** A soft elliptical radial-gradient patch - the recurring cloud/fog/light unit. */
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
}
```

- [ ] **Step 2: Compile to verify the scaffold is valid**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (unused-private-function warnings are fine and expected at this stage - nothing calls `blob`/`linearGradientOf` yet).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): scaffold WeatherPainter with SceneCtx and blob primitive"
```

---

## Task 5: Port PARTLY (sculpted cumulus in open sky)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add cumulusField + cumulus, inside the WeatherPainter object**

Ported from `cumulusField`/`cumulus` in `design/widget-weather-fable.html`. `k` is intensity 0..1; more clouds appear as intensity rises (2..5 of the 5 fixed positions). Each cloud's lit side faces the sun. Add this block just above the closing `}` of the `WeatherPainter` object:

```kotlin
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
```

Note: `blob` here calls the private RadialGradient shader directly at `(lx,ly,lr)` rather than via the translate/scale unit-circle helper, since the lit-lobe gradient center is offset from the lobe's own center (`sunDir` shift) - a plain `RadialGradient` + `drawCircle` is simpler than fighting the `blob` helper's assumption that the gradient is centered.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port PARTLY cumulus compositor"
```

---

## Task 6: Port OVERCAST (the only full ceiling)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add paintOvercast**

Ported from `overcastCeiling`. Uses a seeded `Random(31)` for the lumpy cloud-base texture, matching the existing file's `Random(seed)` convention (used for `stars`/`noise`) rather than porting the JS `mulberry32` bit-for-bit - the visual composition is what was validated, not the exact PRNG stream.

```kotlin
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port OVERCAST ceiling compositor"
```

---

## Task 7: Port FOG (luminous low banks, progressive dissolve)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add fogColor, softDisc, paintFogMid, paintFogFront**

Ported from `fogColor`/`softDisc`/`fogMid`/`fogFront`. `softDisc` is also reused by HAZE (Task 11) and by the fog condition-layer dispatch (Task 13), so it is `internal` rather than `private`. Note: the mid-ridge "emerging ridge between the banks" call in the HTML source used the *raw* hour palette for one `ridge()` call by inconsistency; this port intentionally uses the weather-tinted palette everywhere for consistency (a deliberate small improvement, not a behavior regression - see the design spec's "Risks" section).

```kotlin
    internal fun fogColor(p: PosterRenderer.Palette, night: Float): Int =
        PosterRenderer.lerp(
            PosterRenderer.lerp(PosterRenderer.c("#eef0f3"), p.hz, .10f),
            PosterRenderer.c("#6d7994"),
            night * .68f,
        )

    /** Diffuse light source with no crisp rim - used by FOG and HAZE instead of the sharp disc. */
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port FOG compositor and shared softDisc"
```

---

## Task 8: Port RAIN (nimbostratus + ragged scud + wet sheen)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add paintRainDeck + wetSheen**

Ported from `rainDeck`/`wetSheen`. `wetSheen` is reused by STORM (Task 9) with `storm = true`, which cools and dims it (fixes the "phantom warm band" issue found and corrected in the HTML prototype).

```kotlin
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

    /** Wet-ground reflection band. [storm] cools + dims it - RAIN gets a warmer, brighter sheen. */
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port RAIN deck and wet-sheen compositors"
```

---

## Task 9: Port STORM (fused cumulonimbus shelf + bolt)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add paintStormDeck**

Ported from the final, corrected `stormDeck` (the version with the fused-Path shelf, shallow uniform scallops, soft under-base murk, and attached fractus - not the earlier stacked-circle-towers draft). The rect + repeated circles in one `Path` with the default `Path.FillType.WINDING` reproduces the canvas nonzero-winding union the HTML relied on.

```kotlin
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
```

- [ ] **Step 2: Add lightning (the baked bolt)**

Ported from `lightning`. Fixed `seed = 7`, `intensity = .85f` - one stable, always-visible bolt per repaint, since there is no animation loop to fade it in.

```kotlin
    private fun strokePts(cv: Canvas, pts: List<FloatArray>, paint: Paint) {
        val path = Path().apply {
            moveTo(pts[0][0], pts[0][1])
            for (i in 1 until pts.size) lineTo(pts[i][0], pts[i][1])
        }
        cv.drawPath(path, paint)
    }

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
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port STORM shelf and baked lightning bolt"
```

---

## Task 10: Port SNOW (bright sky + white ground + bokeh flakes)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add paintSnowSky + paintSnowBlanket**

Ported from `snowSky`/`snowBlanket`. `snowBlanket` clips to `PosterRenderer.nearRidgePath` (extracted in Task 3) so the white dusting hugs the exact same silhouette the near ridge already paints.

```kotlin
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port SNOW sky and ground-blanket compositors"
```

---

## Task 11: Port HAZE (warm, distance-graded, never grey)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add hazeWarm + paintHazeAtmos**

Ported from `hazeWarm`/`hazeAtmos`. `hazeWarm` is `internal` since it's also needed by the sky-tinting dispatch in Task 13. Reuses `softDisc` from Task 7 for the diffuse warm sun.

```kotlin
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port HAZE atmosphere compositor"
```

---

## Task 12: Port precipitation (rain / snow fall, static bake)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`

- [ ] **Step 1: Add paintRainFall + paintSnowFall**

Ported from `rainFall`/`snowFall`, with the animation `phase` fixed to `STATIC_PHASE` (the constant already declared in Task 4) - the exact single-frame equivalent of the HTML gallery cards.

```kotlin
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
        val windX = kotlin.math.sin(wind)
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
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt
git commit -m "feat(widget): port static rain and snow precipitation"
```

---

## Task 13: Wire it all into PosterRenderer.render() + smoke test

This is where every piece from Tasks 4-12 gets dispatched per condition, in the exact layering order validated in `design/widget-weather-fable.html`: tint sky -> optional darken (rain/storm) -> horizon glow (scaled by glowMul) -> night sky (scaled by starMul) -> sun/moon glow (scaled by glowMul) -> god-rays (CLEAR/PARTLY only) -> far ridge (weather-tinted) -> condition layer -> near ridge (weather-tinted) -> ground treatment -> precip -> scrim (scaled by scrimMul) -> grain.

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt`
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt`
- Test: `app/src/test/java/com/dwell/app/widget/poster/PosterRendererWeatherTest.kt`

- [ ] **Step 1: Write the failing smoke test**

```kotlin
package com.dwell.app.widget.poster

import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

class PosterRendererWeatherTest {

    private val hours = listOf(7f, 12f, 17.6f, 22f) // dawn, noon, golden hour, night

    @Test
    fun `every condition renders at every hour without throwing`() {
        WeatherCondition.entries.forEach { condition ->
            hours.forEach { hour ->
                val weather = PosterWeather(condition, 70)
                val bmp = PosterRenderer.render(64, 64, hour, weather)
                assertEquals(64, bmp.width)
                assertEquals(64, bmp.height)
            }
        }
    }

    @Test
    fun `clear weather is the default and matches the no-weather call`() {
        val hour = 15f
        val withDefaultParam = PosterRenderer.render(48, 48, hour)
        val withExplicitClear = PosterRenderer.render(48, 48, hour, PosterWeather(WeatherCondition.CLEAR, 70))
        val sampleX = 24
        val sampleY = 24
        assertEquals(
            withDefaultParam.getPixel(sampleX, sampleY),
            withExplicitClear.getPixel(sampleX, sampleY),
        )
    }

    @Test
    fun `zero and full intensity both render without throwing`() {
        listOf(WeatherCondition.RAIN, WeatherCondition.STORM, WeatherCondition.SNOW).forEach { condition ->
            PosterRenderer.render(64, 64, 12f, PosterWeather(condition, 0))
            PosterRenderer.render(64, 64, 12f, PosterWeather(condition, 100))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.widget.poster.PosterRendererWeatherTest" -q`
Expected: FAIL - `render()` doesn't accept a `PosterWeather` parameter yet.

- [ ] **Step 3: Add the per-condition dispatch functions to WeatherPainter**

Append to `PosterWeatherPainter.kt`, inside the `WeatherPainter` object:

```kotlin
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

    /** Extra darkening overlay strength - only RAIN and STORM. */
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

    /** Weather-tinted far ridge color - prevents a phantom warm band from the raw palette. */
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

    /** Layer 6 - the condition-specific sky content + light source. Dispatches to the per-condition paint functions from Tasks 5-11. */
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

    /** Layer 9b - per-condition ground treatment, painted right after the near ridge. */
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

    /** Layer 8 - static-baked precipitation. */
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
```

- [ ] **Step 4: Expose body/ridge from PosterRenderer for WeatherPainter to call**

`ridge` is already being widened; `body` needs the same treatment (it's the sun/moon disc drawer). In `PosterRenderer.kt`, rename `private fun body(...)` to `internal fun bodyPublic(cv: Canvas, cx: Float, cy: Float, hF: Float, isDay: Boolean, p: Palette)` (keep the function body unchanged - only the visibility + name change, matching the calls added in Step 3 above; keep the existing internal call site inside `render()`'s own pipeline consistent with the new name if `render()` still calls it directly anywhere - see Step 5, where the direct call is replaced entirely by the `paintConditionLayer` dispatch). Also widen `private fun ridge(...)` to `internal fun ridge(...)` (visibility only, signature unchanged).

- [ ] **Step 5: Thread weather through PosterRenderer.render()**

Add these imports to the top of `PosterRenderer.kt`:

```kotlin
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
```

Replace the body of `render()` in `PosterRenderer.kt` with the weather-aware pipeline:

```kotlin
    fun render(w: Int, h: Int, hour: Float, weather: PosterWeather = PosterWeather.Default): Bitmap {
        val ww = w.coerceAtLeast(1)
        val hh = h.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(ww, hh, Bitmap.Config.RGB_565)
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

        // 1. SKY - weather-tinted 5-stop vertical gradient.
        paint.shader = LinearGradient(
            0f, 0f, 0f, hF,
            weatherSky,
            floatArrayOf(0f, .28f, .5f, .72f, 1f),
            Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, wF, hF, paint)

        // 1b. DARKEN - rain/storm only.
        if (darken > 0f) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, hF,
                intArrayOf(withAlpha(c("#0b0e15"), a255(darken)), withAlpha(c("#0b0e15"), a255(darken * .75f)), withAlpha(c("#0b0e15"), a255(darken * .35f))),
                floatArrayOf(0f, .6f, 1f),
                Shader.TileMode.CLAMP,
            )
            cv.drawRect(0f, 0f, wF, hF, paint)
        }

        // 2. HORIZON GLOW - scaled by glowMul.
        paint.shader = RadialGradient(
            hgX, horizonY, wF * .95f,
            intArrayOf(withAlpha(p.hz, a255((.5f * p.glow + .22f) * glowMul)), withAlpha(p.hz, a255(.16f * glowMul)), withAlpha(p.hz, 0)),
            floatArrayOf(0f, .4f, 1f),
            Shader.TileMode.CLAMP,
        )
        cv.drawRect(0f, 0f, wF, horizonY + 2f, paint)

        // 3. NIGHT SKY - scaled by starMul.
        if (night * starMul > .05f) {
            milkyWay(cv, wF, hF, night * starMul)
            stars(cv, wF, horizonY, night * starMul)
        }

        // 4. SUN / MOON GLOW - scaled by glowMul.
        if (glowMul > .04f) {
            paint.shader = RadialGradient(
                cx, cy, hF * .98f,
                intArrayOf(withAlpha(p.sun, a255(.9f * p.glow * glowMul)), withAlpha(p.sun, a255(.42f * p.glow * glowMul)), withAlpha(p.sun, a255(.1f * p.glow * glowMul)), withAlpha(p.sun, 0)),
                floatArrayOf(0f, .16f, .5f, 1f),
                Shader.TileMode.CLAMP,
            )
            cv.drawRect(0f, 0f, wF, hF, paint)
        }

        // 5. GOD-RAYS - CLEAR and PARTLY only.
        if (p.glow > .82f && sp.second > .30f && (weather.condition == WeatherCondition.CLEAR || weather.condition == WeatherCondition.PARTLY)) {
            val strength = (p.glow - .82f) * 3.2f * (if (weather.condition == WeatherCondition.PARTLY) .6f else 1f)
            godRays(cv, hF, cx, cy, p.sun, strength)
        }

        // 6. FAR RIDGE - weather-tinted.
        val baseFar = lerp(p.hillFar, p.sky[4], .12f)
        val farColor = WeatherPainter.farTint(p, baseFar, weather, night)
        ridge(cv, wF, hF, horizonY, .62f, farColor, p, far = true)

        // 7. CONDITION LAYER - the per-weather sky content + light source (Tasks 5-11).
        WeatherPainter.paintConditionLayer(cv, sceneCtx, weather, p.hillNear)

        // 8. NEAR RIDGE - weather-tinted; the text bed.
        val nearColor = WeatherPainter.nearTint(p, p.hillNear, weather, night)
        ridge(cv, wF, hF, horizonY + hF * .11f, .96f, nearColor, p, far = false)

        // 8b. GROUND TREATMENT - snow blanket / wet sheen / fog front.
        WeatherPainter.paintGroundTreatment(cv, sceneCtx, weather)

        // 9. PRECIP - static-baked rain/snow/lightning.
        WeatherPainter.paintPrecip(cv, sceneCtx, weather)

        // 10. BOTTOM SCRIM - scaled by scrimMul.
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
```

This fully replaces the previous `render()` body (which called `haze(...)` and `body(...)` directly for every hour regardless of weather) - those two calls now happen only inside `WeatherPainter.paintConditionLayer`'s CLEAR/PARTLY branches, which is exactly equivalent for CLEAR (verified by Task 13 Step 6's regression test) since `haze(..., mul = 1f)` and `bodyPublic(...)` reproduce the old unconditional calls exactly.

- [ ] **Step 6: Run the smoke test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.widget.poster.PosterRendererWeatherTest" -q`
Expected: PASS (3 tests). If the "clear weather matches the no-weather call" test fails, the CLEAR branch of `paintConditionLayer`/`weatherSky`/`farTint`/`nearTint`/`scrimMul` diverged from the original code path - re-check each against the pre-Task-13 `render()` body (they must be no-ops for CLEAR).

- [ ] **Step 7: Run the full existing test suite to check for regressions**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS - every previously-passing test (`WallpaperMatchTest`, `WidgetConfigFakes`-based tests, `WidgetStyleTest`, etc.) still passes.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt app/src/test/java/com/dwell/app/widget/poster/PosterRendererWeatherTest.kt
git commit -m "feat(widget): wire weather compositors into PosterRenderer.render"
```

---

## Task 14: PosterClockWidgetProvider reads the stored weather

**Files:**
- Modify: `app/src/main/java/com/dwell/app/widget/poster/PosterClockWidgetProvider.kt`

- [ ] **Step 1: Add Hilt + inject the store, read it before rendering**

Full new file contents:

```kotlin
package com.dwell.app.widget.poster

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.dwell.app.R
import com.dwell.app.data.widget.PosterWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * The poster clock: a full-bleed painted scene ([PosterRenderer]) whose palette tracks the
 * hour - and, when the owner has chosen one via [com.dwell.app.ui.widgetconfig.PosterWeatherConfigActivity],
 * a weather layer on top - with live TextClock time + date stacked over it. The scene is
 * repainted on update (and on resize / the ~30-min period in poster_clock_widget_info.xml).
 */
@AndroidEntryPoint
class PosterClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var weatherStore: PosterWeatherStore

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ids.forEach { render(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                render(context, manager, id)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        val weather = weatherStore.get(id)
        val p = PosterRenderer.paletteFor(hour)

        val dm = context.resources.displayMetrics
        val opts = manager.getAppWidgetOptions(id)
        fun px(key: String, fallbackDp: Int): Int {
            val dp = opts.getInt(key, 0).takeIf { it > 0 } ?: fallbackDp
            return (dp * dm.density).toInt()
        }
        var w = px(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
        var h = px(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160)
        val cap = 512
        if (w > cap) { h = h * cap / w; w = cap }
        if (h > cap) { w = w * cap / h; h = cap }

        try {
            val bmp = PosterRenderer.render(w, h, hour, weather)
            val views = RemoteViews(context.packageName, R.layout.widget_poster).apply {
                setImageViewBitmap(R.id.poster_bg, bmp)
                setTextColor(R.id.poster_time, p.ink)
                setTextColor(R.id.poster_date, withAlpha(p.ink, 235))
                setInt(R.id.poster_rule, "setColorFilter", withAlpha(p.ink, 110))
            }
            manager.updateAppWidget(id, views)
        } catch (t: Throwable) {
            Log.e(TAG, "render/update failed id=$id", t)
        }
    }

    private fun withAlpha(color: Int, a: Int) =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        private const val TAG = "PosterClock"

        /** Re-render a single poster widget after its weather config changes. */
        fun refresh(context: Context, id: Int) {
            val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
            val intent = Intent(context, PosterClockWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            }
            context.sendBroadcast(intent)
        }
    }
}
```

Note the switch from a plain synchronous read to `goAsync()` + a background coroutine in both `onUpdate` and `onAppWidgetOptionsChanged` - the DataStore read is now suspending, and `AppWidgetProvider` callbacks run on the main thread, so skipping `goAsync()` would risk ANR the way `ClockWidgetProvider.onUpdate` already avoids it.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterClockWidgetProvider.kt
git commit -m "feat(widget): read stored weather in PosterClockWidgetProvider"
```

---

## Task 15: PosterPinReceiver

**Files:**
- Create: `app/src/main/java/com/dwell/app/widget/poster/PosterPinReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the receiver**

Mirrors `WidgetPinReceiver` exactly, swapped to the poster provider + weather store.

```kotlin
package com.dwell.app.widget.poster

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.PosterWeatherStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the successful-pin callback from [AppWidgetManager.requestPinAppWidget] and
 * applies the weather condition the user chose in [com.dwell.app.ui.widgetconfig.PosterWeatherConfigActivity]
 * to the freshly-placed poster widget.
 */
@AndroidEntryPoint
class PosterPinReceiver : BroadcastReceiver() {

    @Inject lateinit var store: PosterWeatherStore
    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val weather = PosterWeather.decode(intent.getStringExtra(EXTRA_WEATHER).orEmpty())
        val pending = goAsync()
        scope.launch {
            try {
                store.save(id, weather)
                PosterClockWidgetProvider.refresh(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_WEATHER = "com.dwell.app.EXTRA_POSTER_WEATHER"
    }
}
```

- [ ] **Step 2: Register the receiver in the manifest**

Add next to the existing `.widget.clock.WidgetPinReceiver` entry in `app/src/main/AndroidManifest.xml`:

```xml
        <receiver
            android:name=".widget.poster.PosterPinReceiver"
            android:exported="false" />
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/widget/poster/PosterPinReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(widget): add PosterPinReceiver to bake chosen weather into a new pin"
```

---

## Task 16: PosterWeatherConfigViewModel

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigViewModel.kt`

- [ ] **Step 1: Write the ViewModel**

Gallery-mode only (no `android:configure`, so there is no existing appWidgetId to load - see the design spec's config-surface section for why).

```kotlin
package com.dwell.app.ui.widgetconfig

import androidx.lifecycle.ViewModel
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PosterWeatherConfigViewModel @Inject constructor() : ViewModel() {

    private val _draft = MutableStateFlow(PosterWeather.Default)
    val draft: StateFlow<PosterWeather> = _draft.asStateFlow()

    fun setCondition(condition: WeatherCondition) = _draft.update { it.copy(condition = condition) }

    fun setIntensity(intensity: Int) = _draft.update { it.copy(intensity = intensity).coerced() }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigViewModel.kt
git commit -m "feat(widget): add PosterWeatherConfigViewModel"
```

---

## Task 17: PosterWeatherConfigScreen

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigScreen.kt`

- [ ] **Step 1: Write the screen**

Live preview calls the real `PosterRenderer` so what's shown is exactly what ships (matches the design spec's requirement). 8 pills in two rows of 4; intensity `Slider` hidden for CLEAR.

```kotlin
package com.dwell.app.ui.widgetconfig

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dwell.app.data.widget.PosterWeather
import com.dwell.app.data.widget.WeatherCondition
import com.dwell.app.ui.components.DwellPrimaryButton
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.theme.AccentFill
import com.dwell.app.ui.theme.DwellSpacing
import com.dwell.app.widget.poster.PosterRenderer
import java.util.Calendar

private const val PREVIEW_WIDTH = 320
private const val PREVIEW_HEIGHT = 190
private const val POSTER_ASPECT = 357f / 210f

@Composable
fun PosterWeatherConfigScreen(
    weather: PosterWeather,
    onSelectCondition: (WeatherCondition) -> Unit,
    onIntensity: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DwellScaffold(modifier = modifier, applyStatusBarPadding = true, applyNavBarPadding = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DwellSpacing.screenGutter, vertical = DwellSpacing.xl),
        ) {
            Text(
                text = "Weather",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(DwellSpacing.lg))

            LivePreview(weather)
            Spacer(Modifier.height(DwellSpacing.xl))

            SectionLabel("Condition")
            Spacer(Modifier.height(DwellSpacing.md))
            val row1 = WeatherCondition.entries.take(4)
            val row2 = WeatherCondition.entries.drop(4)
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                row1.forEach { ConditionPill(it, weather.condition == it, Modifier.weight(1f), onSelectCondition) }
            }
            Spacer(Modifier.height(DwellSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(DwellSpacing.sm)) {
                row2.forEach { ConditionPill(it, weather.condition == it, Modifier.weight(1f), onSelectCondition) }
            }

            if (weather.condition != WeatherCondition.CLEAR) {
                Spacer(Modifier.height(DwellSpacing.xl))
                SectionLabel(intensityLabel(weather.intensity))
                Spacer(Modifier.height(DwellSpacing.sm))
                Slider(
                    value = weather.intensity.toFloat(),
                    onValueChange = { onIntensity(it.toInt()) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = AccentFill, activeTrackColor = AccentFill),
                )
            }

            Spacer(Modifier.height(DwellSpacing.section))
            DwellPrimaryButton(text = "Add widget", onClick = onAdd)
        }
    }
}

private fun intensityLabel(intensity: Int): String {
    val word = when {
        intensity < 40 -> "LIGHT"
        intensity > 85 -> "HEAVY"
        else -> "MODERATE"
    }
    return "INTENSITY - $word ($intensity%)"
}

@Composable
private fun LivePreview(weather: PosterWeather) {
    val hour = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    }
    val bitmap = remember(weather) {
        PosterRenderer.render(PREVIEW_WIDTH, PREVIEW_HEIGHT, hour, weather).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(POSTER_ASPECT)
            .clip(RoundedCornerShape(26.dp)),
    )
}

@Composable
private fun ConditionPill(
    condition: WeatherCondition,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (WeatherCondition) -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentFill else Color(0xFF1B1815))
            .border(1.dp, if (selected) AccentFill else Color(0x14ECE7DD), RoundedCornerShape(14.dp))
            .clickable { onClick(condition) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = condition.label,
            color = if (selected) Color(0xFF12100E) else Color(0xFFA89F8E),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp,
    )
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. If `AccentFill` isn't found at that import path, check `app/src/main/java/com/dwell/app/ui/theme/Color.kt` for its actual declaration location and fix the import - it is already used the same way in `app/src/main/java/com/dwell/app/ui/components/Buttons.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigScreen.kt
git commit -m "feat(widget): add PosterWeatherConfigScreen with live renderer preview"
```

---

## Task 18: PosterWeatherConfigActivity + manifest + WidgetsScreen wiring

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt`

- [ ] **Step 1: Write the activity**

Gallery-mode only - always pins a new widget, never reconfigures (there is no `android:configure` declared on the provider, per the design spec).

```kotlin
package com.dwell.app.ui.widgetconfig

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.ui.theme.DwellTheme
import com.dwell.app.widget.poster.PosterClockWidgetProvider
import com.dwell.app.widget.poster.PosterPinReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PosterWeatherConfigActivity : ComponentActivity() {

    private val viewModel: PosterWeatherConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DwellTheme {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                PosterWeatherConfigScreen(
                    weather = draft,
                    onSelectCondition = viewModel::setCondition,
                    onIntensity = viewModel::setIntensity,
                    onAdd = ::pinWidget,
                )
            }
        }
    }

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(this, PosterClockWidgetProvider::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            val callback = Intent(this, PosterPinReceiver::class.java)
                .putExtra(PosterPinReceiver.EXTRA_WEATHER, viewModel.draft.value.encode())
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                this, 0, callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            manager.requestPinAppWidget(provider, null, pending)
        }
        finish()
    }
}
```

- [ ] **Step 2: Register the activity in the manifest**

Add after the existing `.ui.widgetconfig.WidgetConfigActivity` entry in `app/src/main/AndroidManifest.xml`:

```xml
        <activity
            android:name=".ui.widgetconfig.PosterWeatherConfigActivity"
            android:exported="false"
            android:theme="@style/Theme.Dwell" />
```

(`exported="false"` - unlike `WidgetConfigActivity`, this one has no `APPWIDGET_CONFIGURE` intent-filter, since it's gallery-mode-only and reached exclusively from inside the app.)

- [ ] **Step 3: Wire WidgetsScreen's CLOCK tap to the new activity**

Replace the full contents of `app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt`:

```kotlin
package com.dwell.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dwell.app.data.widget.CatalogWidget
import com.dwell.app.ui.components.DwellScaffold
import com.dwell.app.ui.screens.widgets.WidgetGallery
import com.dwell.app.ui.widgetconfig.PosterWeatherConfigActivity
import com.dwell.app.widget.date.DateWidgetProvider

@Composable
fun WidgetsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DwellScaffold(modifier = modifier) {
        WidgetGallery(
            onSelect = { widget ->
                when (widget) {
                    // Clock is the poster: opens the weather picker, which pins on "Add widget".
                    CatalogWidget.CLOCK -> context.startActivity(Intent(context, PosterWeatherConfigActivity::class.java))
                    // Date has no styling yet - pin it straight to the home screen.
                    CatalogWidget.DATE -> pinWidget(context, DateWidgetProvider::class.java)
                    else -> Unit // Soon cards aren't tappable.
                }
            },
        )
    }
}

/** Ask the launcher to pin a widget for [provider]; no-op on launchers that don't support it. */
private fun pinWidget(context: Context, provider: Class<*>) {
    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
```

- [ ] **Step 4: Compile the whole app**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full test suite one more time**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS - all tests, including the new `PosterWeatherTest` and `PosterRendererWeatherTest`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/widgetconfig/PosterWeatherConfigActivity.kt app/src/main/AndroidManifest.xml app/src/main/java/com/dwell/app/ui/screens/WidgetsScreen.kt
git commit -m "feat(widget): open the weather picker from the Widgets tab before pinning"
```

---

## Task 19: Manual verification on device

**Files:** none (verification only - this project verifies on a real device over adb, not an emulator).

- [ ] **Step 1: Install the debug build**

Run: `./gradlew :app:installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 2: Walk the flow on-device**

1. Open Dwell -> Widgets tab -> tap "Clock". The new weather picker opens (not a direct pin).
2. Pick each of the 8 conditions in turn; confirm the live preview visibly changes and no two conditions look alike (this is the acceptance bar from the original design work in `design/widget-weather-fable.html`).
3. Drag the intensity slider for RAIN or SNOW; confirm the preview's precipitation density visibly scales, and the LIGHT/MODERATE/HEAVY label tracks it.
4. Pick STORM, tap "Add widget", confirm the pin flow completes and the placed widget shows a dark shelf + a visible lightning bolt + rain streaks.
5. Repeat with CLEAR - confirm the placed widget looks exactly like today's poster clock (no regression).
6. Force-touch/long-press the placed weather widget - confirm there is no "Edit" option (expected: no `android:configure` declared, matches the Date widget's current behavior, not a bug).

- [ ] **Step 3: Report results**

No code changes in this task - if a condition doesn't read correctly on-device (e.g. legibility issue at real widget size vs. the 320x190 preview), open a follow-up task rather than patching silently, since the compositors were tuned against the HTML prototype's viewport and real widget cells vary in aspect ratio.

---

## Plan Self-Review Notes

- **Spec coverage:** Data model (Task 1), persistence (Task 2), renderer port (Tasks 3-13), provider wiring (Task 14), config surface incl. pin receiver (Tasks 15-18), tests (Tasks 1, 13) - every section of `docs/superpowers/specs/2026-07-02-poster-clock-weather-design.md` has a task. The two explicit non-goals (temp/condition text overlay, live data) have no task, correctly.
- **Type consistency:** `WeatherCondition`/`PosterWeather` names, `PosterWeather.encode()/decode()/coerced()`, `PosterWeatherStore.get/save/observe/clear`, `WeatherPainter.SceneCtx` fields, and `PosterRenderer.render(w, h, hour, weather)` are used identically across every task that references them.
- **CLEAR-path safety net:** Task 13's second smoke test (`clear weather is the default and matches the no-weather call`) is the regression guard for "existing placed widgets are unaffected" from the spec's goals - it will fail loudly if any per-condition helper accidentally touches the CLEAR path.
