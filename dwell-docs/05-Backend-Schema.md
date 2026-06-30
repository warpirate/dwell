# Backend Schema

**Product:** Dwell — a calm home screen: wallpapers, widgets, and a minimalist launcher
**Version:** v1.0
**Status:** Draft
**Backend:** Firebase (Firestore + Storage + Cloud Functions)
**Companion to:** 02-TRD.md

---

## 1. Firestore Collections

### `wallpapers/{wallpaperId}`

World-readable. Write-only by admin (you).

```
{
  id: string,                 // doc id
  title: string,              // optional, internal/admin label
  category: string,           // ref to category id, e.g. "nature"
  tags: string[],             // for future search (P2). Populate now, unused in v1 UI.
  urls: {
    thumb: string,            // grid preview WebP
    full_phone: string,       // phone apply
    full_tablet: string       // tablet apply
  },
  dominantColor: string,      // hex, for placeholder bg while loading
  isAiGenerated: boolean,     // true; supports Play AI-content disclosure tracking
  createdAt: timestamp,
  order: number               // manual sort within a category
}
```

The `tags` field exists from day one so adding search later (P2) needs no migration. This is the "architectural insurance" from the PRD.

### `categories/{categoryId}`

World-readable. Write-only by admin.

```
{
  id: string,                 // "nature", "abstract", "dark", ...
  name: string,               // display name
  order: number,              // chip order
  coverWallpaperId: string    // optional, for a category cover later
}
```

### `users/{uid}`

Read/write by that user only. Created on first sign-in (anonymous upgrade).

```
{
  uid: string,
  email: string | null,
  provider: string,           // "password" | "google" | "anonymous"
  premium: boolean,           // entitlement: removes ads + unlocks coordinated layer. Written server-side only.
  settings: {
    theme: string,            // "light" | "dark" | "system"
    notificationsOptIn: boolean
  },
  createdAt: timestamp,
  updatedAt: timestamp
}
```

`premium` is never trusted from the client. A Cloud Function validates the Play purchase token and writes it.

### `users/{uid}/favorites/{wallpaperId}`

Subcollection. Read/write by that user only.

```
{
  wallpaperId: string,
  addedAt: timestamp
}
```

Stored as a subcollection (not an array on the user doc) so favorites scale and sync cleanly without rewriting the whole user document on each change.

---

## 2. What Is NOT in the Schema (v1)

- **No wallpaper tiering.** All wallpapers and widgets are free, so wallpapers have no `isPremium`/`tier` field. The `premium` unlock gates only ads, the extra launcher home styles, and matched widget presets — all app-side feature flags, not per-wallpaper content. (If premium *content* ever ships in v2, add a `tier` field to `wallpapers` then. Not now.)
- **No notes-widget sync.** Notes are local-only in v1 (TRD open question, recommended local). No Firestore collection for them.
- **No search index.** `tags` is populated but there's no server-side search service in v1.

Keeping these out is deliberate. Every field that exists is a field that has to be maintained, secured, and reasoned about.

---

## 3. Firebase Storage Layout

```
/wallpapers/{wallpaperId}/thumb.webp
/wallpapers/{wallpaperId}/full_phone.webp
/wallpapers/{wallpaperId}/full_tablet.webp
```

- World-readable, admin-write.
- The app never lists the bucket. It reads URLs from Firestore `wallpapers` docs.

---

## 4. Security Rules (intent)

Firestore:
```
match /wallpapers/{id}    { allow read: if true;  allow write: if isAdmin(); }
match /categories/{id}    { allow read: if true;  allow write: if isAdmin(); }
match /users/{uid} {
  allow read, write: if request.auth.uid == uid;
  // premium field must not be client-writable:
  // enforce via a rule that rejects client changes to premium,
  // OR keep all writes to it inside a Cloud Function with admin SDK.
}
match /users/{uid}/favorites/{wid} {
  allow read, write: if request.auth.uid == uid;
}
```

Storage:
```
match /wallpapers/{allPaths=**} { allow read: if true; allow write: if isAdmin(); }
```

`isAdmin()` keys off a custom claim on your account or a hardcoded UID allowlist for v1.

The critical rule: clients can write their own user doc and favorites, but `premium` is owned by the server. Either strip it from client-writable fields in rules or route all entitlement writes through Cloud Functions.

---

## 5. Cloud Functions

| Function | Trigger | Does |
|---|---|---|
| `verifyPurchase` | callable from app after purchase | Validates the Play purchase token, writes `premium: true` to the user doc |
| `deleteAccount` | callable (in-app) + HTTPS (web page) | Deletes Auth user, user doc, favorites subcollection, user Storage objects |
| `onNewWallpaper` (optional) | Firestore create on `wallpapers` | Sends FCM to the `new_wallpapers` topic |

`deleteAccount` must cascade fully (favorites subcollection included) to satisfy the Play requirement that account deletion also deletes associated data.

---

## 6. Local Cache (on-device)

Not a backend, but it mirrors backend shape so offline works.

**Room**
- `wallpaper_cache`: mirrors the `wallpapers` fields needed to render the grid offline (id, category, urls.thumb, dominantColor).
- `favorites_cache`: local copy of the user's favorites for instant offline display, reconciled with Firestore on sync.

**DataStore (Preferences)**
- `theme`, `premium` (cached read of the server flag), `notificationsOptIn`, per-widget config keyed by `appWidgetId`.

Source-of-truth rule: server wins when online, cache serves when offline. `premium` is read from cache for speed but re-validated against Firestore on sign-in and app resume.

---

## 7. Admin Upload (v1, no CMS)

A small internal script or a single private web page that, authenticated as you:
1. Takes a source image.
2. Generates the three WebP variants (thumb / full_phone / full_tablet).
3. Computes `dominantColor`.
4. Uploads files to Storage and writes the `wallpapers` doc.

This is enough for a solo uploader. A real CMS is a P2 item.
