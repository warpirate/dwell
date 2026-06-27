# Implementation Plan

**Product:** Dwell — minimalist wallpapers + widgets + optional launcher
**Version:** v1.0
**Status:** Draft
**Build model:** Solo developer + AI coding agent (Claude Code) mix
**Companion to:** all prior docs

---

## 1. How This Plan Is Structured

You're building with an AI agent in the loop, so this plan is broken into **agent-sized tasks**: each one is a self-contained unit with a clear input, a clear done-state, and a verifiable output. You drive architecture and review; the agent does the implementation grind. Each phase ends with something you can actually run and see, not just code that compiles.

Pair this with `08-CLAUDE.md`, which is the context file the agent reads every session so it stays consistent across tasks.

A reminder that shapes the whole timeline: **the closed test is real calendar time.** You're on a personal Play account, so 12 testers across 14 continuous days is a hard gate before production. Phase 8 below is that gate, and it cannot be compressed. Plan for it.

---

## 2. Phases

### Phase 0 — Foundation
Goal: a running empty app wired to Firebase.
- Android project: Kotlin, Compose, Hilt, min SDK 26, target SDK 36.
- Firebase project created; app registered; `google-services.json` in place.
- Auth, Firestore, Storage, Functions, Analytics, Crashlytics, FCM enabled.
- Base theme tokens (light/dark, accent) implemented as a Compose theme.
- Bottom nav shell with three empty destinations.
- **Done when:** app launches to an empty themed Wallpapers tab, Firebase connection confirmed.

### Phase 1 — Wallpapers (the core)
Goal: the main loop works.
- Firestore `wallpapers` + `categories` read path, paginated.
- Category chips + staggered grid with Coil.
- Full-bleed preview + apply sheet (Home / Lock / Both) via WallpaperManager.
- Resolution-variant selection (phone vs tablet).
- Offline cache (Room + Coil disk cache).
- **Done when:** you can browse categories and apply a wallpaper to home/lock/both, online and offline-cached.

### Phase 2 — Accounts & sync
Goal: optional login with no data loss.
- Anonymous session on first launch.
- Sign-in sheet (email/password + Google via Credential Manager), anonymous upgrade.
- Favorites: subcollection write/read, local cache, sync.
- Settings sync (theme, notification opt-in).
- **Account deletion** (in-app) wired to `deleteAccount` Cloud Function.
- **Done when:** favorite-triggers-login works, favorites sync across devices, account deletion fully removes data.

### Phase 3 — Monetization
Goal: ads on, unlock removes them.
- AdMob integration, native ad slot in grid per UI/UX doc.
- Play Billing one-time `remove_ads` product.
- `verifyPurchase` Cloud Function; `removeAds` flag write + cache.
- Entitlement restore on new device.
- **Done when:** ads show for free users, purchase removes them, entitlement survives reinstall/new device.

### Phase 4 — Widgets
Goal: four configurable widgets.
- Clock, calendar, notes, battery via Glance (RemoteViews fallback where needed).
- Per-widget config Activity (color, font, live preview).
- Calendar permission requested contextually.
- **Done when:** all four widgets place on the home screen and respect their config.

### Phase 5 — Notifications & polish
Goal: drops, analytics, final UI pass.
- FCM topic subscription + `onNewWallpaper` function.
- Notification permission contextually (Android 13+).
- Analytics events wired (per TRD list).
- Light/dark final pass, empty/error/offline states, copy pass.
- **Done when:** a new upload pushes a notification; all states look right in both themes.

### Phase 6 — Launcher (optional, cuttable)
Goal: ship one solid launcher home style.
- HOME intent activity, app drawer (PackageManager/LauncherApps), icon grid, gestures.
- Set-default via RoleManager; easy revert.
- Build the home screen from a `HomeStyle` config object, not hardcoded layout. v1 ships one style (default: Zen). This makes the P1 home-style picker (Editorial / Structured) a config addition later, not a rewrite.
- **Done when:** the Zen home style is stable enough not to embarrass the app, driven by config. If the launcher isn't solid by this point, cut it to v2 and move on. Nothing else depends on it. The multi-style picker is explicitly NOT in this phase.

### Phase 7 — Store prep & compliance
Goal: submission-ready. (See 07-Launch-Readiness.md for the full checklist.)
- Privacy policy page live (public URL, no PDF).
- Public account-deletion web page live, hitting `deleteAccount`.
- Data safety form completed (declare Firebase Analytics, Crashlytics, AdMob, auth data).
- AI-generated content disclosure handled.
- Content rating questionnaire, store listing, screenshots, app signing.
- **Done when:** the app passes your own run-through of the Launch Readiness checklist.

### Phase 8 — Closed test (the hard gate)
Goal: clear Google's testing requirement.
- Upload to closed testing track.
- Recruit 12+ testers (aim for 15+ as buffer against opt-outs).
- Run 14 continuous days; testers genuinely use the app.
- Apply for production access; answer the questionnaire with real feedback and fixes.
- **Done when:** production access granted.

### Phase 9 — Production launch
- Promote to production, staged rollout.
- Monitor Crashlytics and analytics.
- First post-launch fixes.

---

## 3. Build Order Logic

Wallpapers first because it's the core value and everything else is optional around it. Accounts before monetization because the unlock attaches to an account. Widgets after the wallpaper loop is solid. Launcher last and cuttable. Compliance before the closed test because you can't test what isn't submission-shaped. The closed test before launch because Google requires it.

---

## 4. Working With the Agent

- Feed the agent one phase task at a time, not the whole plan. Each task references `08-CLAUDE.md` for conventions.
- Review architecture decisions yourself; let the agent handle implementation and boilerplate.
- After each task, run it. Don't batch unverified work.
- Keep `08-CLAUDE.md` updated as decisions evolve, so the agent never drifts from current reality.

---

## 5. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Closed-test delay (can't find 12 testers) | Start recruiting during Phase 5, not Phase 8. Tester communities exist. |
| Launcher eats the whole timeline | It's last and explicitly cuttable. Time-box it. |
| AI wallpaper licensing/disclosure | Resolve generation-tool licensing early; handle Play AI disclosure in Phase 7. |
| Firestore cost surprise | Paginate reads, cache aggressively. Read-heavy app stays in cheap territory if you don't over-fetch. |
| Scope creep | Non-goals in the PRD are the contract. New ideas go to a parking lot, not into v1. |
