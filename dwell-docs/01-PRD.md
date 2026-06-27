# Product Requirements Document

**Product:** Dwell — minimalist wallpapers + widgets + optional launcher
**Platform:** Android
**Version:** v1.0
**Status:** Draft
**Owner:** Krdhar

---

## 1. Problem Statement

People who want a clean, minimalist phone aesthetic have to stitch it together from several apps: one for wallpapers, one for widgets, sometimes a launcher on top. The wallpaper apps are usually bloated with ads and low-quality images. The widget apps are fiddly. Nothing ties them into one consistent visual language.

This app gives that whole minimalist look in one place. Curated AI-generated wallpapers, clean customizable widgets, and an optional launcher for people who want to go all the way. The bar is simple: open the app, get something on your home screen, done.

The name is **Dwell**: to dwell is to live somewhere and to let your attention rest on something. A calm home you settle into. That is the product and the brand in one word.

---

## 2. Goals

1. Let a brand-new user apply a wallpaper within 60 seconds of first open, with zero login.
2. Ship a wallpaper catalog that can grow without app updates, so new content is a server push, not a release.
3. Make widgets that look good by default and are customizable enough that users actually keep them on their home screen.
4. Convert a meaningful share of active users to the one-time unlock (target set in metrics below) on the strength of the catalog, not dark patterns.
5. Keep the app fast and light. A minimalist app that feels heavy is a contradiction users will uninstall.

---

## 3. Non-Goals (v1)

- **No live/animated wallpapers.** Static only. Live wallpapers add battery and performance cost that fights the minimalist identity. Revisit later.
- **No wallpaper search.** Categories and browsing cover v1. Search is a v2 add once the catalog is large enough to need it.
- **No full CMS.** Uploading is a solo job via a simple internal tool or script. A multi-uploader CMS is premature.
- **No cross-platform.** Android only. The launcher and widget APIs are Android-specific and iOS buys us nothing here.
- **No social or sharing features.** No profiles, no sharing wallpapers to friends, no community. Out of scope.
- **Launcher is not the hero.** It ships, but it is optional and secondary. The app must be fully valuable to someone who never touches it.

---

## 4. Target Users

- **The aesthetic-minded browser.** Wants a nice wallpaper, maybe a clean clock widget. Will not sign up to browse. This is the majority and the app must serve them with no friction.
- **The customizer.** Cares about matching widgets to wallpaper, tweaking colors and fonts, building a coordinated home screen. Will favorite things and likely sign in to sync.
- **The all-in minimalist.** Wants the launcher too. Smallest group, highest engagement.

---

## 5. User Stories

**Wallpapers**
- As a first-time user, I want to browse and apply a wallpaper without making an account, so I get value immediately.
- As a user, I want wallpapers grouped into categories (nature, abstract, dark, etc.), so I can find a vibe fast.
- As a user, I want to set a wallpaper to home screen, lock screen, or both, so I control where it shows.
- As a tablet user, I want wallpapers that fit my screen properly, so they are not stretched or cropped badly.
- As a returning user, I want a push when new wallpapers drop, so I come back.

**Widgets**
- As a user, I want clock, calendar, notes, and battery widgets, so I cover the common home-screen needs.
- As a user, I want to change a widget's color and font, so it matches my wallpaper and taste.
- As a user, I want a clear config screen when I add a widget, so setup is obvious, not trial and error.

**Accounts and sync**
- As a user, I want to favorite wallpapers, and I am asked to sign in only at that point, so login is never a wall in front of browsing.
- As a user with an account, I want my favorites and settings synced across devices, so I do not lose them.
- As a user, I want to sign in with email or Google, so I pick what is convenient.

**Launcher (optional)**
- As a minimalist user, I want to optionally set this as my default launcher, so my whole home screen is clean.
- As a launcher user, I want an app drawer, minimal icons, and gestures, so the experience is complete, not half a launcher.
- As a launcher user, I want to choose between a few curated home styles, so my home screen matches my taste. (P1; v1 ships one style.)
- As a user, I want to back out of the launcher easily, so trying it is low-risk.

**Monetization**
- As a free user, I want to use the core app with ads, so I can decide if it is worth paying for.
- As a user, I want a single one-time purchase that removes ads and unlocks everything, so there is no subscription and no confusion.

**Edge cases**
- As a user with no internet, I want to still see cached wallpapers and use already-added widgets, so the app is not dead offline.
- As a user, I want clear feedback when a wallpaper fails to load or apply, so I am not staring at a blank screen.
- As a user who denies a permission, I want the app to keep working where it can, so one "no" does not break everything.

---

## 6. Requirements

### Must-Have (P0)

**Wallpapers**
- Server-hosted catalog via Firebase Storage + Firestore metadata, expandable without app updates.
- Category browsing (nature, abstract, dark, and more).
- Apply to home screen, lock screen, or both, separately.
- Multiple resolutions served for phone and tablet.
- Full-screen preview before applying.
- Offline cache of recently viewed wallpapers.

_Acceptance:_
- Given a user with no account, when they tap a wallpaper and choose "set lock screen," then it applies to the lock screen only and home stays unchanged.
- Given no network, when the user opens the app, then previously loaded wallpapers still render from cache.

**Widgets**
- Clock, calendar, notes, battery widgets.
- Per-widget config screen for color and font at minimum.
- Widgets render correctly across the supported size range.

_Acceptance:_
- Given a user adds the clock widget, when the config screen appears, then they can pick a color and font and see a live preview before placing it.

