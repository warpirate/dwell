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
- **Launcher is the hero experience, but never forced.** It is opt-in and easy to back out of; never make it a precondition for anything. Build it last and ship-gated (slips to v1.1 if not solid). v1 ships ONE home style (default: Zen, free). Build the home screen from a `HomeStyle` config object, never hardcoded layout, so the P1 home-style picker (Editorial / Structured) is a config addition, not a rewrite. Do NOT build the multi-style picker in v1.
- **Static wallpapers only.** No live/animated wallpapers in v1.
- **No search in v1.** Populate the `tags` field on wallpapers for the future, but build no search UI or service.

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
