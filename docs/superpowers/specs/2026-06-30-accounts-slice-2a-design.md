# Phase 2 Slice 2a — Accounts (sign-in, anonymous upgrade, resume sync)

**Product:** Dwell
**Phase:** 2 (Accounts & sync), slice 2a
**Status:** Approved design (hardened via parallel design review)
**Companion to:** dwell-docs/05-Backend-Schema.md, dwell-docs/08-CLAUDE.md, docs/superpowers/specs/2026-06-30-favorites-slice-1-design.md

---

## 1. Goal

Let a user turn their silent anonymous session into a real account (email/password
or Google) without losing favorites, see their account state, sign out, and have
favorites sync across devices on app open. No login wall: everything still works
signed out.

Done when: from a real device, sign up with email, the favorites you saved
anonymously are still there; sign out and back in and they return; (after the
Google gates) the same works with Google; favorites saved on one device appear on
another after opening the app.

## 2. Scope

In:
- Sign-in bottom sheet: email/password (two modes: Sign in / Create account) and
  "Continue with Google" (Credential Manager).
- Anonymous -> real **upgrade** via `linkWithCredential` (uid preserved, favorites
  kept with zero migration).
- Collision fallback (credential already belongs to another account): sign in to
  that account and **merge** the anonymous favorites up before reconcile.
- Create/update `users/{uid}` doc on real sign-in (never write `removeAds`).
- Sign-out (atomic: sign out then re-anonymous), favorites cleared to a clean slate.
- Account row in More: "Sign in" when anonymous, email + "Sign out" when real.
- Reconcile favorites on app resume (ON_START), in addition to cold launch.
- `removeAds` Firestore rules hardening (lock the field server-side).

Out (deferred):
- Settings/theme screen and settings sync (slice 2b).
- A `pendingSync`/dirty-flag column and the offline-replay reconcile redesign.
- Notifications opt-in (Phase 5).
- Account deletion, orphaned-anonymous cleanup, Cloud Functions, Blaze (later).
- Release/upload-key SHA-1 (release checklist).
- Nonce on the Google option (Firebase verifies server-side; add later if a
  security review asks).

## 3. Build order (decoupled by gate)

The email/password path needs **no** console setup. The Google path is blocked
until manual gates clear (see §9), because `app/google-services.json` currently
has `"oauth_client": []`, so `R.string.default_web_client_id` does not exist and
Google code will not compile. Therefore:

1. **2a-i — email/password + the whole account spine** (repository, upgrade
   protocol, merge, user doc, sign-out, resume sync, sheet, More row, rules). Built
   and verified first.
2. **2a-ii — Google** bolted on after the gates clear.

## 4. Architecture

### 4.1 Auth repository

Extend `AuthRepository` (keep `uid`, `currentUid()`, `ensureSignedIn()` unchanged):

```
sealed interface UpgradeResult {
  data class Linked(val uid: String) : UpgradeResult          // uid preserved
  data class SignedInExisting(val uid: String) : UpgradeResult // collision -> adopted account
  data class Error(val error: AuthError) : UpgradeResult
}

enum class AuthError { INVALID_CREDENTIALS, WEAK_PASSWORD, INVALID_EMAIL, NETWORK, UNKNOWN }

suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult
suspend fun linkGoogle(idToken: String): UpgradeResult
suspend fun signInEmail(email: String, password: String): UpgradeResult  // returning user, no anon link
suspend fun signOut()
```

`AuthRepositoryImpl` (inject `FirebaseFirestore`):
- **Upgrade (no collision, the common lossless path):** when `currentUser != null
  && isAnonymous`, build the credential (`EmailAuthProvider.getCredential` or
  `GoogleAuthProvider.getCredential(idToken, null)`) and call
  `currentUser.linkWithCredential(cred).await()`. uid is unchanged; `users/{uid}`
  and `users/{uid}/favorites` stay owned. Call `ensureUserDoc()`. Return
  `Linked(uid)`.
- **Collision:** `linkWithCredential` throws `FirebaseAuthUserCollisionException`
  (covers `ERROR_EMAIL_ALREADY_IN_USE` and `ERROR_CREDENTIAL_ALREADY_IN_USE`).
  Sign in to the existing account with the same credential
  (`signInWithCredential`). Call `ensureUserDoc()`. Return
  `SignedInExisting(existingUid)`. The **merge** is sequenced by the caller (§4.3).
