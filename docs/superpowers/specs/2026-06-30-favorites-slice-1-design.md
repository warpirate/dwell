# Phase 2 Slice 1 — Anonymous auth + favorites

**Product:** Dwell
**Phase:** 2 (Accounts & sync), first buildable slice
**Status:** Approved design
**Companion to:** dwell-docs/05-Backend-Schema.md, dwell-docs/06-Implementation-Plan.md, dwell-docs/08-CLAUDE.md

---

## 1. Goal

Make the favorite control real. Tapping the heart on a wallpaper saves it, the
save survives restarts and works offline, and the user can browse their saved
wallpapers from two places. No sign-in UI in this slice.

Done when: tap the heart in the preview, it fills, and the favorite shows up in
both the Saved filter and the More screen after a restart and offline.

## 2. Scope

In:
- Anonymous Firebase Auth on launch (silent, no prompt).
- Favorites: write/read `users/{uid}/favorites` subcollection, Room
  `favorites_cache`, offline-first.
- Heart toggle in the preview, with a filled accent state.
- Two read surfaces: a Saved chip in the category row, and a Favorites row in
  the More tab that opens a favorites grid.

Out (deferred, by design):
- Sign-in sheet, email/Google, anonymous upgrade. (Slice 2.)
- Cross-device sync and the Firestore realtime listener. Anonymous auth is
  per-install, so there is no second device to sync with yet. (Slice 2.)
- `users/{uid}` parent doc creation and settings sync. Favorites do not need
  the parent doc. (Later in Phase 2.)
- Account deletion / Cloud Functions / Blaze billing. (Later in Phase 2.)
- Toggling a favorite from a grid card. Preview-only this slice.

## 3. Architecture

Chosen approach: Room is the UI source of truth; Firestore is durable backing.

- The heart tap writes to Room first (optimistic, instant UI through a Flow),
  then writes through to Firestore.
- On launch and resume, a one-shot reconcile reads the Firestore favorites and
  replaces the Room copy. No live listener this slice.

This keeps the heart instant and correct offline, with the minimum moving parts.
A realtime listener buys nothing while there is a single anonymous user on a
single install; it lands in slice 2 with sign-in.

Follows the existing repository rule: only repositories touch Firebase or the
cache; view models call repositories.

### 3.1 Auth

`AuthRepository` (interface + impl, singleton):
- `uid: Flow<String?>` — emits the current user id, null when signed out.
- `suspend fun ensureSignedIn()` — if `FirebaseAuth.currentUser` is null, call
  `signInAnonymously()`. Idempotent.

`FirebaseAuth` is provided in `DataModule`. `ensureSignedIn()` is invoked once
at app start from a `LaunchedEffect` in `DwellApp` (before favorites are read).

### 3.2 Cache

`FavoriteEntity` in table `favorites_cache`:
- `wallpaperId: String` (primary key)
- `addedAtMillis: Long`

`FavoriteDao`:
- `observeAll(): Flow<List<FavoriteEntity>>` — ordered `addedAtMillis` desc.
- `suspend fun upsert(entity)`
- `suspend fun deleteById(wallpaperId: String)`
- `suspend fun replaceAll(entities: List<FavoriteEntity>)` — clear + insert,
  used by reconcile. One transaction.

`DwellDatabase` adds `FavoriteEntity` to its entity list and bumps `version`.
Dev builds keep `fallbackToDestructiveMigration`.

### 3.3 Favorites repository

`FavoritesRepository` (interface + impl, singleton):
- `observeFavoriteIds(): Flow<Set<String>>` — maps `dao.observeAll()` to a set
  of ids. Drives heart fill state.
- `observeFavoriteWallpapers(): Flow<List<Wallpaper>>` — joins favorites to
  `wallpaper_cache` and emits the saved wallpapers, newest-saved first. A
  favorited wallpaper is always in `wallpaper_cache`, because it was on screen
  when favorited, so this works offline.
- `suspend fun toggle(wallpaper: Wallpaper)` — if currently favorited, delete
  from Room then delete the Firestore doc; else upsert to Room then set the
  Firestore doc. Room write is optimistic; a Firestore failure is non-fatal and
  leaves the optimistic local state (reconcile corrects it later).
- `suspend fun reconcile()` — read `users/{uid}/favorites` once, map to
  entities, `dao.replaceAll(...)`. No-op when there is no uid or when offline.

The Firestore favorite doc matches the schema: `{ wallpaperId, addedAt }` at
`users/{uid}/favorites/{wallpaperId}`. Doc id is the wallpaper id, so a toggle
is a single set or delete with no query.

Implementation note: the join in `observeFavoriteWallpapers` reads the favorite
ids from `favorites_cache` and the rows from `wallpaper_cache`. A favorite whose
wallpaper row is missing from the cache is skipped rather than shown broken.

### 3.4 Preview wiring

