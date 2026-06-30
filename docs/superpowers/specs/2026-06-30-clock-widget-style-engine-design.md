# Clock Widget + Style Engine — Design Spec

**Date:** 2026-06-30
**Status:** Approved (brainstorming)
**Phase:** 4 (Widgets) — first slice

## Goal

Ship one App Widget — a clock — fully end-to-end, plus the **paid style engine** that gates customization behind the `premium` entitlement. This single slice proves the whole widget stack (renderer + config Activity + per-instance persistence + premium gating + live preview + in-app add-to-home). Calendar, notes, and battery become replicas afterward; they reuse every shared piece.

## Monetization (updates TRD §5 / §8)

The TRD currently makes per-widget color+font customization **free**. This slice changes that:

- **Free:** the clock widget, fully functional, in one tasteful default Dwell style.
- **Paid (`premium`):** the style engine — font, color, opacity, corner radius, size variant. (Presets + wallpaper-matching are premium too, added once the engine exists.)
- The config Activity reads `EntitlementRepository.observePremium()` (already shipped). Non-premium users see the style controls **locked** with an "Unlock Dwell" CTA routing to the existing purchase flow.

This makes the `premium` gate (already merged) carry real depth, and keeps the free tier genuinely good (function is free; only look-tweaking is paid).

The `premium` unlock is one-time and permanent, so there is no entitlement-lapse case to handle: a user who customizes stays customized.

## Renderer decision: clock = RemoteViews + `TextClock`

A live clock cannot tick efficiently in Glance — the AppWidget periodic-update floor is ~30 minutes, far too slow for a clock. Android's `TextClock` view self-updates (system-driven, battery-free). So the **clock** renders as a RemoteViews widget wrapping a `TextClock` — the TRD's documented "RemoteViews fallback where Glance can't express it cleanly" case.

Crucially, the **style engine, config Activity, persistence, and preview are renderer-agnostic**. The clock's RemoteViews renderer consumes a `WidgetStyle`; the later Glance widgets (battery, notes — state-driven, no constant tick) consume the same `WidgetStyle` through a Glance renderer. Building the clock first therefore proves the shared infra is not Glance-coupled.

## Components (each one responsibility)

1. **`WidgetStyle`** (`data/widget/WidgetStyle.kt`) — immutable model: `fontKey`, `colorKey`, `opacity` (0–100), `cornerRadiusDp`, `sizeVariant` (SMALL/MEDIUM/LARGE), plus a `WidgetStyle.Default`. Maps to concrete values via the locked design tokens. Pure Kotlin → unit-tested.

2. **`WidgetStyleStore`** (`data/widget/WidgetStyleStore.kt`) — persists a `WidgetStyle` per `appWidgetId` in **DataStore (Preferences)**. `observe(id): Flow<WidgetStyle>`, `save(id, style)`, `clear(id)`. Adds the DataStore dependency (the TRD already anticipated DataStore for widget config). Unit-tested against a temp DataStore.

3. **`ClockWidgetProvider`** (`widget/clock/ClockWidgetProvider.kt`) — an `AppWidgetProvider` that builds RemoteViews from the saved `WidgetStyle`: a `TextClock` (time) + `TextClock` (date) styled by color/font/size/opacity, on a rounded background. Reads the style synchronously in `onUpdate` (cached) and refreshes when config changes.

4. **`WidgetStyleResolver`** (`widget/WidgetStyleResolver.kt`) — maps `WidgetStyle` keys → concrete RemoteViews attributes (ARGB color from token + opacity, text size from `sizeVariant`, font via a bundled font or `setTextViewTextSize`/typeface). Renderer-agnostic helper. Unit-tested for the key→value mapping.

5. **`WidgetConfigActivity`** + **`WidgetConfigViewModel`** (`ui/widgetconfig/`) — a Compose screen on the app design system (`DwellScaffold` + components). Live preview at top; style controls below, enabled iff premium (else locked rows + Unlock CTA). Launched on placement (the provider's `android:configure`). On save: writes `WidgetStyleStore[appWidgetId]`, calls the provider to refresh, returns `RESULT_OK` with the `appWidgetId`. ViewModel state (free-vs-gated, current style) is unit-tested with a fake entitlement flow + fake store.

6. **Widgets tab** (`ui/screens/WidgetsScreen.kt`) — replace the placeholder with a card previewing the clock + **"Add to home screen"** via `AppWidgetManager.requestPinAppWidget()` (supported on minSdk 26) + a "Customize" entry that opens `WidgetConfigActivity`. Shows a short "coming soon" note for the other three widgets.

## Data flow

```
User places clock (long-press home) OR taps "Add to home screen" (requestPinAppWidget)
   -> system launches WidgetConfigActivity (android:configure) with appWidgetId
   -> premium?  edit style controls  :  controls locked + "Unlock Dwell" CTA -> purchase flow
   -> Save -> WidgetStyleStore.save(appWidgetId, style)
           -> ClockWidgetProvider refreshes that id from the saved style
           -> RESULT_OK(appWidgetId)
   -> TextClock self-updates time on the home screen, battery-free
```

## Dependencies added

- `androidx.datastore:datastore-preferences` — per-widget style persistence.
- `androidx.glance:glance-appwidget` — NOT needed for the clock, deferred to the battery/notes slice. (Listed here so the plan does not add it prematurely.)

## Testing

- **Unit (JVM, TDD):** `WidgetStyle` defaults + (de)serialization; `WidgetStyleStore` round-trip against a temporary DataStore; `WidgetStyleResolver` key→value mapping; `WidgetConfigViewModel` free-vs-gated state from a fake premium flow.
- **On device:** placing the widget, the config Activity, the locked→Unlock path, "Add to home screen", and that `TextClock` ticks. RemoteViews + AppWidget cannot be exercised on the JVM.

## Out of scope (deferred, each its own slice)

- Calendar, notes, battery widgets (replicas reusing this infra — next).
- Weather, quick-launch, daily-quote, and the rest of the catalog (separate subsystems; weather needs a keyed/paid API).
- Style presets + wallpaper-palette matching (premium extras, land once the engine exists).
- The home-screen launcher (Phase 6) that will compose these.

## Docs to update alongside

- TRD §5 / §8 and PRD §Monetization: widget color+font moves from free to the `premium` style engine; free users get one default style.
- `08-CLAUDE.md` product rules: note "widget customization is premium; widgets themselves are free."