- **Errors:** `FirebaseAuthInvalidCredentialsException` -> `INVALID_CREDENTIALS`,
  `FirebaseAuthWeakPasswordException` -> `WEAK_PASSWORD`, etc., mapped at the
  repository boundary to `AuthError` (no Firebase types leak up).
- `private suspend fun ensureUserDoc()`: `set(users/{uid}, {uid, email, provider,
  updatedAt: serverTimestamp(), createdAt: serverTimestamp() only when absent},
  SetOptions.merge())`. **Never writes `removeAds`.** `provider` derived from
  `currentUser.providerData`.
- `signOut()`: `auth.signOut()` then `auth.signInAnonymously().await()` so
  `currentUid()` is never a steady-state null.

Catching the collision exception is `FirebaseAuthUserCollisionException` only.
Invalid-credential and weak-password are inline form errors, never a merge path.

### 4.2 Favorites merge primitive

- `FavoriteDao`: add `@Query("SELECT * FROM favorites_cache") suspend fun getAll():
  List<FavoriteEntity>` (one-shot; no schema change, no version bump).
- `FavoritesRemoteSource`: add `suspend fun putAll(uid: String, favorites:
  List<FavoriteRemote>)` — a Firestore `WriteBatch` of
  `document(wallpaperId).set({wallpaperId, addedAt}, SetOptions.merge())`, chunked
  <= 450 per batch, committed sequentially. Non-destructive union.
- `FavoritesRepository`: add `suspend fun mergeLocalInto(targetUid: String)` —
  reads `favoriteDao.getAll()`, maps to `FavoriteRemote`, calls `remote.putAll`.
- `FavoritesRepository`: add `suspend fun clearLocal()` — `favoriteDao.clear()`
  (used by sign-out before reconcile).

### 4.3 Sequencing (the data-loss protocol — load-bearing)

`reconcile()` runs `favoriteDao.replaceAll()` = clear + insert from server only.
It is the only destructive op. Every local-only favorite must reach the server
before any reconcile, or it is lost. So the `SignInViewModel` runs the whole flow
in one coroutine:

```
launch {
  when (val r = auth.linkEmail(email, pw, createAccount) /* or linkGoogle(idToken) */) {
    is Linked            -> favorites.reconcile()                 // uid kept, server already has favs
    is SignedInExisting  -> { favorites.mergeLocalInto(r.uid); favorites.reconcile() } // merge FIRST
    is Error             -> uiState.update { it.copy(inlineError = map(r.error)) }
  }
}
```

Guard against a racing reconcile (cold-launch or resume) running between the auth
switch and the merge: `FavoritesRepositoryImpl` holds a `Mutex` serializing
identity-change vs merge/reconcile, plus an `isUpgrading` flag the resume reconcile
checks and skips while set.

`currentUid()` is read live from FirebaseAuth on every `toggle()`/`reconcile()`
call (no cached field), so the uid change after link/sign-in/sign-out propagates to
`FavoritesRepositoryImpl` with no rewiring.

### 4.4 Sign-out

`SignInViewModel.signOut()` (or an account VM): `auth.signOut()` +
`signInAnonymously()` (inside `AuthRepository.signOut()`), then
`favorites.clearLocal()` **before** `favorites.reconcile()` so the previous
account's hearts do not leak into or get pushed under the new anonymous uid. For
Google, the UI also calls `credentialManager.clearCredentialState(...)` so the next
attempt re-shows the picker. Sign-out never deletes remote favorites.

### 4.5 UI

- `ui/auth/SignInSheet.kt` — Material3 `ModalBottomSheet` (chosen over the
  hand-rolled `ApplyPanel` Surface because the form needs IME handling and
  drag-to-dismiss). Styled to match Dwell: `colorScheme.surface`,
  `RoundedCornerShape(topStart=16, topEnd=16)`, drag handle, Fraunces
  `displayLarge` title, forest-green primary button identical to `ApplyButton`
  (radius 8, height 52). Two-mode segmented toggle (Sign in | Create account,
  styled like `TargetSelector`), email + password (show/hide) fields, primary
  button, divider, "Continue with Google" button, inline error text.
  `Modifier.imePadding()` + `navigationBarsPadding()`. `KeyboardType.Email` /
  `Password`, IME action submits.