`PreviewViewModel` takes `FavoritesRepository`:
- Collects `observeFavoriteIds()` and exposes `isFavorite: Boolean` in
  `PreviewUiState` (true when the set contains this wallpaper id).
- `onToggleFavorite()` calls `repository.toggle(wallpaper)` in
  `viewModelScope`.

`PreviewScreen` heart control:
- Outline heart (`ic_heart_outline`) when not saved.
- Filled heart (`ic_heart_filled`, new drawable) tinted with the accent color
  when saved. Accent on the acted-on thing, per the brand rule.
- `onClick` calls `onToggleFavorite()`. The favoriting-triggers-login behavior
  is not wired this slice; the heart just saves.

### 3.5 Read surfaces

Saved chip (Wallpapers tab):
- A special chip with a heart, shown in `CategoryChipRow` alongside the
  category chips. Distinct id, e.g. `__favorites__`, that no real category uses.
- Selecting it puts `WallpapersViewModel` in favorites mode: the grid renders
  `favoritesRepository.observeFavoriteWallpapers()` instead of the paged
  catalog. Category paging is untouched and resumes when another chip is picked.
- Empty state in favorites mode: "No favorites yet. Tap the heart on any
  wallpaper."

Favorites row (More tab):
- The More screen gets a Favorites row that navigates to a new `favorites`
  route at the top level (sibling of `preview`, so it can sit over or beside the
  shell as a normal screen with a back affordance).
- `FavoritesScreen` renders the same grid from
  `observeFavoriteWallpapers()`; tapping an item opens the existing preview
  route.

Both surfaces read the one repository flow and reuse the existing
`WallpaperGrid` composable and the preview route. No duplicate data path.

## 4. Data flow

Favoriting:
```
tap heart -> PreviewViewModel.onToggleFavorite()
          -> FavoritesRepository.toggle(wallpaper)
          -> Room upsert/delete (UI updates via observeFavoriteIds)
          -> Firestore set/delete users/{uid}/favorites/{id}
```

Reading favorites:
```
favorites_cache (Flow) join wallpaper_cache -> List<Wallpaper>
   -> Saved chip grid / FavoritesScreen
```

Launch reconcile:
```
app start -> AuthRepository.ensureSignedIn()
          -> FavoritesRepository.reconcile()  (Firestore -> Room replaceAll)
```

## 5. Error handling

- Anonymous sign-in failure: favorites stay local in Room; `reconcile()` and
  Firestore write-through become no-ops until a uid exists. The heart still
  works on-device. No error UI this slice.
- Firestore write failure on toggle: non-fatal. Optimistic Room state stays;
  the next `reconcile()` reconciles against the server.
- Offline: Room serves everything; write-through is skipped or fails quietly.
- A favorited wallpaper missing from `wallpaper_cache`: skipped in the list.

## 6. Security rules

Extend `firestore.rules`:
```
match /users/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
  match /favorites/{wid} {
    allow read, write: if request.auth != null && request.auth.uid == uid;
  }
}
```
Keep the existing world-readable rules for `wallpapers` and `categories`.
`removeAds` enforcement and the user-doc field guard come with slice 2.

Deploy with `firebase deploy --only firestore:rules`.

## 7. Console gates (manual, before it runs)

- Enable the Anonymous sign-in provider in the Firebase console
  (Authentication > Sign-in method).
- Deploy the updated rules.

## 8. Testing

Repository logic, with a fake DAO and a fake Firestore seam:
- `toggle` adds then removes; Room reflects each step.
- `toggle` keeps optimistic local state when the remote write throws.
- `reconcile` replaces local with the remote set, including removals.
- `observeFavoriteWallpapers` skips ids with no cached wallpaper row.

Manual on device:
- Tap heart in preview, it fills with accent. Kill and reopen: still filled.
- Airplane mode: favorite a different wallpaper, it persists; the Saved chip and
  the More > Favorites screen both list it.
- Unfavorite: it disappears from both surfaces.

## 9. Files

New:
- `data/auth/AuthRepository.kt`, `AuthRepositoryImpl.kt`
- `data/local/FavoriteEntity.kt`, `FavoriteDao.kt`
- `data/favorites/FavoritesRepository.kt`, `FavoritesRepositoryImpl.kt`
- `ui/favorites/FavoritesScreen.kt`
- `res/drawable/ic_heart_filled.xml`

Changed:
- `data/local/DwellDatabase.kt` (entity + version), `di/DataModule.kt`
  (FirebaseAuth, FavoriteDao), `di/RepositoryModule.kt` (auth + favorites
  binds)
- `ui/preview/PreviewViewModel.kt`, `PreviewUiState.kt`, `PreviewScreen.kt`
- `ui/wallpapers/WallpapersViewModel.kt`, `WallpapersUiState.kt`,
  `WallpapersScreen.kt`, `components/CategoryChipRow.kt`
- `ui/screens/MoreScreen.kt`, `ui/DwellApp.kt` (favorites route)
- `res/values/strings.xml`, `firestore.rules`
