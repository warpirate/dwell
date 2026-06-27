# Dwell test-data seeder

Fills Firestore with placeholder content so the Phase 1 wallpaper grid renders
before any real assets exist: 3 categories (Nature, Abstract, Dark) and 24
wallpapers (8 per category, picsum.photos images).

## Steps

1. **Service account key.** Firebase console → Project settings → Service
   accounts → **Generate new private key**. Save the file as
   `seed/serviceAccountKey.json`. It is gitignored; never commit it.
2. **Install + run.**
   ```
   cd seed
   npm install
   node seed.js
   ```
   Re-running is safe (fixed doc ids upsert, no duplicates).

## Security rules + index (do these once, or the app reads fail)

A new Firestore project denies all client reads by default, so the grid would
get permission-denied (not "offline"). Publish the rules and the composite
index from the repo root:

- **With the Firebase CLI** (`npm i -g firebase-tools`, then `firebase login`,
  `firebase use <project-id>`):
  ```
  firebase deploy --only firestore:rules,firestore:indexes
  ```
- **Or by hand:**
  - Rules: console → Firestore → Rules → paste `firestore.rules` → Publish.
  - Index: console → Firestore → Indexes → composite index on `wallpapers`
    with fields `category` (Asc), `order` (Asc). Or just open a category chip
    once — Firestore logs a one-click "create index" link in the error.

The "All" chip needs no composite index (single-field `createdAt` is automatic).
