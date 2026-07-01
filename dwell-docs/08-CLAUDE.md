# CLAUDE.md

Build-rules and context for the AI coding agent. Read this at the start of every session. Keep it current as decisions change.

---

## What we're building

**Dwell** — a single Android app: minimalist AI-generated wallpapers (primary) + customizable widgets + an optional minimalist launcher. Wallpapers are the core; everything else is secondary. The app must be fully useful to someone who never logs in and never touches the launcher.

The name: to dwell is to live somewhere and to let your attention rest on something. A calm home you settle into. The brand identity is that calm: warm-gray neutrals, deep-green accent `#3A5A40`, Fraunces titles, generous whitespace. Keep product copy and any design work true to it.

Full specs live in the doc set (PRD, TRD, UI/UX, App Flow, Backend Schema, Implementation Plan, Launch Readiness). This file is the quick-reference so you don't drift.

---

## Stack (do not change without being told)

- Kotlin, Jetpack Compose, Material 3
- Compose downloadable fonts: `androidx.compose.ui:ui-text-google-fonts` (Fraunces titles; body is system sans)
- MVVM, unidirectional data flow
- Hilt for DI
- Coroutines + Flow
- Min SDK 26, target/compile SDK 36
- Single Gradle module (do not split into feature modules in v1)
- Coil 3 for images (WebP)
- Room (cache) + DataStore (prefs)
- Firebase: Auth, Firestore, Storage, Cloud Functions, Analytics, Crashlytics, FCM
- Play Billing 7+ (one-time `unlock_premium`)
- AdMob
- Widgets: Jetpack Glance, RemoteViews only where Glance can't do it

---

## Architecture rules

- Screens render state and emit events. No business logic in composables.
- Only repositories touch Firebase or the cache. ViewModels call repositories, never Firebase directly.
- One source of truth per data type. Server wins online; cache serves offline.
- `premium` entitlement is **server-owned**. Never trust or write it from the client. It's set by the `verifyPurchase` Cloud Function.
- Account deletion goes through the `deleteAccount` Cloud Function and must cascade (user doc + favorites + storage objects).

---

## Product rules (these are decisions, not suggestions)

- **No login wall.** Browsing and applying wallpapers work fully logged out. Login is triggered only by: favoriting/sync, or the unlock purchase.
- **Monetization: one-time `premium` unlock.** ALL wallpapers and widgets are free — do NOT add per-wallpaper gating, `isPremium`/`tier` fields, or a content paywall. The unlock removes ads AND enables the coordinated layer: extra launcher home styles (Editorial / Structured) and wallpaper-matched widget presets, gated by the app-side `premium` flag. The Zen launcher style is free.
- **Widget customization is premium — but presets are free, the engine is premium.** Widgets are free, and so is a curated set of finished **presets** (Editorial / Sage / Bold — Sage uses the brand green; the identity colour is NEVER locked). The `premium` unlock adds the open **style engine** (mix any colour/size/opacity; later font/radius) plus the premium presets (Gold / Quiet / Forest), and on the roadmap wallpaper-matched presets. Gate widget *styling depth*, never widget *function*, and never the brand green. Placeholder unlock price **₹299** (one-time); the UI shows Play's real localized price via `BillingRepository.formattedPrice()`, falling back to ₹299.
- **Launcher is the hero experience, but never forced.** It is opt-in and easy to back out of; never make it a precondition for anything. Build it last and ship-gated (slips to v1.1 if not solid). v1 ships ONE home style (default: Zen, free). Build the home screen from a `HomeStyle` config object, never hardcoded layout, so the P1 home-style picker (Editorial / Structured) is a config addition, not a rewrite. Do NOT build the multi-style picker in v1.
- **Static wallpapers only.** No live/animated wallpapers in v1.
- **No search in v1.** Populate the `tags` field on wallpapers for the future, but build no search UI or service.

---

## Widget vision (where widgets can go)

Full research + roadmap: `docs/research/2026-07-01-widget-maximization-map.md`. Approved paywall/reframe mock: `docs/design/widget-monetization.html`. See also the `dwell-widget-monetization` memory.