- `ui/auth/SignInViewModel.kt` (`@HiltViewModel`, `AuthRepository` +
  `FavoritesRepository`): sheet UiState (mode, email, password, inProgress,
  inlineError), sequences the flow in §4.3, exposes `signOut()`.
- Google credential retrieval helper lives at the Compose/Activity boundary
  (`CredentialManager.create(activity).getCredential(activity, request)` with
  `GetSignInWithGoogleOption(serverClientId =
  getString(R.string.default_web_client_id))`), extracts the
  `GoogleIdTokenCredential` id token, passes only the `idToken` String into
  `AuthRepository.linkGoogle`. Repository stays Android-UI-free.
- `MoreScreen`: add an account `MoreRow` above Favorites. Anonymous -> "Sign in"
  (opens the sheet). Real -> shows the email with a "Sign out" action. Driven by an
  `isAnonymous` / account-email signal from `AuthRepository` (seeded with the
  current value so it is correct on first composition).
- On collision (`SignedInExisting`), show a short toast: "Signed in to your
  existing account. Kept your saved wallpapers."

### 4.6 Resume reconcile

In the Compose layer (`DwellApp`), use `LocalLifecycleOwner` + a
`LifecycleEventObserver` on `ON_START` to call `bootstrap.onResume()` which launches
`favorites.reconcile()`. No new dependency, no `ProcessLifecycleOwner` cold-start
double-fire. `AppBootstrapViewModel.start()` keeps owning the one-time cold-start
`ensureSignedIn()` + `reconcile()`; `onResume()` is not gated by `started`.
`reconcile()` early-returns on null uid (safe anonymous/offline no-op) and is gated
by `isUpgrading`.

## 5. Data flow

Upgrade (no collision):
```
form submit -> SignInViewModel -> auth.linkEmail(create=false/true)
  -> currentUser.linkWithCredential(cred)  [uid kept]
  -> ensureUserDoc()  -> Linked(uid)
  -> favorites.reconcile()  -> sheet dismisses, More shows email
```

Collision:
```
auth.linkEmail -> linkWithCredential throws CollisionException
  -> signInWithCredential(existing) -> ensureUserDoc() -> SignedInExisting(existingUid)
SignInViewModel: favorites.mergeLocalInto(existingUid)  [putAll, set+merge]
              -> favorites.reconcile()  -> toast -> More shows email
```

Sign-out:
```
auth.signOut() + signInAnonymously()  [new anon uid]
  -> favorites.clearLocal()  -> favorites.reconcile()  [empty]  -> More shows "Sign in"
```

## 6. Error handling

- Link/sign-in errors map to `AuthError` -> inline sheet copy (Dwell voice: say what
  happened + how to fix, no apology). Under email-enumeration-protection,
  wrong-password and user-not-found both surface as `INVALID_CREDENTIALS`, so
  sign-in uses neutral "Email and password don't match." Create + email-already-in-
  use offers "Sign in instead" and flips the mode.
- `fetchSignInMethodsForEmail` is **not used** (deprecated; returns empty under
  enumeration protection, would mis-route everyone). The explicit mode toggle is the
  source of truth; the post-submit error code only drives the one-tap mode flip.
- Google `NoCredentialException` (no Google account / picker dismissed) -> quiet
  inline "No Google account available" or dismiss, no crash.
- Network failures -> "Check your connection and try again."
- Sign-in failure leaves the anonymous session intact; favorites untouched.

## 7. Security rules

Harden `users/{uid}` so the owner can write their doc but never set/change
`removeAds` (a later Cloud Function owns it). Keep the favorites subcollection rule.

```
match /users/{uid} {
  allow read: if request.auth != null && request.auth.uid == uid;
  allow create: if request.auth != null && request.auth.uid == uid
                && !('removeAds' in request.resource.data);
  allow update: if request.auth != null && request.auth.uid == uid
                && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['removeAds']);
  match /favorites/{wid} {
    allow read, write: if request.auth != null && request.auth.uid == uid;
  }
}
```

Deploy with `firebase deploy --only firestore:rules`; validate the `affectedKeys`
guard in the Rules Playground (the condition is negated). Note: the upgrade/merge
works without this change (owner-only already permits the writes); the hardening is
shipped because it locks `removeAds` before any billing exists.

## 8. New dependencies

