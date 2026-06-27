# App Flow

**Product:** Dwell — minimalist wallpapers + widgets + optional launcher
**Version:** v1.0
**Status:** Draft
**Companion to:** 01-PRD.md, 03-UIUX-Design.md

---

## 1. First Launch

```
App open (first time)
   │
   ├─ No login wall. Land directly on Wallpapers.
   ├─ Anonymous Firebase session created silently (for analytics + later upgrade).
   ├─ Optional one-card intro at top of grid: "Tap any wallpaper to set it."
   │   (dismissible, never blocks)
   └─ User can browse, preview, and apply immediately.
```

No onboarding carousel. No permission requests upfront. The app proves its value in the first five seconds, then asks for things only when needed.

---

## 2. Core Flow: Browse → Apply

```
Wallpapers grid
   │  tap category chip → grid filters to that category
   │  tap thumbnail
   ▼
Full-bleed preview + apply sheet
   │  choose Home / Lock / Both
   │  tap Apply
   ▼
   ├─ online + image cached or fetchable → apply, show "Applied to [target]"
   ├─ offline + image cached            → apply works
   └─ offline + image not cached        → "Can't apply offline. Connect and try again."
```

Applying never requires login. This is the main loop and it stays frictionless.

---

## 3. Favorite Flow (first login trigger)

```
Tap heart on a wallpaper
   │
   ├─ logged in    → favorite saved, heart fills (accent), synced to Firestore
   └─ logged out   → sign-in sheet rises
                        │
                        ├─ user signs in (email or Google)
                        │     → anonymous session upgraded/linked
                        │     → the favorite they just tapped is saved
                        │     → returns to where they were
                        └─ user dismisses → no favorite saved, no nagging
```

---

## 4. Remove-Ads Purchase Flow

```
More → Remove ads
   │
   ├─ logged out → sign-in sheet first (entitlement must attach to an account)
   ▼
Play Billing purchase dialog
   │  success
   ▼
Cloud Function verifies purchase token
   │
   ▼
removeAds = true written to user doc + cached locally
   │
   ▼
Ads disappear app-wide. "Remove ads" row shows "Unlocked."
```

Restore: signing in on a new device reads `removeAds` from Firestore, so the entitlement follows the account.

---

## 5. Widget Flow

```
Widgets tab → tap a widget → config (color, font, live preview)
   │  "Add to home screen"
   ▼
System widget placement
   │  (calendar widget only) → request READ_CALENDAR
   │       ├─ granted → widget shows events
   │       └─ denied  → widget shows a "grant calendar access" prompt, app unaffected
   ▼
Widget live on home screen
```

A denied permission degrades one widget, never the app.

---

## 6. Optional Launcher Flow

```
More → "Use as launcher" toggle
   │
   ▼
System "set default home app" dialog (RoleManager)
   │
   ├─ user accepts → app becomes home; app drawer + gestures active
   └─ user declines → toggle stays off, nothing changes
   │
   └─ user can revert anytime in More or in system settings
```

Trying the launcher is low-risk and one tap to undo. Never forced, never a precondition for anything else.

---

## 7. Account Deletion Flow

```
More → Account → Delete account
   │
   ▼
Confirmation: "This permanently deletes your account, favorites, and synced settings. This can't be undone."
   │  confirm
   ▼
Cloud Function deletes: Auth user, user doc, favorites, user Storage objects
   │
   ▼
Signed out, returned to logged-out browsing. App still fully usable.
```

Plus the public web deletion path (outside the app) that hits the same Cloud Function. Required by Play policy.

---

## 8. Push Notification Flow

```
New wallpapers uploaded (admin)
   │
   ▼
FCM message to "new_wallpapers" topic
   │
   ▼
Notification: "New wallpapers added"
   │  tap
   ▼
Opens app to the new/recent wallpapers
```

Notification permission (Android 13+) requested contextually, not on first launch. If denied, the app works; the user just won't get drops.

---

## 9. Edge Cases & States

| Situation | Behavior |
|---|---|
| No network on launch | Grid shows cached thumbnails + favorites. Quiet offline line. |
| Wallpaper fails to load | Card shows a neutral placeholder, retry on tap. No red error spam. |
| Apply fails | Clear message naming the cause and the fix. No silent failure. |
| Sign-in cancelled | Return to prior screen, no penalty, no repeated prompt. |
| Purchase pending/failed | Standard Play Billing states surfaced plainly. Entitlement only flips on verified success. |
| Permission denied (calendar/notifications) | Degrade the single dependent feature, keep the app working. |
| Account deletion mid-flight | Show progress, confirm completion, then sign out. |
| Tablet | Adaptive grid columns, tablet-resolution wallpaper variants. |

---

## 10. Navigation Map

```
                 ┌──────────────┐
                 │  Wallpapers  │ ◀── default landing
                 └──────┬───────┘
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
   Category filter   Preview+Apply    (bottom nav)
                        │
                   Favorite → Sign-in sheet (on demand)

   ┌──────────┐   ┌──────────┐
   │ Widgets  │   │   More   │
   └────┬─────┘   └────┬─────┘
        ▼              ├─ Favorites
   Widget config       ├─ Appearance (light/dark/system)
        ▼              ├─ Account (sign in/out, delete)
   Add to home         ├─ Remove ads
                       ├─ Launcher toggle (optional)
                       └─ Privacy / support / about
```
