# Widget Monetization Reframe — Design Spec

**Date:** 2026-07-01
**Status:** Approved (mock greenlit — `docs/design/widget-monetization.html`)

## Problem

Today "free" = one cream clock at one size, and the paywall gates 2 extra colors + 2
extra sizes of a single widget. That is not sellable, and worse it **locks the brand's
own green accent** behind the paywall. The unlock also promises a "coordinated layer"
(wallpaper-matched presets, extra home styles) that isn't built, so the purchase has
almost nothing real to deliver.

## The reframe

Move the free/premium line from *atoms* (individual color/size toggles) to *finished
looks* + *capability*:

- **FREE** — a curated set of complete, lovable **presets**, including one that uses the
  brand green. Free is generous and finished, not a demo.
- **PREMIUM** (one-time unlock) — the open **style engine** (mix any color / size /
  opacity yourself), plus the **premium presets**. Cross-device via the existing
  `premium` entitlement. Wallpaper-matched widgets and new layouts are the roadmap the
  membership grows into (not sold as delivered in P0).

## Scope — P0 (this spec)

Ship the honest, salable slice with **no new widget rendering** (all presets are
color/size combos over the existing editorial layout) and **no wallpaper sampling**.

### Presets (render on the existing `widget_clock.xml`)

| Preset | Color | Size | Tier |
|---|---|---|---|
| Editorial | Cream | Medium | Free |
| Sage | Green | Medium | Free  ← unlocks the brand color |
| Bold | Cream | Large | Free |
| Gold | Sand | Medium | Premium |
| Quiet | Cream | Small | Premium |
| Forest | Green | Large | Premium |

Each is a named `WidgetStyle`. Curated combos are free; **arbitrary mixing is premium**
(that is what the engine sells). Every preset above renders distinctly today — no broken
promises.

### Screens

1. **Widget picker / config** (`WidgetConfigScreen`, reworked):
   - Live preview card reflects the selected preset.
   - Preset grid: free presets selectable; premium presets show a lock badge but stay
     tappable (tapping previews them — the tease).
   - A "Fine-tune it yourself" row = the open engine. Premium users get the inline
     color/size/opacity controls; free users get a locked row that routes to the paywall.
   - Dynamic CTA: free preset selected → **Add widget** (pins, as today). Premium preset
     selected while not premium → **Unlock everything** → Paywall.

2. **Paywall** (`PaywallActivity` + `PaywallScreen`, new):
   - Headline + subhead (Fraunces).
   - Honest value checklist (delivered in P0): the full style engine, every preset,
     yours across devices, one-time — no subscription.
   - A single quiet roadmap line: "Coming to members: wallpaper-matched widgets & new
     layouts." (Roadmap, visually distinct from delivered checkmarks — not a checkbox.)
   - Primary CTA with the **real Play price** (`formattedPrice()`), fallback "₹299":
     "Unlock Dwell — {price}, once". Secondary "Maybe later".
   - Reachable from the config's locked presets / locked engine row.
   - On unlock success the `premium` flag flips via the existing Firestore listener; the
     paywall observes `isPremium` and finishes back to config.

## Components & boundaries

- `data/widget/WidgetPreset.kt` — enum of presets (id, label, style, free). Pure data +
  `free`/`premium`/`of(style)` helpers. Independently testable.
- `WidgetConfigViewModel` — gains `selectPreset`, a `selected` StateFlow, `needsUnlock`,
  and a `priceLabel` StateFlow. Keeps existing engine setters for premium users.
- `BillingRepository.formattedPrice(): String?` — surfaces Play's localized price for the
  paywall. Impl reuses the existing product-details query.
- `ui/paywall/PaywallActivity`, `PaywallScreen`, `PaywallViewModel` — the pitch, the
  price, the unlock. No new billing logic; delegates to `BillingRepository`.

## Non-goals (P0)

- Wallpaper-matched palettes (P2 — needs palette extraction + wallpaper access).
- New layouts (Two-tone / Stacked) and new widget types (P1).
- Changing the pin flow — reuse the verified `requestPinAppWidget` + `WidgetPinReceiver`.

## Testing

- Unit: `WidgetPreset` free/premium partition + `of()` round-trips every preset's style.
- Unit: `WidgetConfigViewModel` — selecting a free preset ⇒ CTA is "add"; selecting a
  premium preset while not premium ⇒ `needsUnlock`; while premium ⇒ not.
- Device (verify skill): pick Sage (free green) → Add widget → renders green on home;
  pick Gold (premium) while free → Unlock everything → Paywall shows price → Maybe later
  returns. (Actual purchase can't complete on a dev build without a Play product, so the
  billing leg is verified up to `launchPurchase`.)

## Docs to reconcile after build

`01-PRD.md`, `02-TRD.md`, `05-Backend-Schema.md`, `08-CLAUDE.md` still describe premium as
"remove ads + coordinated layer." Update the widget-customization framing to
presets-free / engine-premium, and record ₹299 as the placeholder unlock price.
