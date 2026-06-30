# Accounts Slice 2a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the silent anonymous session into a real account (email/password now, Google after a console gate) without losing favorites, with sign-out and resume sync.

**Architecture:** Anonymous→real uses `linkWithCredential` to preserve the uid (favorites kept with zero migration). On a returning-user sign-in (different uid) the anonymous favorites are snapshotted *before* the auth switch and merged up to the existing account *before* any reconcile (reconcile's `replaceAll` is the only destructive op). The favorites merge/sequencing core is pure-JVM and TDD'd; Firebase/Compose wiring is build + device verified.

**Tech Stack:** Kotlin, Compose, Hilt, Room, Firebase Auth + Firestore, Credential Manager (Google). Tests: JUnit4 + coroutines-test + Turbine.

**Spec:** `docs/superpowers/specs/2026-06-30-accounts-slice-2a-design.md`

**Build order:** Tasks 1–9 are **2a-i** (email/password + the whole account spine, no console gate). Tasks 10–12 are **2a-ii** (Google, blocked by the manual gates in Task 10).

**Branch:** create `phase-2a-accounts` off `main` first.

---

## File Structure

New:
- `data/auth/UpgradeResult.kt` — sealed result of an upgrade/sign-in
- `data/auth/AuthError.kt` — domain auth error enum
- `ui/auth/SignInUiState.kt` — sheet state
- `ui/auth/SignInViewModel.kt` — sequences link/sign-in → merge → reconcile
- `ui/auth/SignInSheet.kt` — ModalBottomSheet form
- `ui/auth/GoogleSignIn.kt` — Compose/Activity-boundary Credential Manager helper [2a-ii]
- Tests: `app/src/test/.../data/favorites/FavoritesMergeTest.kt`, `app/src/test/.../ui/auth/SignInViewModelTest.kt`

Modified:
- `data/local/FavoriteDao.kt` (add `getAll`)
- `data/favorites/FavoritesRemoteSource.kt` / `FavoritesRemoteSourceImpl.kt` (add `putAll`)
- `data/favorites/FavoritesRepository.kt` / `FavoritesRepositoryImpl.kt` (snapshot/mergeInto/clearLocal + Mutex)
- `data/auth/AuthRepository.kt` / `AuthRepositoryImpl.kt` (linkEmail/linkGoogle/signOut/ensureUserDoc, inject FirebaseFirestore)
- `ui/AppBootstrapViewModel.kt` (onResume)
- `ui/DwellApp.kt` (ON_START reconcile, sign-in sheet host, account state)
- `ui/screens/MoreScreen.kt` (account row)
- `gradle/libs.versions.toml`, `app/build.gradle.kts` (3 deps)
- `res/values/strings.xml`, `firestore.rules`
- `app/src/test/.../data/favorites/Fakes.kt` (add `getAll`, `putAll` to fakes)
- `app/google-services.json` (re-download) [2a-ii gate]

---

## Task 1: Add Credential Manager + googleid dependencies

These are libraries; they compile with no console setup (the Google *code* that needs `default_web_client_id` comes in Task 11). Adding them now de-risks.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions + libraries to the catalog**

In `gradle/libs.versions.toml` under `[versions]` add:

```toml
androidxCredentials = "1.6.0"
googleid = "1.1.1"
```

Under `[libraries]` add:

```toml
# Credential Manager + Google ID (sign-in)
androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "androidxCredentials" }
androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "androidxCredentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleid" }
```

- [ ] **Step 2: Wire them into the app module**

In `app/build.gradle.kts`, in `dependencies { }`, after the Firebase block add:

```kotlin
    // Sign-in (Credential Manager + Google ID)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Credential Manager + googleid deps for sign-in"
```

---

## Task 2: Favorites merge core (TDD)

The data-loss-safe primitives: snapshot local favorites, push a set up to a uid (non-destructive), clear local. Plus a `Mutex` serializing reconcile vs merge.

**Files:**
- Modify: `data/local/FavoriteDao.kt`
- Modify: `data/favorites/FavoritesRemoteSource.kt`, `FavoritesRemoteSourceImpl.kt`
- Modify: `data/favorites/FavoritesRepository.kt`, `FavoritesRepositoryImpl.kt`
- Modify (tests): `app/src/test/java/com/dwell/app/data/favorites/Fakes.kt`
- Test: `app/src/test/java/com/dwell/app/data/favorites/FavoritesMergeTest.kt`

- [ ] **Step 1: Add `getAll` to FavoriteDao**

In `FavoriteDao.kt`, add after `observeAll()`:

```kotlin
    /** One-shot snapshot of all favorites. Used to capture before an account switch. */
    @Query("SELECT * FROM favorites_cache ORDER BY addedAtMillis DESC")
    suspend fun getAll(): List<FavoriteEntity>
```

- [ ] **Step 2: Add `putAll` to the remote source interface**

In `FavoritesRemoteSource.kt`:

```kotlin
    /** Non-destructive batched upload (set + merge). Used to merge favorites into an account. */
    suspend fun putAll(uid: String, favorites: List<FavoriteRemote>)
```

- [ ] **Step 3: Extend the repository interface**

In `FavoritesRepository.kt`, add:

```kotlin
    /** Snapshot of the current local favorites, captured before an account switch. */
    suspend fun snapshotLocalFavorites(): List<FavoriteRemote>

    /** Merge a captured favorites set up into [uid]'s server favorites (non-destructive). */
    suspend fun mergeInto(uid: String, favorites: List<FavoriteRemote>)

    /** Clear the local favorites cache (used on sign-out before reconcile). */
    suspend fun clearLocal()
```

(`FavoriteRemote` is already in `data.favorites`.)

- [ ] **Step 4: Update the fakes for the new methods**

In `app/src/test/java/com/dwell/app/data/favorites/Fakes.kt`, add `getAll` to `FakeFavoriteDao` (inside the class):

```kotlin
    override suspend fun getAll(): List<FavoriteEntity> =
        state.value.sortedByDescending { it.addedAtMillis }
```

And add `putAll` to `FakeRemoteSource` (inside the class):

```kotlin
    override suspend fun putAll(uid: String, favorites: List<FavoriteRemote>) {
        if (failWrites) error("offline")
        val m = remote.getOrPut(uid) { mutableMapOf() }
        favorites.forEach { m[it.wallpaperId] = it.addedAtMillis }
    }
```

- [ ] **Step 5: Write the failing merge tests**

Create `app/src/test/java/com/dwell/app/data/favorites/FavoritesMergeTest.kt`:

```kotlin
package com.dwell.app.data.favorites

import com.dwell.app.data.local.FavoriteEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesMergeTest {

    private fun repo(
        dao: FakeFavoriteDao = FakeFavoriteDao(),
        wallpaperDao: FakeWallpaperDao = FakeWallpaperDao(),
        remote: FakeRemoteSource = FakeRemoteSource(),
        auth: FakeAuthRepository = FakeAuthRepository("u1"),
    ) = FavoritesRepositoryImpl(dao, wallpaperDao, remote, auth) { 1000L }

    @Test
    fun snapshotLocalFavorites_returnsRoomFavorites() = runTest {
        val dao = FakeFavoriteDao().apply {
            upsert(FavoriteEntity("a", 2L)); upsert(FavoriteEntity("b", 1L))
        }
        val sut = repo(dao = dao)

        val snap = sut.snapshotLocalFavorites()

        assertEquals(setOf("a", "b"), snap.map { it.wallpaperId }.toSet())
    }

    @Test
    fun mergeInto_pushesAllToRemote_withoutClearingRoom() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("b", 1L)) }
        val remote = FakeRemoteSource()
        val sut = repo(dao = dao, remote = remote)

        sut.mergeInto("existing", listOf(FavoriteRemote("b", 1L)))

        assertEquals(setOf("b"), remote.fetchAll("existing").map { it.wallpaperId }.toSet())
        assertTrue(dao.exists("b")) // Room not cleared
    }

    @Test
    fun collisionSequence_mergeBeforeReconcile_yieldsUnion() = runTest {
        // existing account already has "a" on the server; anon Room has "b"
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("b", 5L)) }
        val remote = FakeRemoteSource().apply { remote["existing"] = mutableMapOf("a" to 1L) }
        val auth = FakeAuthRepository("existing")
        val sut = repo(dao = dao, remote = remote, auth = auth)

        // capture anon favorites, then (auth already switched) merge up, then reconcile
        val snap = listOf(FavoriteRemote("b", 5L))
        sut.mergeInto("existing", snap)
        sut.reconcile()

        assertTrue(dao.exists("a"))
        assertTrue(dao.exists("b")) // union, "b" not lost
    }

    @Test
    fun clearLocal_emptiesRoom() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("x", 1L)) }
        val sut = repo(dao = dao)

        sut.clearLocal()

        assertFalse(dao.exists("x"))
    }

    @Test
    fun signOutSequence_clearThenReconcile_endsEmpty() = runTest {
        // fresh anon account has no server favorites
        val dao = FakeFavoriteDao().apply { upsert(FavoriteEntity("old", 1L)) }
        val remote = FakeRemoteSource()
        val auth = FakeAuthRepository("newAnon")
        val sut = repo(dao = dao, remote = remote, auth = auth)

        sut.clearLocal()
        sut.reconcile()

        assertFalse(dao.exists("old"))
        assertEquals(0, dao.getAll().size)
    }
}
```

- [ ] **Step 6: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.data.favorites.FavoritesMergeTest"`
Expected: FAIL — `snapshotLocalFavorites`/`mergeInto`/`clearLocal` not defined; `putAll` unresolved.

- [ ] **Step 7: Implement `putAll` in the Firestore source**

In `FavoritesRemoteSourceImpl.kt`, add the import `import com.google.firebase.firestore.SetOptions` and the method:

```kotlin
    override suspend fun putAll(uid: String, favorites: List<FavoriteRemote>) {
        if (favorites.isEmpty()) return
        // Chunk under the 500-write batch limit.
        favorites.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { fav ->
                val ref = collection(uid).document(fav.wallpaperId)
                batch.set(
                    ref,
                    mapOf(
                        "wallpaperId" to fav.wallpaperId,
                        "addedAt" to Timestamp(Date(fav.addedAtMillis)),
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
        }
    }
```

- [ ] **Step 8: Implement the repository methods + Mutex**

In `FavoritesRepositoryImpl.kt`, add imports:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

Add a field at the top of the class body:

```kotlin
    // Serializes the destructive reconcile against merge so a resume/launch reconcile
    // cannot run replaceAll between an account switch and the merge-up.
    private val syncMutex = Mutex()
```

Wrap the existing `reconcile()` body in the lock (change its body to):

```kotlin
    override suspend fun reconcile() = syncMutex.withLock {
        val uid = auth.currentUid() ?: return@withLock
        runCatching {
            val entities = remote.fetchAll(uid).map { FavoriteEntity(it.wallpaperId, it.addedAtMillis) }
            favoriteDao.replaceAll(entities)
        }
        Unit
    }
```

Add the new methods:

```kotlin
    override suspend fun snapshotLocalFavorites(): List<FavoriteRemote> =
        favoriteDao.getAll().map { FavoriteRemote(it.wallpaperId, it.addedAtMillis) }

    override suspend fun mergeInto(uid: String, favorites: List<FavoriteRemote>) = syncMutex.withLock {
        runCatching { remote.putAll(uid, favorites) }
        Unit
    }

    override suspend fun clearLocal() {
        favoriteDao.clear()
    }
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.data.favorites.FavoritesMergeTest"`
Expected: PASS — 5 tests.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/local/FavoriteDao.kt app/src/main/java/com/dwell/app/data/favorites/ app/src/test/java/com/dwell/app/data/favorites/
git commit -m "feat: add data-loss-safe favorites merge primitives (snapshot, mergeInto, clearLocal)"
```

---

## Task 3: Auth result types + email/sign-out repository

**Files:**
- Create: `data/auth/AuthError.kt`, `data/auth/UpgradeResult.kt`
- Modify: `data/auth/AuthRepository.kt`, `AuthRepositoryImpl.kt`

`AuthRepositoryImpl` is a thin wrapper over the Firebase SDK; verified by build + device. No unit test (the testable sequencing lives in Task 4's view model).

- [ ] **Step 1: Create the error enum**

`data/auth/AuthError.kt`:

```kotlin
package com.dwell.app.data.auth

/** Domain auth failure, mapped from Firebase at the repository boundary. */
enum class AuthError { INVALID_CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, INVALID_EMAIL, NETWORK, UNKNOWN }
```

- [ ] **Step 2: Create the result type**

`data/auth/UpgradeResult.kt`:

```kotlin
package com.dwell.app.data.auth

/** Outcome of an upgrade/sign-in attempt. */
sealed interface UpgradeResult {
    /** The anonymous user was linked; uid preserved, favorites already owned. */
    data class Linked(val uid: String) : UpgradeResult
    /** The credential belonged to an existing account; we adopted it (caller must merge). */
    data class SignedInExisting(val uid: String) : UpgradeResult
    data class Error(val error: AuthError) : UpgradeResult
}
```

- [ ] **Step 3: Extend the AuthRepository interface**

In `AuthRepository.kt`, add:

```kotlin
    /**
     * Create-account mode links email/password to the anonymous user (uid kept).
     * Sign-in mode signs in to an existing account (caller merges favorites).
     */
    suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult

    /** Link/sign-in with a Google ID token. */
    suspend fun linkGoogle(idToken: String): UpgradeResult

    /** Whether the current user is anonymous (no real account). */
    fun isAnonymous(): Boolean

    /** Email of the current user, or null when anonymous. */
    fun currentEmail(): String?

    /** Sign out, then immediately re-create an anonymous session (never null uid). */
    suspend fun signOut()
```

- [ ] **Step 4: Implement in AuthRepositoryImpl**

In `AuthRepositoryImpl.kt`, change the constructor to inject Firestore and add imports:

```kotlin
import com.dwell.app.data.auth.AuthError
import com.dwell.app.data.auth.UpgradeResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
```

Constructor:

```kotlin
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : AuthRepository {
```

Add the methods (after `ensureSignedIn`):

```kotlin
    override fun isAnonymous(): Boolean = auth.currentUser?.isAnonymous ?: true

    override fun currentEmail(): String? = auth.currentUser?.email?.takeIf { it.isNotBlank() }

    override suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult {
        val cred = EmailAuthProvider.getCredential(email, password)
        return try {
            val user = auth.currentUser
            if (createAccount && user != null && user.isAnonymous) {
                user.linkWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.Linked(user.uid)
            } else {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            }
        } catch (e: Throwable) {
            UpgradeResult.Error(e.toAuthError())
        }
    }

    override suspend fun linkGoogle(idToken: String): UpgradeResult {
        val cred = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val user = auth.currentUser
            if (user != null && user.isAnonymous) {
                user.linkWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.Linked(user.uid)
            } else {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            // Google credential already on another account: adopt it.
            try {
                auth.signInWithCredential(cred).await()
                ensureUserDoc()
                UpgradeResult.SignedInExisting(auth.currentUser!!.uid)
            } catch (e2: Throwable) {
                UpgradeResult.Error(e2.toAuthError())
            }
        } catch (e: Throwable) {
            UpgradeResult.Error(e.toAuthError())
        }
    }

    override suspend fun signOut() {
        auth.signOut()
        auth.signInAnonymously().await()
    }

    private suspend fun ensureUserDoc() {
        val user = auth.currentUser ?: return
        if (user.isAnonymous) return
        val ref = firestore.collection("users").document(user.uid)
        val providerId = user.providerData
            .firstOrNull { it.providerId != "firebase" }?.providerId
        val data = mutableMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "provider" to when (providerId) {
                GoogleAuthProvider.PROVIDER_ID -> "google"
                else -> "password"
            },
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!ref.get().await().exists()) {
            data["createdAt"] = FieldValue.serverTimestamp()
        }
        ref.set(data, SetOptions.merge()).await()
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError.INVALID_EMAIL
            else AuthError.INVALID_CREDENTIALS
        is FirebaseNetworkException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }
```

- [ ] **Step 5: Update the test fake so the interface still compiles**

Extending `AuthRepository` breaks the slice-1 `FakeAuthRepository` in
`app/src/test/java/com/dwell/app/data/favorites/Fakes.kt` (missing overrides),
which would fail the whole test compile. Add the import
`import com.dwell.app.data.auth.UpgradeResult` and add these overrides inside
`FakeAuthRepository`:

```kotlin
    override fun isAnonymous(): Boolean = current == null
    override fun currentEmail(): String? = null
    override suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult =
        UpgradeResult.Linked(current ?: "u1")
    override suspend fun linkGoogle(idToken: String): UpgradeResult =
        UpgradeResult.Linked(current ?: "u1")
    override suspend fun signOut() {}
```

- [ ] **Step 6: Build main + tests**

Run: `./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (main compiles; existing favorites tests still pass with the extended fake).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/auth/ app/src/test/java/com/dwell/app/data/favorites/Fakes.kt
git commit -m "feat: add email/Google upgrade, sign-out, and user-doc write to AuthRepository"
```

---

## Task 4: SignInViewModel (TDD)

Sequences the data-loss-safe flow: snapshot → link/sign-in → (merge on collision) → reconcile.

**Files:**
- Create: `ui/auth/SignInUiState.kt`, `ui/auth/SignInViewModel.kt`
- Test: `app/src/test/java/com/dwell/app/ui/auth/SignInViewModelTest.kt`

- [ ] **Step 1: Create the UI state**

`ui/auth/SignInUiState.kt`:

```kotlin
package com.dwell.app.ui.auth

import com.dwell.app.data.auth.AuthError

enum class SignInMode { SignIn, Create }

data class SignInUiState(
    val mode: SignInMode = SignInMode.SignIn,
    val email: String = "",
    val password: String = "",
    val inProgress: Boolean = false,
    val inlineError: AuthError? = null,
    val done: Boolean = false,        // success -> dismiss the sheet
    val mergedExisting: Boolean = false, // show the "kept your favorites" toast
)
```

- [ ] **Step 2: Write the failing view-model test**

Create `app/src/test/java/com/dwell/app/ui/auth/SignInViewModelTest.kt`:

```kotlin
package com.dwell.app.ui.auth

import com.dwell.app.data.auth.AuthError
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import com.dwell.app.data.favorites.FavoriteRemote
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuth(var result: UpgradeResult) : AuthRepository {
        override val uid: Flow<String?> = MutableStateFlow("u1")
        override fun currentUid(): String? = "u1"
        override suspend fun ensureSignedIn() {}
        override fun isAnonymous(): Boolean = true
        override fun currentEmail(): String? = null
        override suspend fun linkEmail(email: String, password: String, createAccount: Boolean) = result
        override suspend fun linkGoogle(idToken: String) = result
        override suspend fun signOut() {}
    }

    private class RecordingFavorites(val snapshot: List<FavoriteRemote>) : FavoritesRepository {
        val calls = mutableListOf<String>()
        override fun observeFavoriteIds(): Flow<Set<String>> = MutableStateFlow(emptySet())
        override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> = MutableStateFlow(emptyList())
        override suspend fun toggle(wallpaper: Wallpaper) {}
        override suspend fun reconcile() { calls.add("reconcile") }
        override suspend fun snapshotLocalFavorites(): List<FavoriteRemote> { calls.add("snapshot"); return snapshot }
        override suspend fun mergeInto(uid: String, favorites: List<FavoriteRemote>) { calls.add("mergeInto:$uid:${favorites.size}") }
        override suspend fun clearLocal() { calls.add("clearLocal") }
    }

    @Test
    fun linked_reconcilesOnly_noMerge() = runTest {
        val fav = RecordingFavorites(listOf(FavoriteRemote("b", 1L)))
        val vm = SignInViewModel(FakeAuth(UpgradeResult.Linked("u1")), fav)
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("secret1"); vm.setMode(SignInMode.Create)

        vm.submitEmail()

        assertTrue(vm.uiState.value.done)
        assertEquals(listOf("snapshot", "reconcile"), fav.calls)
        assertTrue(fav.calls.none { it.startsWith("mergeInto") })
    }

    @Test
    fun signedInExisting_mergesThenReconciles_inOrder() = runTest {
        val fav = RecordingFavorites(listOf(FavoriteRemote("b", 1L)))
        val vm = SignInViewModel(FakeAuth(UpgradeResult.SignedInExisting("existing")), fav)
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("secret1"); vm.setMode(SignInMode.SignIn)

        vm.submitEmail()

        // snapshot must come before merge, and merge before reconcile
        val merge = fav.calls.indexOf("mergeInto:existing:1")
        val recon = fav.calls.indexOf("reconcile")
        val snap = fav.calls.indexOf("snapshot")
        assertTrue(snap in 0 until merge)
        assertTrue(merge in 0 until recon)
        assertTrue(vm.uiState.value.done)
        assertTrue(vm.uiState.value.mergedExisting)
    }

    @Test
    fun error_setsInlineError_noDone() = runTest {
        val fav = RecordingFavorites(emptyList())
        val vm = SignInViewModel(FakeAuth(UpgradeResult.Error(AuthError.INVALID_CREDENTIALS)), fav)
        vm.onEmailChange("a@b.com"); vm.onPasswordChange("x")

        vm.submitEmail()

        assertEquals(AuthError.INVALID_CREDENTIALS, vm.uiState.value.inlineError)
        assertEquals(false, vm.uiState.value.done)
        assertTrue(fav.calls.none { it == "reconcile" })
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.ui.auth.SignInViewModelTest"`
Expected: FAIL — `SignInViewModel` not defined.

- [ ] **Step 4: Implement the view model**

`ui/auth/SignInViewModel.kt`:

```kotlin
package com.dwell.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun setMode(mode: SignInMode) = _uiState.update { it.copy(mode = mode, inlineError = null) }
    fun onEmailChange(v: String) = _uiState.update { it.copy(email = v, inlineError = null) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v, inlineError = null) }

    fun submitEmail() {
        val s = _uiState.value
        if (s.inProgress) return
        _uiState.update { it.copy(inProgress = true, inlineError = null) }
        viewModelScope.launch {
            // Capture anon favorites BEFORE any account switch (data-loss protocol).
            val snapshot = favorites.snapshotLocalFavorites()
            val result = auth.linkEmail(s.email.trim(), s.password, s.mode == SignInMode.Create)
            applyResult(result, snapshot)
        }
    }

    /** Called by the UI after a Google id token is obtained at the Activity boundary. */
    fun submitGoogle(idToken: String) {
        if (_uiState.value.inProgress) return
        _uiState.update { it.copy(inProgress = true, inlineError = null) }
        viewModelScope.launch {
            val snapshot = favorites.snapshotLocalFavorites()
            val result = auth.linkGoogle(idToken)
            applyResult(result, snapshot)
        }
    }

    private suspend fun applyResult(
        result: UpgradeResult,
        snapshot: List<com.dwell.app.data.favorites.FavoriteRemote>,
    ) {
        when (result) {
            is UpgradeResult.Linked -> {
                favorites.reconcile()
                _uiState.update { it.copy(inProgress = false, done = true) }
            }
            is UpgradeResult.SignedInExisting -> {
                favorites.mergeInto(result.uid, snapshot)
                favorites.reconcile()
                _uiState.update { it.copy(inProgress = false, done = true, mergedExisting = true) }
            }
            is UpgradeResult.Error -> {
                _uiState.update { it.copy(inProgress = false, inlineError = result.error) }
            }
        }
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.ui.auth.SignInViewModelTest"`
Expected: PASS — 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/auth/ app/src/test/java/com/dwell/app/ui/auth/
git commit -m "feat: add SignInViewModel sequencing upgrade -> merge -> reconcile with tests"
```

---

## Task 5: Sign-in sheet UI + strings

**Files:**
- Create: `ui/auth/SignInSheet.kt`
- Modify: `res/values/strings.xml`

- [ ] **Step 1: Add auth strings**

In `res/values/strings.xml`, add inside `<resources>`:

```xml
    <!-- Auth -->
    <string name="account_sign_in">Sign in</string>
    <string name="account_create">Create account</string>
    <string name="account_sign_out">Sign out</string>
    <string name="account_email_hint">Email</string>
    <string name="account_password_hint">Password</string>
    <string name="account_continue_google">Continue with Google</string>
    <string name="account_or">or</string>
    <string name="account_merged_toast">Signed in to your existing account. Kept your saved wallpapers.</string>
    <string name="account_err_invalid">Email and password don\'t match. Check them and try again.</string>
    <string name="account_err_email_in_use">That email already has an account. Sign in instead.</string>
    <string name="account_err_weak_password">Use at least 6 characters for your password.</string>
    <string name="account_err_invalid_email">That email address isn\'t valid.</string>
    <string name="account_err_network">Check your connection and try again.</string>
    <string name="account_err_unknown">Couldn\'t sign you in. Try again.</string>
    <string name="cd_toggle_password">Show or hide password</string>
```

- [ ] **Step 2: Create the sheet**

`ui/auth/SignInSheet.kt`:

```kotlin
package com.dwell.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.R
import com.dwell.app.data.auth.AuthError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    onSignedIn: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    LaunchedEffect(state.done) {
        if (state.done) {
            if (state.mergedExisting) {
                Toast.makeText(context, R.string.account_merged_toast, Toast.LENGTH_LONG).show()
            }
            onSignedIn()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (state.mode == SignInMode.Create) {
                    stringResource(R.string.account_create)
                } else {
                    stringResource(R.string.account_sign_in)
                },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            ModeToggle(mode = state.mode, onSelect = viewModel::setMode)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.account_email_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.account_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.inlineError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(errorRes(state.inlineError!!)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::submitEmail,
                enabled = !state.inProgress && state.email.isNotBlank() && state.password.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.inProgress) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.height(20.dp))
                } else {
                    Text(
                        text = if (state.mode == SignInMode.Create) stringResource(R.string.account_create) else stringResource(R.string.account_sign_in),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // Google button is added in Task 11 (gated on google-services.json).
        }
    }
}

@Composable
private fun ModeToggle(mode: SignInMode, onSelect: (SignInMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(0.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeChip(stringResource(R.string.account_sign_in), mode == SignInMode.SignIn, Modifier.weight(1f)) { onSelect(SignInMode.SignIn) }
        ModeChip(stringResource(R.string.account_create), mode == SignInMode.Create, Modifier.weight(1f)) { onSelect(SignInMode.Create) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = modifier.fillMaxSize(),
        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp), modifier = modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun errorRes(e: AuthError): Int = when (e) {
    AuthError.INVALID_CREDENTIALS -> R.string.account_err_invalid
    AuthError.EMAIL_IN_USE -> R.string.account_err_email_in_use
    AuthError.WEAK_PASSWORD -> R.string.account_err_weak_password
    AuthError.INVALID_EMAIL -> R.string.account_err_invalid_email
    AuthError.NETWORK -> R.string.account_err_network
    AuthError.UNKNOWN -> R.string.account_err_unknown
}
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/auth/SignInSheet.kt app/src/main/res/values/strings.xml
git commit -m "feat: add email sign-in/create ModalBottomSheet"
```

---

## Task 6: Account row in More + sheet host + sign-out

**Files:**
- Modify: `ui/screens/MoreScreen.kt`
- Modify: `ui/DwellApp.kt`

- [ ] **Step 1: Add an account row + a sign-out hook to MoreScreen**

Replace `MoreScreen.kt` body with a version that shows account state. Add a small account view model and wire two rows:

`ui/screens/MoreScreen.kt`:

```kotlin
package com.dwell.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwell.app.R

@Composable
fun MoreScreen(
    isSignedIn: Boolean,
    email: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (isSignedIn) {
            AccountRow(email = email.orEmpty(), onSignOut = onSignOut)
        } else {
            MoreRow(
                iconRes = R.drawable.ic_heart_outline,
                label = stringResource(R.string.account_sign_in),
                onClick = onSignIn,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        MoreRow(
            iconRes = R.drawable.ic_heart_outline,
            label = stringResource(R.string.favorites_title),
            onClick = onOpenFavorites,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun AccountRow(email: String, onSignOut: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = stringResource(R.string.account_sign_out),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onSignOut).padding(8.dp),
        )
    }
}

@Composable
private fun MoreRow(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
```

- [ ] **Step 2: Add an account view model**

Create `ui/auth/AccountViewModel.kt`:

```kotlin
package com.dwell.app.ui.auth

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountState(val isSignedIn: Boolean, val email: String?)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState(!auth.isAnonymous(), auth.currentEmail()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        auth.uid.onEach {
            _state.value = AccountState(!auth.isAnonymous(), auth.currentEmail())
        }.launchIn(viewModelScope)
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            favorites.clearLocal()
            favorites.reconcile()
        }
    }
}
```

- [ ] **Step 3: Host the sheet and wire MoreScreen in DwellApp**

In `DwellApp.kt`, add imports:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.ui.auth.AccountViewModel
import com.dwell.app.ui.auth.SignInSheet
```

In `MainShell`, add an account VM, a sheet flag, and pass account state to `MoreScreen`. Replace the `MainShell` composable's body top and the `MoreScreen` call:

```kotlin
@Composable
private fun MainShell(
    onWallpaperClick: (String) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val accountVm: AccountViewModel = hiltViewModel()
    val account by accountVm.state.collectAsStateWithLifecycle()
    var showSignIn by remember { mutableStateOf(false) }

    if (showSignIn) {
        SignInSheet(
            onSignedIn = { showSignIn = false },
            onDismiss = { showSignIn = false },
        )
    }

    Scaffold(
        bottomBar = { /* unchanged */
            DwellBottomBar(
                currentRoute = currentRoute,
                onSelect = { destination ->
                    if (destination.route != currentRoute) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DwellDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(DwellDestination.WALLPAPERS.route) {
                WallpapersScreen(onWallpaperClick = onWallpaperClick)
            }
            composable(DwellDestination.WIDGETS.route) { WidgetsScreen() }
            composable(DwellDestination.MORE.route) {
                MoreScreen(
                    isSignedIn = account.isSignedIn,
                    email = account.email,
                    onSignIn = { showSignIn = true },
                    onSignOut = accountVm::signOut,
                    onOpenFavorites = onOpenFavorites,
                )
            }
        }
    }
}
```

(`hiltViewModel` is already imported in `DwellApp.kt`.)

- [ ] **Step 4: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt app/src/main/java/com/dwell/app/ui/auth/AccountViewModel.kt app/src/main/java/com/dwell/app/ui/DwellApp.kt
git commit -m "feat: account row in More with sign-in sheet and sign-out"
```

---

## Task 7: Reconcile favorites on resume

**Files:**
- Modify: `ui/AppBootstrapViewModel.kt`
- Modify: `ui/DwellApp.kt`

- [ ] **Step 1: Add an onResume reconcile to the bootstrap VM**

In `AppBootstrapViewModel.kt`, add:

```kotlin
    /** Re-pull favorites when the app comes to the foreground. Safe no-op offline/anonymous. */
    fun onResume() {
        viewModelScope.launch { favorites.reconcile() }
    }
```

- [ ] **Step 2: Fire it on ON_START in DwellApp**

In `DwellApp.kt`, add imports:

```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

In `DwellApp()`, after the existing `LaunchedEffect(Unit) { bootstrap.start() }`, add:

```kotlin
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) bootstrap.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/AppBootstrapViewModel.kt app/src/main/java/com/dwell/app/ui/DwellApp.kt
git commit -m "feat: reconcile favorites on app resume (ON_START)"
```

---

## Task 8: Firestore rules — lock removeAds

**Files:**
- Modify: `firestore.rules`

- [ ] **Step 1: Harden the users rule**

Replace the `match /users/{uid} { ... }` block in `firestore.rules` with:

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

- [ ] **Step 2: Deploy (requires `firebase login`)**

Run: `firebase deploy --only firestore:rules`
Expected: "Deploy complete!" If the CLI is not logged in, run `firebase login` first, or paste the rules in the console.

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git commit -m "feat: lock removeAds field server-side in firestore rules"
```

---

## Task 9: Device verification — email path (2a-i)

**Files:** none.

- [ ] **Step 1: Full unit-test pass**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Install**

Run: `./gradlew.bat :app:installDebug`
Expected: Installed on 1 device. (If the device is offline: `adb reconnect offline`, then retry.)

- [ ] **Step 3: Verify by hand**

1. Force-stop and open the app. Favorite 2 wallpapers (hearts fill).
2. More tab → "Sign in" row → sheet opens. Switch to "Create account", enter a new email + a 6+ char password, tap Create account. Sheet dismisses; More shows the email.
3. The 2 favorites are still in More → Favorites (uid preserved, no loss).
4. Sign out (More). Favorites are now empty (clean anon slate).
5. Sign in (mode "Sign in") with the same email + password. The 2 favorites return.
6. Collision/merge: with the account existing, do a fresh anon session (clear app data OR uninstall+reinstall to reset the anon uid), favorite 1 different wallpaper, then Sign in to the same email account. A toast appears ("Kept your saved wallpapers"); More → Favorites shows the union.
7. Logcat clean: `adb logcat -d | findstr /C:"PERMISSION_DENIED" /C:"FATAL"` shows nothing for the app.

- [ ] **Step 4: No commit (verification only). If a bug is found, fix it as its own task.**

---

## Task 10: Google manual gates (you, console) — blocks Task 11

**Files:** `app/google-services.json` (re-downloaded).

- [ ] **Step 1: Enable the Google provider**

Firebase Console (dwell-launcher) → Authentication → Sign-in method → enable **Google**. Keep Email/Password and Anonymous enabled.

- [ ] **Step 2: Add the debug SHA-1**

Run: `./gradlew.bat signingReport`
Copy the **debug** variant `SHA1`. In Firebase Console → Project Settings → Your apps → Android app (`com.dwell.app`) → "Add fingerprint" → paste the SHA-1.

- [ ] **Step 3: Re-download google-services.json**

Download the updated `google-services.json` and replace `app/google-services.json`. Verify it now contains an `oauth_client` entry with `"client_type": 3` (the web client). After this, `R.string.default_web_client_id` exists.

- [ ] **Step 4: Confirm the resource resolves**

Run: `./gradlew.bat :app:processDebugResources`
Expected: BUILD SUCCESSFUL (the generated `default_web_client_id` string is present).

---

## Task 11: Google sign-in wiring (2a-ii)

**Files:**
- Create: `ui/auth/GoogleSignIn.kt`
- Modify: `ui/auth/SignInSheet.kt`

- [ ] **Step 1: Add the Credential Manager helper**

`ui/auth/GoogleSignIn.kt`:

```kotlin
package com.dwell.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.dwell.app.R

/**
 * Drives the system Google chooser and returns the ID token, or null if the user
 * dismissed it / no Google account is available. Must be called with an Activity
 * context (the system sheet needs one).
 */
suspend fun getGoogleIdToken(context: Context): String? {
    val option = GetSignInWithGoogleOption.Builder(
        context.getString(R.string.default_web_client_id),
    ).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val response = CredentialManager.create(context).getCredential(context, request)
        val cred = response.credential
        if (cred is com.google.android.libraries.identity.googleid.GoogleIdTokenCredential ||
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(cred.data).idToken
        } else {
            null
        }
    } catch (e: Exception) {
        null // NoCredentialException / user cancelled / no provider
    }
}
```

- [ ] **Step 2: Add the Google button to the sheet**

In `SignInSheet.kt`, add imports (LocalContext, OutlinedButton, fillMaxWidth are already imported from Task 5 — add only these two):

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
```

Inside the `Column`, after the primary `Button` (and before the closing brace of the `Column`), add:

```kotlin
            Spacer(Modifier.height(12.dp))
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val token = getGoogleIdToken(context)
                        if (token != null) viewModel.submitGoogle(token)
                    }
                },
                enabled = !state.inProgress,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.account_continue_google), style = MaterialTheme.typography.labelLarge)
            }
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (now that `default_web_client_id` exists from Task 10).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/auth/GoogleSignIn.kt app/src/main/java/com/dwell/app/ui/auth/SignInSheet.kt app/google-services.json
git commit -m "feat: add Google sign-in via Credential Manager"
```

---

## Task 12: Device verification — Google path (2a-ii)

**Files:** none.

- [ ] **Step 1: Install**

Run: `./gradlew.bat :app:installDebug`

- [ ] **Step 2: Verify by hand (emulator/device with Google Play + a Google account)**

1. Fresh anon session, favorite 1 wallpaper.
2. More → Sign in → "Continue with Google" → pick an account.
3. Sheet dismisses; More shows the Google email; the favorite is preserved (link) or merged (if that Google account already had a Dwell account).
4. Sign out, then sign in with Google again → favorites return.
5. Logcat clean (no PERMISSION_DENIED, no FATAL).

- [ ] **Step 3: No commit (verification only).**

---

## Done

At the end the slice provides email/password + Google accounts, anonymous→real upgrade with no favorites loss, a merge-up fallback for existing accounts, sign-out, resume sync, and a locked `removeAds` field. Deferred: settings/theme + settings sync (slice 2b), account deletion + Cloud Functions (later).
