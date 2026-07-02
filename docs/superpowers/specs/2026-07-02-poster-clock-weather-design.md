# Poster clock weather layer — design

## Context

The poster Clock widget ([PosterRenderer.kt](../../../app/src/main/java/com/dwell/app/widget/poster/PosterRenderer.kt)) paints a time-of-day landscape (dawn → noon → golden hour → night) into one bitmap; time/date ride on top as live `TextClock` views. It has no weather layer today and no configure screen at all — `WidgetsScreen` pins it straight to the home screen with `requestPinAppWidget`.

A weather-reactive scene engine was prototyped and iterated in `design/widget-weather-fable.html` (8 conditions, each with a bespoke compositor so no two look alike — clear/partly/overcast/fog/rain/storm/snow/haze — always composited over the existing hour palette, never replacing it). This spec ports that engine into the real Android widget, driven by a **manually chosen** condition (no live weather data source in this slice — see "Explicitly out of scope").

Separately, `CatalogWidget.WEATHER` is an unrelated, still-SOON square catalog card (temp-only, no clock face). It is not touched by this work.

## Goals

- The poster Clock widget can show one of 8 weather conditions, each instantly distinguishable, always composited over the correct hour's palette (a rainy dawn still looks like dawn).
- Existing placed widgets are unaffected until their owner opts in (default = CLEAR = byte-identical to today's render).
- A small config surface lets the user pick condition + intensity, reached the same way the app already handles the (separate) text-clock's style picker — baked into the pin, no OS-forced popup.

## Non-goals / explicitly out of scope

- No live weather data (no Open-Meteo, no location permission, no network, no background refresh).
- No temperature or condition-name text overlay on the widget face. A hand-typed number would go stale forever on a permanent home-screen widget; this is a natural fast-follow once live data lands.
- No animation. RemoteViews can't animate; each repaint bakes one representative static frame (matches the caveat already documented in the HTML prototype).
- `CatalogWidget.WEATHER` (the separate temp-only square card) stays SOON — untouched.
- The old text-only `ClockWidgetProvider` / `WidgetConfigScreen` / `WidgetStyleStore` (color/size/opacity presets) are untouched.
- The catalog gallery's `ClockPreview` card (golden-hour static drawable) is untouched — it represents the catalog browsing card, not any placed instance's chosen weather.
- No `android:configure` declaration on the widget — meaning no long-press "Edit" after placement (matches the Date widget's current state; not a regression).

## Architecture

### 1. Data model + persistence

New file `app/src/main/java/com/dwell/app/data/widget/PosterWeather.kt`:

```kotlin
enum class WeatherCondition(val label: String) {
    CLEAR("Clear"), PARTLY("Partly cloudy"), OVERCAST("Overcast"), FOG("Fog"),
    RAIN("Rain"), STORM("Thunderstorm"), SNOW("Snow"), HAZE("Haze"),
}

data class PosterWeather(
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val intensity: Int = 70, // 0..100; ignored (but still stored) when condition == CLEAR
) {
    fun coerced(): PosterWeather = copy(intensity = intensity.coerceIn(0, 100))
    fun encode(): String = "${condition.name}|$intensity"
    companion object {
        val Default = PosterWeather()
        fun decode(raw: String): PosterWeather = runCatching {
            val p = raw.split("|").also { require(it.size == 2) }
            PosterWeather(WeatherCondition.valueOf(p[0]), p[1].toInt()).coerced()
        }.getOrDefault(Default)
    }
}
```

New `PosterWeatherStore` interface + `DataStorePosterWeatherStore` impl in the same package, keyed per appWidgetId — structurally identical to `WidgetStyleStore` / `DataStoreWidgetStyleStore` (`observe`, `get`, `save`, `clear`; DataStore key `"poster_weather_$id"`). Bound in the existing DI module alongside the style store.

### 2. Renderer port

New file `app/src/main/java/com/dwell/app/widget/poster/PosterWeatherPainter.kt` (kept separate from `PosterRenderer.kt` to avoid ballooning that file past ~250 lines). Internal `object WeatherPainter` holding the ported compositors, one function group per condition, faithful 1:1 port of `design/widget-weather-fable.html`'s JS onto `android.graphics.Canvas`, using the exact primitive mapping already documented in `PosterRenderer.kt`'s header (`LinearGradient`/`RadialGradient`/`Path`+`drawPath`/`BitmapShader` REPEAT/`PorterDuffXfermode.ADD` for "lighter"):

- `cumulusField` / `cumulus` (partly)
- `overcastCeiling` (overcast)
- `fogMid` / `fogFront` (fog)
- `rainDeck` / `wetSheen` (rain)
- `stormDeck` (storm shelf + fractus + interior glow) + `lightning` (baked bolt, always-on for the static frame)
- `snowSky` / `snowBlanket` (snow)
- `hazeAtmos` (haze)
- `rainFall` / `snowFall` (precip, baked at a fixed phase — no animation)
- shared helpers: `blob` (elliptical radial gradient via translate+scale+drawCircle), `ridge` tint override, `softDisc` (diffuse fog/haze sun)

`PosterRenderer.render(w, h, hour, weather: PosterWeather = PosterWeather.Clear)` gains the new parameter. When `weather.condition == CLEAR`, the render path is byte-identical to today's code (no behavior change for existing/default widgets). For other conditions, `WeatherPainter` is invoked between existing pipeline steps (far ridge → **condition layer + disc** → near ridge **+ per-condition ground treatment** → precip → scrim), matching the layering already validated visually in the HTML prototype.

### 3. Provider wiring

`PosterClockWidgetProvider.render()` reads `PosterWeatherStore.get(id)` (needs Hilt injection — the provider currently isn't `@AndroidEntryPoint`; add it, mirroring `ClockWidgetProvider`) and passes the result into `PosterRenderer.render(...)`. No changes to `widget_poster.xml` — no new overlay views.

### 4. Config surface

New, minimal — mirrors the existing `WidgetConfigActivity` / `WidgetConfigViewModel` / `WidgetConfigScreen` triad used by the old text clock, slimmed to just this feature (no presets, no paywall, no wallpaper-match):

- `PosterWeatherConfigActivity` (`ComponentActivity`, `@AndroidEntryPoint`): gallery-mode only (no appWidgetId to reconfigure, since there's no `android:configure`). On confirm, calls `requestPinAppWidget` with a `PosterPinReceiver` callback carrying the chosen `PosterWeather.encode()`.
- `PosterWeatherConfigViewModel`: holds draft `PosterWeather`, `setCondition`, `setIntensity`.
- `PosterWeatherConfigScreen`: title, live preview (calls the real `PosterRenderer.render` at the current hour with the draft weather — so what you see is exactly what ships), 8 condition pills, intensity slider (disabled/hidden when CLEAR is selected), "Add widget" button.
- `PosterPinReceiver` (mirrors `WidgetPinReceiver`): on the pin broadcast, saves the carried `PosterWeather` into `PosterWeatherStore` for the new appWidgetId, then triggers a provider refresh.
- `WidgetsScreen`: `CatalogWidget.CLOCK`'s `onSelect` now launches `PosterWeatherConfigActivity` instead of calling `pinWidget` directly.

### 5. Tests

`PosterWeatherTest.kt` (mirrors `WallpaperMatchTest.kt` conventions):
- `PosterWeather.encode()`/`decode()` round-trips for all 8 conditions × a few intensities.
- `decode()` falls back to `Default` on garbage input (matches `WidgetStyle.decode`'s `runCatching`).
- Smoke test: `PosterRenderer.render(w, h, hour, weather)` does not throw, for all 8 conditions × {dawn, noon, golden hour, night} hours, at a small bitmap size.

## Risks / open questions carried into the plan

- Exact layering order per condition (far ridge tint, disc visibility, near-ridge ground treatment) must match the visually-verified HTML — the implementation plan should port condition-by-condition and screenshot/compare against the corresponding `design/widget-weather-fable.html` gallery card rather than porting blind.
- `PosterClockWidgetProvider` isn't currently Hilt-enabled; adding `@AndroidEntryPoint` + constructor injection needs the same `goAsync()` pattern `ClockWidgetProvider` uses for the suspend read, to avoid ANR risk on the DataStore read.