**Accounts**
- Optional login. Browsing, applying wallpapers, and using widgets all work with zero login.
- Login required only to favorite/sync and to complete the unlock purchase.
- Email/password and Google sign-in.
- Favorites and settings sync for logged-in users.
- **Account deletion, in-app and via a public web link.** Required by Play policy for any app with account creation. Deleting the account must also delete the user's associated data (favorites, settings, premium flag handled per billing rules).

_Acceptance:_
- Given a logged-out user, when they tap the favorite icon, then they are prompted to sign in, and after signing in the favorite is saved.
- Given a logged-in user, when they choose "delete account" in settings, then their account and associated data are removed and they are signed out.
- Given any user, when they visit the public account-deletion web page, then they can request deletion without needing to open the app.

**Monetization**
- Ads in the free tier.
- One-time "remove ads / unlock all" IAP via Google Play Billing.
- Premium state stored as a flag on the user record and respected across devices.

_Acceptance:_
- Given a user completes the unlock purchase, when they reopen the app on another signed-in device, then ads are gone and premium content is unlocked there too.

**Platform**
- Minimum Android 8.0 (API 26).
- Light and dark mode.
- Push notifications for new wallpaper drops.
- Crash and basic usage analytics (Crashlytics + Firebase Analytics).

### Nice-to-Have (P1)

- Launcher: home screen, app drawer, minimal icons, gestures. Ships in v1 with **one** home style if stable, but explicitly cuttable without blocking launch since it is optional.
- **Home-style picker.** Let the user choose between curated launcher home styles (e.g. Editorial / Zen / Structured). v1 ships one style; the picker and additional styles are a fast-follow. Critical: the launcher must be built so a home style is swappable config, not hardcoded, so adding styles later is a content change, not a rewrite.
- Widget size customization beyond defaults.
- "Recently added" and "popular" auto-collections.
- Auto-apply a daily wallpaper.

### Future Considerations (P2)

- Wallpaper search.
- Live/animated wallpapers.
- Multi-uploader CMS.
- Widget marketplace or many more widget types.
- Theme packs (wallpaper + matching widget presets bundled).

Design v1 so these are not architecturally blocked. Notably: keep wallpaper metadata rich enough to support search later, and keep the widget config system generic enough to add new widget types without a rewrite.

---

## 7. Success Metrics

**Leading (days to weeks)**
- Time to first wallpaper applied: target under 60 seconds for new users.
- Wallpaper apply rate: target 60%+ of new users apply at least one in their first session.
- Widget add rate: target 25%+ of active users add at least one widget in week one.
- Crash-free sessions: target 99%+.

**Lagging (weeks to months)**
- D7 retention: set a baseline at launch, then target improvement release over release.
- Unlock conversion: target 2 to 4% of monthly active users buy the one-time unlock. Treat anything above that as a strong signal.
- Launcher adoption: track what % of users set it as default. Informs whether to invest more in v2.

Measurement via Firebase Analytics. Evaluate at 1 week, 1 month, and 1 quarter post-launch.

---

## 8. Open Questions

- **[Legal]** AI-generated content disclosure. Play Console now has an AI-content disclosure step, and provenance/copyright varies by generation tool. Confirm the disclosure requirement and the license terms of whatever tool generates the wallpapers before launch. _Blocking for store submission, not for build._
- **[Design] RESOLVED.** Ad placement: a single native ad slot every ~12 items in the wallpaper grid, styled to match cards. No interstitial on the apply moment (interrupting the signature interaction is the wrong place to monetize).
- **[Product] RESOLVED.** Monetization is remove-ads-only. All wallpapers and widgets are free; the one-time unlock only removes ads. No content gating.
- **[Engineering] RESOLVED.** Tablet wallpaper handling: serve distinct pre-generated resolution variants from the server (thumb / full_phone / full_tablet), not client-side cropping.
- **[Product]** First-run experience: how do we surface the optional launcher without making it feel pushy or like a required step?

---

## 9. Timeline / Phasing

No hard external deadline. Suggested build order, since the doc set and code should follow this:

1. **Foundation:** Firebase project, auth, Firestore schema, wallpaper pipeline.
2. **Wallpapers (the core):** catalog, categories, preview, apply, offline cache.
3. **Accounts and sync:** optional login, favorites, settings sync.
4. **Monetization:** ads + Play Billing unlock.
5. **Widgets:** the four widget types plus config screens.
6. **Launcher:** last, because it is optional and the heaviest. Cut from v1 if it is not solid.
7. **Polish:** push notifications, analytics, light/dark, store-prep and AI-disclosure compliance.

---

## 10. Locked Decisions (reference)

- One app: wallpapers (primary) + widgets + optional launcher.
- Wallpapers: Firebase-hosted, categorized, static, multi-resolution, separate home/lock apply, AI-generated.
- Widgets: clock, calendar, notes, battery, customizable (background color, typeface, size).
- Launcher: ships v1 with one home style (default: Zen), built as swappable config; home-style picker (Editorial / Zen / Structured) is a P1 fast-follow. Optional, never forced, cuttable.
- Design language (confirmed via Claude Design pass): warm-gray neutrals, single deep-green accent `#3A5A40` used only on active states, Fraunces titles, outline monochrome app icons, generous whitespace.
- Accounts: optional, email + Google, login only for favorites/sync/purchase.
- Monetization: remove-ads-only one-time unlock via Play Billing. All content free.
- Min Android 8.0 (API 26), light + dark mode, push, analytics.
- Admin: solo, simple tool/script, no CMS for v1.
- Distribution: standard $25 personal Play developer account. Requires a 12-tester / 14-day closed test before production access. Compliance items (account deletion, privacy policy, data safety form, AI-content disclosure, Play Billing) tracked in the separate Launch Readiness doc.
