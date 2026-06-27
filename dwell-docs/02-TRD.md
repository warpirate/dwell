# Technical Requirements Document

**Product:** Dwell — minimalist wallpapers + widgets + optional launcher
**Platform:** Android
**Version:** v1.0
**Status:** Draft
**Companion to:** 01-PRD.md

---

## 1. Platform Targets

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Min SDK:** 26 (Android 8.0). Covers the vast majority of active devices.
- **Target SDK:** 36 (Android 16). Required for new app submissions as of Aug 31, 2026.
- **Compile SDK:** 36
- **Form factors:** phone (primary), tablet (supported via resolution variants and adaptive layout)
- **Orientation:** portrait-locked for app UI in v1. Wallpaper rendering handles both.

---

## 2. Architecture

MVVM with a unidirectional data flow. Compose screens observe state from ViewModels. ViewModels call repositories. Repositories are the only thing that talks to Firebase or the local cache.

```
Compose UI  →  ViewModel (StateFlow)  →  Repository  →  ┬ Firebase (remote)
                                                        └ Room + DataStore (local)
```

Rules:
- Screens hold no business logic. They render state and emit events.
- Repositories own the "remote vs cache" decision. The ViewModel never knows where data came from.
- One source of truth per data type. The cache is authoritative for what the user sees offline; the server is authoritative when online.

**Modules:** single Gradle module for v1. Split into feature modules later only if build times demand it. Premature modularization is a tax on a solo build.

**DI:** Hilt.

---

## 3. Key Libraries

| Concern | Choice | Why |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Modern, less boilerplate, good for custom minimal UI |
| Image loading | Coil 3 | Compose-native, good caching, WebP support |
| Async | Coroutines + Flow | Standard |
| DI | Hilt | Standard, low ceremony |
| Local DB | Room | Favorites + wallpaper metadata cache |
| Key-value | DataStore (Preferences) | Settings, theme, removeAds flag cache |
| Auth | Firebase Auth | Email + Google, anonymous upgrade |
| Remote DB | Cloud Firestore | Wallpaper metadata, user docs |
| File storage | Firebase Storage | Wallpaper image files + CDN delivery |
| Server logic | Cloud Functions | Account deletion cascade, purchase verification |
| Push | Firebase Cloud Messaging | New-content notifications |
| Analytics | Firebase Analytics | Usage events |
| Crash | Crashlytics | Crash reporting |
| Billing | Google Play Billing Library 7+ | One-time unlock |
| Ads | Google AdMob | Free-tier ads |
| Widgets | Jetpack Glance, RemoteViews fallback | Glance where capable, RemoteViews where not |

---

## 4. Wallpapers: Technical Approach

**Storage model.** Each wallpaper is one document in Firestore (metadata) plus image files in Firebase Storage. The app never lists the Storage bucket directly. It queries Firestore for metadata, which holds the download URLs.

**Resolution variants.** The server stores 2 to 3 pre-generated sizes per wallpaper:
- `thumb` (grid preview, ~400px wide WebP)
- `full_phone` (phone apply, ~1080x2400)
- `full_tablet` (tablet apply, ~1600x2560)

Pre-generating beats client-side cropping: smaller downloads, predictable quality, less device CPU. The cost is more Storage, which is negligible at this scale.

**Format.** WebP for all variants. Smaller than PNG/JPEG at equivalent quality, native Android support since well before our min SDK.

**Apply.** `WallpaperManager.setBitmap(bitmap, null, true, which)` where `which` is `FLAG_SYSTEM`, `FLAG_LOCK`, or both. Decode the correct resolution variant for the device before setting.

**Offline.** Coil's disk cache holds viewed thumbnails. Room stores metadata for favorites and recently viewed so the grid renders offline. Applying a wallpaper offline works if its full-res file is already cached, otherwise it shows a "needs connection" state.

---

## 5. Widgets: Technical Approach

Four widgets: clock, calendar, notes, battery.

- **Glance** for clock, notes, battery (state-driven, Glance handles these well).
- **RemoteViews** fallback if a widget needs layout Glance can't express cleanly.
- Each widget has a **configuration Activity** launched on placement, where the user picks color and font. Config is persisted per widget instance (by `appWidgetId`) in DataStore.
- Battery widget reads `BatteryManager`. Calendar widget reads the calendar provider (needs `READ_CALENDAR`, requested only when that widget is added). Notes widget stores text locally.
- Widget styling is a shared theming layer so adding new widget types later doesn't duplicate the color/font system. (Supports the P2 "more widgets" path.)