- **The bet:** widgets aren't a side feature — they're what fuses the wallpaper app + launcher into one coordinated product. Grow from one clock → a **wallpaper-aware widget system** (~10 types, 60+ curated looks) without losing the calm editorial identity.
- **Our moat — wallpaper-matched widgets:** the market's #1 "premium" signal. A normal app **cannot read the current wallpaper on Android 14+** (`WallpaperManager.getDrawable()` throws `SecurityException`; the raw bitmap needs Play-restricted `MANAGE_EXTERNAL_STORAGE`). **Dwell owns the wallpaper bitmap the moment a user applies one**, so we can extract its palette (AndroidX Palette) and even pre-blur it for frosted glass. Blocked for every competitor, free for us. This — not colours/sizes — is the paid headline (P2).
- **Lane:** curated editorial + coordination + one clean one-time unlock. NOT KWGT (infinite ugly tinkering / formula language), NOT ThemePack (weekly subs + gem currency + ads-to-unlock — the category's "greedy/cheap" triggers). Keep the one-time `unlock_premium` (₹299 placeholder) as a positioning weapon; add optional one-time seasonal/designer **preset packs** (~₹49–99) as the reopen loop — never gems, never ads-to-unlock.
- **Free must ship one share-worthy screen** (a wallpaper + a free preset widget that looks great) — that screenshot is the growth engine (Reddit/TikTok "aesthetic home screen").
- **Widget tech rules (minSdk 26):**
  - Build new widgets in **Glance** (forward-compatible; inherits Android 16 RemoteCompose later). RemoteViews only where Glance can't.
  - **Custom fonts don't render natively** in RemoteViews/Glance → **bitmap-render Fraunces** headline/numerals for fidelity across launchers.
  - Wallpaper palette: own-bitmap + AndroidX Palette, or `WallpaperManager.getWallpaperColors()` (API 27+, no permission) / Material You `GlanceTheme` (API 31+). Never `getDrawable()`.
  - No real backdrop blur (own-bitmap pre-blur only). Refresh floor ~15 min (WorkManager) → design for "eventually fresh"; clocks self-tick via `TextClock`. Interactive (tap-to-log) is API 31+. Lock-screen widgets roll out ~Dec 2025 (Pixel-first, fragmented) at near-zero dev cost — a forward bet, not a reach play.
- **Build order:** P1 = Glance migration + core suite (Weather/Battery/Photo/Date-Agenda) + clock-family looks (typographic/word-clock/analog). P2 = wallpaper-match moat + interactive (now-playing, habit/countdown). P3 = seasonal preset packs + delight lane (photo-drop, mood) + lock-screen.

---

## Design rules

- The wallpapers are the hero. App chrome stays quiet so imagery carries the color.
- Accent color is used ONLY on the currently-acted-on thing (selected category, filled favorite, primary sheet action, applied confirmation). Never large fills or backgrounds.
- Tokens: warm-gray neutrals. Accent is a **dual-token**: light `#3A5A40` (deep muted green); dark a lighter same-family green that meets WCAG AA on the dark surface (currently `#6E9576`, treat the exact dark value as tunable, not locked). Do not force `#3A5A40` as a foreground on dark; it fails AA there (~2.25:1). Light bg `#FAFAF8`, dark bg `#0E0E0D`. Light + dark, both first-class, full token coverage. (Design language confirmed via the Claude Design pass; treat the light values and the dual-token rule as locked.)
- Type: Fraunces (display, sparing) + Inter/system (body). Sentence case. Generous whitespace. Single 8dp corner radius.
- Launcher app icons: outline, monochrome/grayscale, so the wallpaper stays the only color.
- The signature moment is the full-bleed preview + minimal apply sheet (Home/Lock/Both). Keep it clean; no celebration animation.
- Minimal, purposeful motion only. Extra animation reads as overdesigned here.
- Quality floor: 48dp touch targets, visible focus, reduced-motion respected, content descriptions on icon-only controls, WCAG AA contrast.

---

## Copy rules

- Plain verbs, sentence case, no filler.
- An action keeps its name through the flow: button "Apply" → toast "Applied."
- Errors say what happened and how to fix it, in the app's voice. No apologizing, no vagueness.
- Empty states invite action.
- Name things by what the user controls, not how the system is built.

---

## Writing style (for any prose, comments, docs you produce)

- Short, plain sentences. Varied rhythm.
- No em dashes.
- No AI tells ("dive in", "it's worth noting", "robust", "seamless", etc.).
- Say less, mean it.

---

## Compliance constraints (bake in from the start, don't retrofit)

- Target API 36, signed AAB, Play App Signing.
- Account deletion: in-app + public web page, both cascade-delete data.
- All network HTTPS (Data safety form claims encryption in transit).
- Request permissions contextually (calendar when the widget is added; notifications on Android 13+), never on launch. No unused permissions in the manifest.
- Unlock purchase via Play Billing only, verified server-side.
- Wallpapers are AI-generated: keep `isAiGenerated: true` in metadata for disclosure tracking.

---

## Working agreement

- Implement one phase task at a time (see Implementation Plan). Each task must end in something runnable.
- Don't introduce libraries or patterns not listed here without flagging it first.
- When a decision in this file conflicts with a request, surface the conflict instead of silently picking.
- After a task, state what to run to verify it.