Add to `gradle/libs.versions.toml` + `app/build.gradle.kts`:
- `androidx.credentials:credentials:1.6.0`
- `androidx.credentials:credentials-play-services-auth:1.6.0`
- `com.google.android.libraries.identity.googleid:googleid:1.1.1`

No new Firebase dep (auth + firestore already via BOM 33.7.0). No
`lifecycle-process` (Compose lifecycle used). No new artifact for ModalBottomSheet.

## 9. Manual gates (you, console) — block the Google path only

1. Firebase Console (dwell-launcher) > Authentication > Sign-in method: **enable
   Google**; keep Email/Password and Anonymous enabled.
2. Debug **SHA-1**: `gradlew.bat signingReport`, copy the debug SHA1, paste into
   Project Settings > Your apps > Android app > SHA certificate fingerprints.
3. **Re-download `google-services.json`** into `app/` (now it has a `client_type 3`
   web client, so `R.string.default_web_client_id` exists and Google code compiles).

The email/password path and the entire account spine need none of these and ship
first.

## 10. Testing

JVM unit tests (fakes, no Android), following slice 1:
- `AuthRepositoryImpl` upgrade logic is thin over the Firebase SDK; cover the
  repository's result mapping with a faked Firebase seam if cheap, otherwise verify
  on device. The high-value unit tests are the **merge/sequencing** in the
  repository:
- `FavoritesRepositoryImpl.mergeLocalInto` pushes all local favorites via
  `putAll` (fake remote) and does not clear Room.
- The collision sequence (merge before reconcile) leaves the union, not the empty
  server set: with a fake remote pre-seeded with the existing account's favorites
  and Room holding anon-only favorites, after `mergeLocalInto` + `reconcile` the
  result is the union.
- `clearLocal` empties Room; sign-out sequence (clear before reconcile) ends empty
  for a fresh anon account.
- `reconcile`/`toggle` remain no-ops with null uid.

Manual on device (email path first):
- Favorite 2 wallpapers anonymously. Open the sheet, Create account with a new
  email. Favorites still present (uid preserved). More shows the email.
- Sign out. Favorites empty (clean anon slate). Sign in with the same email.
  Favorites return (reconcile from server).
- Collision: on a second fresh install/anon session, favorite something, then Sign
  in to the existing email account. Toast appears; the anon favorite is merged in
  alongside the account's favorites.
- After Google gates: repeat upgrade + sign-out + sign-in with Google.
- Cross-device: favorite on device A, open the app on device B, it appears (resume
  reconcile).
- Logcat clean: no PERMISSION_DENIED, no crash.

## 11. Files

New:
- `data/auth/UpgradeResult.kt`, `data/auth/AuthError.kt`
- `ui/auth/SignInViewModel.kt`, `ui/auth/SignInSheet.kt`
- `ui/auth/GoogleCredential.kt` (Compose/Activity-boundary helper) [2a-ii]
- Tests under `app/src/test/.../data/favorites/` and `.../data/auth/`

Changed:
- `data/auth/AuthRepository.kt` / `AuthRepositoryImpl.kt` (link/sign-in/sign-out,
  ensureUserDoc, inject FirebaseFirestore)
- `data/favorites/FavoritesRepository.kt` / `Impl.kt` (mergeLocalInto, clearLocal,
  Mutex + isUpgrading)
- `data/favorites/FavoritesRemoteSource.kt` / `Impl.kt` (putAll)
- `data/local/FavoriteDao.kt` (getAll)
- `ui/AppBootstrapViewModel.kt` (onResume), `ui/DwellApp.kt` (ON_START observer,
  sign-in sheet host, account state)
- `ui/screens/MoreScreen.kt` (account row)
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (3 deps)
- `res/values/strings.xml` (auth copy)
- `firestore.rules` (removeAds hardening)
- `app/google-services.json` (re-downloaded) [2a-ii, gate]

## 12. Decisions (resolved open questions)

- Sign-out -> clean-slate empty favorites for the new anon identity; prior account's
  favorites stay safe server-side. No confirmation dialog (simple).
- `removeAds` rules hardening ships in 2a (safe, locks the field before billing).
- Merge conflict resolution: last-write-wins on `addedAt` (acceptable).
- Collision is surfaced with a short toast (kept-your-favorites reassurance).
- No nonce on the Google option this slice.