---

## 6. Launcher: Technical Approach (P1, cuttable)

If built, the launcher is a separate `Activity` with `CATEGORY_HOME` + `CATEGORY_DEFAULT` intent filters.

- App drawer queries `PackageManager` / `LauncherApps` for installed activities.
- Home screen, icon grid, gestures.
- Setting as default uses `RoleManager.createRequestRoleIntent(ROLE_HOME)` on supported versions.

**Home style as swappable config.** v1 ships one home style (default: Zen), but the home screen must be driven by a `HomeStyle` config object (layout, widget placement, spacing, icon treatment), not hardcoded. A style is data the home renderer reads, so adding Editorial and Structured later (the P1 picker) is a config addition, not a rewrite. Do not bake one layout's assumptions into the launcher core.

This is the heaviest component and is explicitly cuttable from v1. Build it last. Do not let it block launch.

---

## 7. Auth Approach

Firebase Auth with **anonymous-to-permanent upgrade**:

1. On first launch, no auth. The user browses and applies wallpapers fully logged out.
2. When the user favorites something or starts the unlock purchase, create or upgrade to a real account (email/password or Google), linking any anonymous session so nothing is lost.

This keeps the "zero friction to browse" promise from the PRD while still giving a real account when one is needed.

Google sign-in uses Credential Manager (the current recommended API), not the deprecated GoogleSignInClient.

---

## 8. Monetization: Technical Approach

- **Unlock = remove ads only.** All content is free. No content gating, no per-wallpaper tier.
- Purchase via Play Billing as a one-time (non-consumable) product, e.g. `remove_ads`.
- On purchase, verify and write `removeAds: true` to the user's Firestore doc (via a Cloud Function that validates the purchase token, so the flag can't be spoofed client-side).
- The flag is cached in DataStore for offline/fast checks. Firestore is the source of truth and restores the entitlement on a new device after sign-in.
- AdMob shows ads only when `removeAds` is false. Ad placement is defined in the UI/UX doc, not here.

---

## 9. Account Deletion: Technical Approach

Required by Play policy. Two entry points, one backend path.

- **In-app:** Settings → Delete account → confirm.
- **Web:** a public page (hosted separately) where a user submits their email to request deletion.
- Both trigger a **Cloud Function** that deletes: the Firebase Auth user, their Firestore doc, their favorites, and any user-specific Storage objects.
- The purchase entitlement is tied to the Google account at the Play level; deleting the app account does not refund or revoke the Play purchase, and the privacy policy states this.

---

## 10. Performance Constraints

A minimalist app that feels heavy is self-defeating. Hard targets:

- Cold start to interactive: under 2 seconds on a mid-range device.
- Wallpaper grid scroll: 60fps, no jank. Use Coil placeholders, paginate Firestore queries (load ~20 per page).
- Apply action: visible feedback within 100ms even if the set operation takes longer.
- APK/AAB size: keep base under ~15MB. Wallpapers are never bundled; they're all remote.

---

## 11. Security & Data Rules

- **Firestore security rules:** a user can read/write only their own user doc and favorites. Wallpaper metadata is world-readable, write-only by you (admin).
- **Storage rules:** wallpaper files world-readable, write-only by admin.
- All network over HTTPS (so the Data safety form can honestly answer "encrypted in transit: yes").
- No secrets in the client. Purchase verification and deletion run server-side in Cloud Functions.
- Admin upload uses a restricted path authenticated as you, not a client capability.

---

## 12. Analytics Events (initial set)

`wallpaper_view`, `wallpaper_apply` (with `target`: home/lock/both), `category_open`, `favorite_add`, `widget_add` (with `type`), `login`, `purchase_unlock`, `account_delete`. Keep the set small and meaningful. Crashlytics auto-captures crashes.

---

## 13. Open Technical Questions

- **[Eng]** Notes widget: local-only text, or synced for logged-in users? Local-only is simpler for v1. Recommend local-only.
- **[Eng]** Do we paginate categories server-side, or load a category fully? Recommend pagination from day one to keep the grid fast as the catalog grows.
- **[Eng]** Tablet detection: smallest-width qualifier (`sw600dp`) drives which resolution variant to request. Confirm thresholds during build.
