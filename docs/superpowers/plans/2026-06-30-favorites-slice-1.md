# Favorites Slice 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the wallpaper heart real — anonymous auth on launch, offline-first favorites, and two surfaces (Saved chip + More > Favorites) to browse saved wallpapers.

**Architecture:** Room is the UI source of truth (observed via Flow); Firestore is durable write-through backing, reconciled one-shot on launch. Firestore and Auth sit behind thin seam interfaces so the favorites repository is pure-JVM testable with fakes.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Firebase Auth + Firestore, Coroutines/Flow. Tests: JUnit4 + kotlinx-coroutines-test + Turbine (JVM unit tests, no Android instrumentation).

**Testing strategy:** TDD the pure-JVM logic — `FavoritesRepositoryImpl` and the favorites-mode state in `WallpapersViewModel` — using fakes for the DAO, the Firestore seam, and auth. Android wiring (auth bootstrap, DI, Compose screens, drawables, rules) is verified by build + on-device run, matching how this project is verified. Each task ends in a commit.

**Spec:** `docs/superpowers/specs/2026-06-30-favorites-slice-1-design.md`

**Branch:** continue on `phase-1-apply` (or branch `phase-2-favorites` off it first).

---

## File Structure

New:
- `data/auth/AuthRepository.kt` — auth seam (interface)
- `data/auth/AuthRepositoryImpl.kt` — FirebaseAuth-backed impl
- `data/util/NowProvider.kt` — injectable clock for testable timestamps
- `data/local/FavoriteEntity.kt` — Room entity + mappers
- `data/local/FavoriteDao.kt` — favorites DAO
- `data/favorites/FavoriteRemote.kt` — remote DTO
- `data/favorites/FavoritesRemoteSource.kt` — Firestore seam (interface)
- `data/favorites/FavoritesRemoteSourceImpl.kt` — Firestore impl
- `data/favorites/FavoritesRepository.kt` — favorites seam (interface)
- `data/favorites/FavoritesRepositoryImpl.kt` — Room-first impl
- `ui/favorites/FavoritesScreen.kt` — favorites grid screen (More entry)
- `res/drawable/ic_heart_filled.xml`
- Tests: `app/src/test/java/com/dwell/app/data/favorites/FavoritesRepositoryImplTest.kt`,
  `app/src/test/java/com/dwell/app/data/favorites/Fakes.kt`,
  `app/src/test/java/com/dwell/app/ui/wallpapers/WallpapersViewModelFavoritesTest.kt`

Modified:
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — test deps
- `data/local/WallpaperDao.kt` — add `getByIds`
- `data/local/DwellDatabase.kt` — add entity + bump version
- `di/DataModule.kt` — provide FirebaseAuth, FavoriteDao, NowProvider
- `di/RepositoryModule.kt` — bind auth + remote source + favorites repo
- `ui/preview/PreviewUiState.kt`, `PreviewViewModel.kt`, `PreviewScreen.kt`
- `ui/wallpapers/WallpapersUiState.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`
- `ui/wallpapers/components/CategoryChipRow.kt`
- `ui/screens/MoreScreen.kt`, `ui/DwellApp.kt`
- `res/values/strings.xml`, `firestore.rules`

---

## Task 1: JVM test infrastructure

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/dwell/app/SmokeTest.kt`

- [ ] **Step 1: Add version + library entries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
junit = "4.13.2"
coroutinesTest = "1.9.0"
turbine = "1.2.0"
```

Under `[libraries]` add:

```toml
# Unit testing (JVM)
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

- [ ] **Step 2: Wire test deps into the app module**

In `app/build.gradle.kts`, inside `dependencies { ... }`, after the `debugImplementation(libs.androidx.ui.tooling)` line add:

```kotlin
    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
```

- [ ] **Step 3: Write the smoke test**

Create `app/src/test/java/com/dwell/app/SmokeTest.kt`:

```kotlin
package com.dwell.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun testInfraRuns() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 4: Run it to prove the test source set works**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.SmokeTest"`
Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test/java/com/dwell/app/SmokeTest.kt
git commit -m "test: add JVM unit-test deps (junit, coroutines-test, turbine)"
```

---

## Task 2: Favorites cache (Room entity, DAO, DB)

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/local/FavoriteEntity.kt`
- Create: `app/src/main/java/com/dwell/app/data/local/FavoriteDao.kt`
- Modify: `app/src/main/java/com/dwell/app/data/local/WallpaperDao.kt`
- Modify: `app/src/main/java/com/dwell/app/data/local/DwellDatabase.kt`

No unit test here — Room DAOs need instrumentation. The DAO is exercised through the repository tests in Task 5 via a fake. Verification is a successful KSP build.

- [ ] **Step 1: Create the entity**

Create `FavoriteEntity.kt`:

```kotlin
package com.dwell.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local copy of one favorite (Backend Schema 6, favorites_cache). The wallpaper
 * id is the key, matching the Firestore favorite doc id, so a toggle is a single
 * write with no query. addedAtMillis drives newest-saved-first ordering.
 */
@Entity(tableName = "favorites_cache")
data class FavoriteEntity(
    @PrimaryKey val wallpaperId: String,
    val addedAtMillis: Long,
)
```

- [ ] **Step 2: Create the DAO**

Create `FavoriteDao.kt`:

```kotlin
package com.dwell.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    /** Saved favorites, newest first. Observed by the UI. */
    @Query("SELECT * FROM favorites_cache ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites_cache WHERE wallpaperId = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites_cache WHERE wallpaperId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorites_cache")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    /** Replace the whole cache with the reconciled remote set, atomically. */
    @Transaction
    suspend fun replaceAll(favorites: List<FavoriteEntity>) {
        clear()
        insertAll(favorites)
    }
}
```

- [ ] **Step 3: Add `getByIds` to WallpaperDao**

In `WallpaperDao.kt`, add after the existing `getById` query:

```kotlin
    /** Cached wallpapers for a set of ids, any order. Used to render favorites. */
    @Query("SELECT * FROM wallpaper_cache WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WallpaperEntity>
```

- [ ] **Step 4: Register the entity and bump the DB version**

Replace the `@Database(...)` annotation and the abstract methods in `DwellDatabase.kt`:

```kotlin
@Database(
    entities = [WallpaperEntity::class, CategoryEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DwellDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "dwell.db"
    }
}
```

(`fallbackToDestructiveMigration` is already set in `DataModule`, so the version bump wipes the dev cache cleanly.)

- [ ] **Step 5: Provide the new DAO in DI**

In `DataModule.kt`, add after `provideCategoryDao`:

```kotlin
    @Provides
    fun provideFavoriteDao(database: DwellDatabase): FavoriteDao = database.favoriteDao()
```

- [ ] **Step 6: Build to verify Room codegen**

Run: `./gradlew.bat :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL (Room generates DAO impls for the new queries).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/local/ app/src/main/java/com/dwell/app/di/DataModule.kt
git commit -m "feat: add favorites_cache Room entity, DAO, and getByIds"
```

---

## Task 3: NowProvider (injectable clock)

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/util/NowProvider.kt`
- Modify: `app/src/main/java/com/dwell/app/di/DataModule.kt`

- [ ] **Step 1: Create the provider**

Create `NowProvider.kt`:

```kotlin
package com.dwell.app.data.util

/**
 * Wall-clock seam so timestamp-producing code stays unit-testable. Production
 * uses the system clock; tests inject a fixed value.
 */
fun interface NowProvider {
    fun nowMillis(): Long
}
```

- [ ] **Step 2: Provide it in DI**

In `DataModule.kt`, add the import `import com.dwell.app.data.util.NowProvider` and inside the `object DataModule`:

```kotlin
    @Provides
    @Singleton
    fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/util/NowProvider.kt app/src/main/java/com/dwell/app/di/DataModule.kt
git commit -m "feat: add NowProvider clock seam"
```

---

## Task 4: Auth repository (anonymous sign-in)

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/auth/AuthRepository.kt`
- Create: `app/src/main/java/com/dwell/app/data/auth/AuthRepositoryImpl.kt`
- Modify: `app/src/main/java/com/dwell/app/di/DataModule.kt`
- Modify: `app/src/main/java/com/dwell/app/di/RepositoryModule.kt`

Auth wraps the Firebase SDK; it is verified on-device (logcat shows an anonymous uid). No unit test.

- [ ] **Step 1: Create the interface**

Create `AuthRepository.kt`:

```kotlin
package com.dwell.app.data.auth

import kotlinx.coroutines.flow.Flow

/**
 * Owns the Firebase Auth session. The app signs in anonymously on launch so
 * every install has a uid for favorites, with no login wall.
 */
interface AuthRepository {

    /** Current user id, null when signed out. Emits on auth state changes. */
    val uid: Flow<String?>

    /** Current user id right now, or null. */
    fun currentUid(): String?

    /** Sign in anonymously if there is no current user. Idempotent. */
    suspend fun ensureSignedIn()
}
```

- [ ] **Step 2: Create the impl**

Create `AuthRepositoryImpl.kt`:

```kotlin
package com.dwell.app.data.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }
}
```

- [ ] **Step 3: Provide FirebaseAuth in DI**

In `DataModule.kt`, add `import com.google.firebase.auth.FirebaseAuth` and `import com.google.firebase.auth.ktx.auth` is NOT needed; use the instance getter:

```kotlin
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
```

- [ ] **Step 4: Bind the auth repository**

In `RepositoryModule.kt`, add imports and a bind inside the abstract class:

```kotlin
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.AuthRepositoryImpl
```

```kotlin
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
```

- [ ] **Step 5: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/auth/ app/src/main/java/com/dwell/app/di/
git commit -m "feat: add AuthRepository with anonymous sign-in"
```

---

## Task 5: Favorites repository (TDD)

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/favorites/FavoriteRemote.kt`
- Create: `app/src/main/java/com/dwell/app/data/favorites/FavoritesRemoteSource.kt`
- Create: `app/src/main/java/com/dwell/app/data/favorites/FavoritesRepository.kt`
- Create: `app/src/main/java/com/dwell/app/data/favorites/FavoritesRepositoryImpl.kt`
- Test: `app/src/test/java/com/dwell/app/data/favorites/Fakes.kt`
- Test: `app/src/test/java/com/dwell/app/data/favorites/FavoritesRepositoryImplTest.kt`

- [ ] **Step 1: Define the seams and the repository interface**

Create `FavoriteRemote.kt`:

```kotlin
package com.dwell.app.data.favorites

/** One favorite as stored remotely. */
data class FavoriteRemote(val wallpaperId: String, val addedAtMillis: Long)
```

Create `FavoritesRemoteSource.kt`:

```kotlin
package com.dwell.app.data.favorites

/** Firestore access for the favorites subcollection. Behind a seam so the
 *  repository is testable with a fake. */
interface FavoritesRemoteSource {
    suspend fun fetchAll(uid: String): List<FavoriteRemote>
    suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long)
    suspend fun remove(uid: String, wallpaperId: String)
}
```

Create `FavoritesRepository.kt`:

```kotlin
package com.dwell.app.data.favorites

import com.dwell.app.data.model.Wallpaper
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {

    /** Ids of saved wallpapers. Drives heart fill state. */
    fun observeFavoriteIds(): Flow<Set<String>>

    /** Saved wallpapers, newest-saved first, joined to the wallpaper cache. */
    fun observeFavoriteWallpapers(): Flow<List<Wallpaper>>

    /** Add if absent, remove if present. Optimistic local write, then remote. */
    suspend fun toggle(wallpaper: Wallpaper)

    /** Replace the local cache from the remote set. No-op when signed out or offline. */
    suspend fun reconcile()
}
```

- [ ] **Step 2: Write the fakes**

Create `app/src/test/java/com/dwell/app/data/favorites/Fakes.kt`:

```kotlin
package com.dwell.app.data.favorites

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.local.FavoriteDao
import com.dwell.app.data.local.FavoriteEntity
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.local.WallpaperEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeFavoriteDao : FavoriteDao {
    val state = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteEntity>> =
        state.asStateFlow().map { list -> list.sortedByDescending { it.addedAtMillis } }

    override suspend fun exists(id: String): Boolean =
        state.value.any { it.wallpaperId == id }

    override suspend fun upsert(favorite: FavoriteEntity) {
        state.value = state.value.filterNot { it.wallpaperId == favorite.wallpaperId } + favorite
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.wallpaperId == id }
    }

    override suspend fun clear() { state.value = emptyList() }

    override suspend fun insertAll(favorites: List<FavoriteEntity>) {
        val ids = favorites.map { it.wallpaperId }.toSet()
        state.value = state.value.filterNot { it.wallpaperId in ids } + favorites
    }

    override suspend fun replaceAll(favorites: List<FavoriteEntity>) {
        clear(); insertAll(favorites)
    }
}

class FakeWallpaperDao(rows: List<WallpaperEntity> = emptyList()) : WallpaperDao {
    val rows = rows.associateBy { it.id }.toMutableMap()

    override suspend fun upsertAll(wallpapers: List<WallpaperEntity>) {
        wallpapers.forEach { rows[it.id] = it }
    }
    override suspend fun getAll(): List<WallpaperEntity> = rows.values.toList()
    override suspend fun getByCategory(categoryId: String): List<WallpaperEntity> =
        rows.values.filter { it.category == categoryId }
    override suspend fun getById(id: String): WallpaperEntity? = rows[id]
    override suspend fun getByIds(ids: List<String>): List<WallpaperEntity> =
        ids.mapNotNull { rows[it] }
}

class FakeRemoteSource : FavoritesRemoteSource {
    val remote = mutableMapOf<String, MutableMap<String, Long>>() // uid -> id -> addedAt
    var failWrites = false

    override suspend fun fetchAll(uid: String): List<FavoriteRemote> =
        remote[uid].orEmpty().map { (id, ts) -> FavoriteRemote(id, ts) }

    override suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long) {
        if (failWrites) error("offline")
        remote.getOrPut(uid) { mutableMapOf() }[wallpaperId] = addedAtMillis
    }

    override suspend fun remove(uid: String, wallpaperId: String) {
        if (failWrites) error("offline")
        remote[uid]?.remove(wallpaperId)
    }
}

class FakeAuthRepository(private val current: String?) : AuthRepository {
    override val uid: Flow<String?> = MutableStateFlow(current)
    override fun currentUid(): String? = current
    override suspend fun ensureSignedIn() {}
}

fun wallpaperEntity(id: String, category: String = "nature") = WallpaperEntity(
    id = id, title = id, category = category, thumbUrl = "t", fullPhoneUrl = "p",
    fullTabletUrl = "tab", dominantColor = "#000000", isAiGenerated = true,
    order = 0, createdAtMillis = 0L,
)
```

- [ ] **Step 3: Write the failing repository tests**

Create `FavoritesRepositoryImplTest.kt`:

```kotlin
package com.dwell.app.data.favorites

import app.cash.turbine.test
import com.dwell.app.data.local.toModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRepositoryImplTest {

    private fun repo(
        dao: FakeFavoriteDao = FakeFavoriteDao(),
        wallpaperDao: FakeWallpaperDao = FakeWallpaperDao(),
        remote: FakeRemoteSource = FakeRemoteSource(),
        auth: FakeAuthRepository = FakeAuthRepository("u1"),
        now: Long = 1000L,
    ) = FavoritesRepositoryImpl(dao, wallpaperDao, remote, auth) { now }

    @Test
    fun toggle_addsThenRemoves() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource()
        val sut = repo(dao = dao, remote = remote)
        val w = wallpaperEntity("nature-1").toModel()

        sut.toggle(w)
        assertTrue(dao.exists("nature-1"))
        assertEquals(1, remote.fetchAll("u1").size)

        sut.toggle(w)
        assertFalse(dao.exists("nature-1"))
        assertEquals(0, remote.fetchAll("u1").size)
    }

    @Test
    fun toggle_keepsLocalStateWhenRemoteWriteFails() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource().apply { failWrites = true }
        val sut = repo(dao = dao, remote = remote)

        sut.toggle(wallpaperEntity("nature-1").toModel())

        assertTrue(dao.exists("nature-1")) // optimistic local write survives
        assertEquals(0, remote.fetchAll("u1").size) // remote never got it
    }

    @Test
    fun reconcile_replacesLocalWithRemote() = runTest {
        val dao = FakeFavoriteDao()
        val remote = FakeRemoteSource().apply {
            remote["u1"] = mutableMapOf("a" to 1L, "b" to 2L)
        }
        val sut = repo(dao = dao, remote = remote)
        // seed a stale local favorite that is not on the server
        dao.upsert(com.dwell.app.data.local.FavoriteEntity("stale", 5L))

        sut.reconcile()

        assertFalse(dao.exists("stale"))
        assertTrue(dao.exists("a"))
        assertTrue(dao.exists("b"))
    }

    @Test
    fun reconcile_isNoOpWhenSignedOut() = runTest {
        val dao = FakeFavoriteDao().apply { upsert(com.dwell.app.data.local.FavoriteEntity("x", 1L)) }
        val sut = repo(dao = dao, auth = FakeAuthRepository(null))

        sut.reconcile()

        assertTrue(dao.exists("x")) // untouched
    }

    @Test
    fun observeFavoriteIds_emitsSavedIds() = runTest {
        val dao = FakeFavoriteDao()
        val sut = repo(dao = dao)

        sut.observeFavoriteIds().test {
            assertEquals(emptySet<String>(), awaitItem())
            sut.toggle(wallpaperEntity("nature-1").toModel())
            assertEquals(setOf("nature-1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFavoriteWallpapers_skipsIdsMissingFromCache() = runTest {
        val dao = FakeFavoriteDao()
        val wallpaperDao = FakeWallpaperDao(listOf(wallpaperEntity("nature-1")))
        val sut = repo(dao = dao, wallpaperDao = wallpaperDao)

        // "ghost" is favorited but not in the wallpaper cache
        dao.upsert(com.dwell.app.data.local.FavoriteEntity("nature-1", 2L))
        dao.upsert(com.dwell.app.data.local.FavoriteEntity("ghost", 3L))

        sut.observeFavoriteWallpapers().test {
            val list = awaitItem()
            assertEquals(listOf("nature-1"), list.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.data.favorites.FavoritesRepositoryImplTest"`
Expected: FAIL — `FavoritesRepositoryImpl` does not exist (compilation error).

- [ ] **Step 5: Implement the repository**

Create `FavoritesRepositoryImpl.kt`:

```kotlin
package com.dwell.app.data.favorites

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.local.FavoriteDao
import com.dwell.app.data.local.FavoriteEntity
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.local.toModel
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.util.NowProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val wallpaperDao: WallpaperDao,
    private val remote: FavoritesRemoteSource,
    private val auth: AuthRepository,
    private val now: NowProvider,
) : FavoritesRepository {

    override fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeAll().map { list -> list.map { it.wallpaperId }.toSet() }

    override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> =
        favoriteDao.observeAll().map { favorites ->
            val rows = wallpaperDao.getByIds(favorites.map { it.wallpaperId })
                .associateBy { it.id }
            // Preserve favorite order (newest-saved first); drop uncached rows.
            favorites.mapNotNull { rows[it.wallpaperId]?.toModel() }
        }

    override suspend fun toggle(wallpaper: Wallpaper) {
        val id = wallpaper.id
        val uid = auth.currentUid()
        if (favoriteDao.exists(id)) {
            favoriteDao.deleteById(id)
            if (uid != null) runCatching { remote.remove(uid, id) }
        } else {
            val ts = now.nowMillis()
            favoriteDao.upsert(FavoriteEntity(id, ts))
            if (uid != null) runCatching { remote.put(uid, id, ts) }
        }
    }

    override suspend fun reconcile() {
        val uid = auth.currentUid() ?: return
        runCatching {
            val entities = remote.fetchAll(uid).map { FavoriteEntity(it.wallpaperId, it.addedAtMillis) }
            favoriteDao.replaceAll(entities)
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.data.favorites.FavoritesRepositoryImplTest"`
Expected: PASS — 6 tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/favorites/ app/src/test/java/com/dwell/app/data/favorites/
git commit -m "feat: add FavoritesRepository (Room-first, Firestore write-through) with tests"
```

---

## Task 6: Firestore remote source impl + DI binds

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/favorites/FavoritesRemoteSourceImpl.kt`
- Modify: `app/src/main/java/com/dwell/app/di/RepositoryModule.kt`

Firestore wiring is verified on-device (Task 11). No unit test.

- [ ] **Step 1: Implement the Firestore source**

Create `FavoritesRemoteSourceImpl.kt`:

```kotlin
package com.dwell.app.data.favorites

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRemoteSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : FavoritesRemoteSource {

    override suspend fun fetchAll(uid: String): List<FavoriteRemote> {
        val snapshot = collection(uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val id = doc.getString("wallpaperId") ?: doc.id
            val addedAt = doc.getTimestamp("addedAt")?.toDate()?.time ?: 0L
            FavoriteRemote(id, addedAt)
        }
    }

    override suspend fun put(uid: String, wallpaperId: String, addedAtMillis: Long) {
        collection(uid).document(wallpaperId).set(
            mapOf(
                "wallpaperId" to wallpaperId,
                "addedAt" to Timestamp(Date(addedAtMillis)),
            ),
        ).await()
    }

    override suspend fun remove(uid: String, wallpaperId: String) {
        collection(uid).document(wallpaperId).delete().await()
    }

    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection("favorites")
}
```

- [ ] **Step 2: Bind the remote source and the repository**

In `RepositoryModule.kt`, add imports:

```kotlin
import com.dwell.app.data.favorites.FavoritesRemoteSource
import com.dwell.app.data.favorites.FavoritesRemoteSourceImpl
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.favorites.FavoritesRepositoryImpl
```

Add binds inside the abstract class:

```kotlin
    @Binds
    @Singleton
    abstract fun bindFavoritesRemoteSource(impl: FavoritesRemoteSourceImpl): FavoritesRemoteSource

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository
```

- [ ] **Step 3: Build to verify the Hilt graph**

Run: `./gradlew.bat :app:kspDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt resolves the full favorites graph).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/favorites/FavoritesRemoteSourceImpl.kt app/src/main/java/com/dwell/app/di/RepositoryModule.kt
git commit -m "feat: add Firestore favorites source and DI binds"
```

---

## Task 7: Auth bootstrap + reconcile on launch

**Files:**
- Modify: `app/src/main/java/com/dwell/app/ui/DwellApp.kt`

- [ ] **Step 1: Add an app-start effect that signs in and reconciles**

In `DwellApp.kt`, add imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.dwell.app.ui.AppBootstrapViewModel
```

Then at the top of the `DwellApp()` composable body, before `NavHost`:

```kotlin
    val bootstrap: AppBootstrapViewModel = hiltViewModel()
    LaunchedEffect(Unit) { bootstrap.start() }
```

- [ ] **Step 2: Create the bootstrap view model**

Create `app/src/main/java/com/dwell/app/ui/AppBootstrapViewModel.kt`:

```kotlin
package com.dwell.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.favorites.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs once on launch: ensure an anonymous session exists, then reconcile the
 * favorites cache from the server. Both steps are safe offline (no-ops).
 */
@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            auth.ensureSignedIn()
            favorites.reconcile()
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/AppBootstrapViewModel.kt app/src/main/java/com/dwell/app/ui/DwellApp.kt
git commit -m "feat: sign in anonymously and reconcile favorites on launch"
```

---

## Task 8: Heart toggle in the preview

**Files:**
- Create: `app/src/main/res/drawable/ic_heart_filled.xml`
- Modify: `app/src/main/java/com/dwell/app/ui/preview/PreviewUiState.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/preview/PreviewViewModel.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/preview/PreviewScreen.kt`

- [ ] **Step 1: Add the filled heart drawable**

Create `ic_heart_filled.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
</vector>
```

- [ ] **Step 2: Add isFavorite to the preview state**

In `PreviewUiState.kt`, add the field to the data class:

```kotlin
data class PreviewUiState(
    val wallpaper: Wallpaper? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val applyState: ApplyState = ApplyState.Idle,
    val isFavorite: Boolean = false,
)
```

- [ ] **Step 3: Observe favorites and add the toggle in the view model**

In `PreviewViewModel.kt`, add the dependency and logic. Add imports:

```kotlin
import com.dwell.app.data.favorites.FavoritesRepository
import kotlinx.coroutines.flow.collectLatest
```

Add `FavoritesRepository` to the constructor:

```kotlin
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WallpaperRepository,
    private val applier: WallpaperApplier,
    private val favorites: FavoritesRepository,
) : ViewModel() {
```

In the existing `init { ... }` block, after the wallpaper load `viewModelScope.launch { ... }`, add a second collector:

```kotlin
        viewModelScope.launch {
            favorites.observeFavoriteIds().collectLatest { ids ->
                _uiState.update { it.copy(isFavorite = ids.contains(wallpaperId)) }
            }
        }
```

Add the toggle function (next to `apply`):

```kotlin
    fun onToggleFavorite() {
        val wallpaper = _uiState.value.wallpaper ?: return
        viewModelScope.launch { favorites.toggle(wallpaper) }
    }
```

- [ ] **Step 4: Wire the heart control in the screen**

In `PreviewScreen.kt`, change `ApplyPanel` to take favorite state and a callback. Update the call site inside `PreviewScreen`:

```kotlin
                ApplyPanel(
                    wallpaper = wallpaper,
                    target = state.target,
                    isApplying = state.applyState == ApplyState.Applying,
                    isFavorite = state.isFavorite,
                    onSelectTarget = viewModel::selectTarget,
                    onApply = { viewModel.apply(isTablet) },
                    onToggleFavorite = viewModel::onToggleFavorite,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
```

Update `ApplyPanel`'s signature and pass-through:

```kotlin
@Composable
private fun ApplyPanel(
    wallpaper: Wallpaper,
    target: WallpaperTarget,
    isApplying: Boolean,
    isFavorite: Boolean,
    onSelectTarget: (WallpaperTarget) -> Unit,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Inside `ApplyPanel`, change `TitleRow(wallpaper = wallpaper)` to:

```kotlin
            TitleRow(
                wallpaper = wallpaper,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
```

Replace `TitleRow` with a version that drives the heart from state:

```kotlin
@Composable
private fun TitleRow(
    wallpaper: Wallpaper,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wallpaper.title ?: stringResource(R.string.preview_untitled),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metaLine(wallpaper),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            Icon(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
                ),
                contentDescription = stringResource(R.string.cd_favorite),
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/preview/ app/src/main/res/drawable/ic_heart_filled.xml
git commit -m "feat: wire favorite toggle into the wallpaper preview"
```

---

## Task 9: Saved chip + favorites mode in the grid (TDD for the view-model logic)

**Files:**
- Modify: `app/src/main/java/com/dwell/app/data/model/Category.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/wallpapers/WallpapersUiState.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/wallpapers/WallpapersViewModel.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/wallpapers/components/CategoryChipRow.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/wallpapers/WallpapersScreen.kt`
- Test: `app/src/test/java/com/dwell/app/ui/wallpapers/WallpapersViewModelFavoritesTest.kt`

- [ ] **Step 1: Add a synthetic Favorites chip id**

In `Category.kt`, inside the companion object, after `ALL_ID`:

```kotlin
        /** Synthetic "Saved" chip. Selecting it shows the user's favorites
         *  instead of a catalog query. Not a Firestore doc. */
        const val FAVORITES_ID = "__favorites__"

        fun favorites(name: String = "Saved"): Category =
            Category(id = FAVORITES_ID, name = name, order = -2)
```

- [ ] **Step 2: Add favorites fields to the UI state**

In `WallpapersUiState.kt`, add two fields to the data class:

```kotlin
data class WallpapersUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String = Category.ALL_ID,
    val wallpapers: List<Wallpaper> = emptyList(),
    val contentState: ContentState = ContentState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val isOffline: Boolean = false,
    val isFavoritesMode: Boolean = false,
    val favorites: List<Wallpaper> = emptyList(),
)
```

- [ ] **Step 3: Write the failing view-model test**

Create `WallpapersViewModelFavoritesTest.kt`:

```kotlin
package com.dwell.app.ui.wallpapers

import app.cash.turbine.test
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Category
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.data.repository.PageCursor
import com.dwell.app.data.repository.WallpaperPage
import com.dwell.app.data.repository.WallpaperRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersViewModelFavoritesTest {

    // Unconfined so the view model's init collectors run eagerly and the state
    // is settled by the time the test reads it.
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun wallpaper(id: String) = Wallpaper(
        id = id, title = id, category = "nature", thumbUrl = "t", fullPhoneUrl = "p",
        fullTabletUrl = "tab", dominantColor = "#000000", isAiGenerated = true,
        order = 0, createdAtMillis = 0L,
    )

    private val emptyPage = WallpaperPage(emptyList(), cursor = null, endReached = true, fromCache = false)

    private class FakeWallpaperRepo(private val page: WallpaperPage) : WallpaperRepository {
        override suspend fun getCategories() = Result.success(emptyList<Category>())
        override suspend fun getWallpapers(categoryId: String?, cursor: PageCursor?, pageSize: Int) =
            Result.success(page)
        override suspend fun getWallpaper(id: String): Wallpaper? = null
    }

    private class FakeFavoritesRepo(val favs: List<Wallpaper>) : FavoritesRepository {
        val ids = MutableStateFlow(favs.map { it.id }.toSet())
        override fun observeFavoriteIds(): Flow<Set<String>> = ids
        override fun observeFavoriteWallpapers(): Flow<List<Wallpaper>> = MutableStateFlow(favs)
        override suspend fun toggle(wallpaper: Wallpaper) {}
        override suspend fun reconcile() {}
    }

    @Test
    fun selectingSavedChip_entersFavoritesMode() = runTest {
        val vm = WallpapersViewModel(
            FakeWallpaperRepo(emptyPage),
            FakeFavoritesRepo(listOf(wallpaper("nature-1"))),
        )

        vm.selectCategory(Category.FAVORITES_ID)

        vm.uiState.test {
            // Unconfined dispatcher: state already settled. Latest item holds.
            val state = awaitItem()
            assertTrue(state.isFavoritesMode)
            assertEquals(listOf("nature-1"), state.favorites.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingRealCategory_leavesFavoritesMode() = runTest {
        val vm = WallpapersViewModel(
            FakeWallpaperRepo(emptyPage),
            FakeFavoritesRepo(emptyList()),
        )
        vm.selectCategory(Category.FAVORITES_ID)
        vm.selectCategory(Category.ALL_ID)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.isFavoritesMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.ui.wallpapers.WallpapersViewModelFavoritesTest"`
Expected: FAIL — `WallpapersViewModel` constructor does not take a `FavoritesRepository`.

- [ ] **Step 5: Implement favorites mode in the view model**

In `WallpapersViewModel.kt`:

Add imports:

```kotlin
import com.dwell.app.data.favorites.FavoritesRepository
import kotlinx.coroutines.flow.collectLatest
```

Add the dependency to the constructor:

```kotlin
class WallpapersViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
```

In `init { ... }`, after the existing calls, add a favorites collector:

```kotlin
        viewModelScope.launch {
            favoritesRepository.observeFavoriteWallpapers().collectLatest { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
```

Replace `selectCategory` to branch on the Saved chip:

```kotlin
    fun selectCategory(categoryId: String) {
        if (categoryId == _uiState.value.selectedCategoryId) return
        val favoritesMode = categoryId == Category.FAVORITES_ID
        _uiState.update {
            it.copy(selectedCategoryId = categoryId, isFavoritesMode = favoritesMode)
        }
        if (!favoritesMode) {
            loadFirstPage(showLoading = true)
        }
    }
```

Add `import com.dwell.app.data.model.Category` if not already present.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.dwell.app.ui.wallpapers.WallpapersViewModelFavoritesTest"`
Expected: PASS — 2 tests.

- [ ] **Step 7: Add the Saved chip to the chip row**

The `WallpapersViewModel.loadCategories` builds the chip list as `listOf(Category.all()) + categories`. Change it to also prepend the Saved chip:

In `WallpapersViewModel.kt`, in `loadCategories`, replace the update body:

```kotlin
                _uiState.update {
                    it.copy(
                        categories = listOf(Category.all(), Category.favorites()) + categories,
                    )
                }
```

The existing `CategoryChipRow` renders every chip by id/name, so the Saved chip shows automatically. No chip-row code change is required for it to appear.

- [ ] **Step 8: Render favorites in the grid when in favorites mode**

In `WallpapersScreen.kt`, in `WallpapersContent`, replace the `Box { when (state.contentState) ... }` block's content selection so favorites mode short-circuits to the favorites grid. Change the `Box(...)` body to:

```kotlin
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (state.isFavoritesMode) {
                if (state.favorites.isEmpty()) {
                    FavoritesEmptyState()
                } else {
                    FavoritesGrid(favorites = state.favorites, onWallpaperClick = onWallpaperClick)
                }
            } else {
                when (state.contentState) {
                    ContentState.Loading -> LoadingState()
                    ContentState.Error -> ErrorState(onRetry = onRetry)
                    ContentState.Empty -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                    ) {
                        EmptyState()
                    }
                    ContentState.Content -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                    ) {
                        WallpaperGrid(
                            state = state,
                            onWallpaperClick = onWallpaperClick,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
```

Add a favorites grid and empty state at the bottom of the file. `FavoritesGrid` reuses the same staggered layout without paging:

```kotlin
@Composable
private fun FavoritesGrid(
    favorites: List<Wallpaper>,
    onWallpaperClick: (Wallpaper) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = favorites, key = { it.id }) { wallpaper ->
            WallpaperCard(wallpaper = wallpaper, onClick = { onWallpaperClick(wallpaper) })
        }
    }
}

@Composable
private fun FavoritesEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.favorites_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
```

- [ ] **Step 9: Add the empty-state string**

In `res/values/strings.xml`, add inside `<resources>`:

```xml
    <string name="favorites_empty">No favorites yet. Tap the heart on any wallpaper.</string>
```

- [ ] **Step 10: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/model/Category.kt app/src/main/java/com/dwell/app/ui/wallpapers/ app/src/test/java/com/dwell/app/ui/wallpapers/ app/src/main/res/values/strings.xml
git commit -m "feat: add Saved chip and favorites mode to the wallpaper grid"
```

---

## Task 10: Favorites screen + More entry + route

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/favorites/FavoritesScreen.kt`
- Create: `app/src/main/java/com/dwell/app/ui/favorites/FavoritesViewModel.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt`
- Modify: `app/src/main/java/com/dwell/app/ui/DwellApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Create the favorites view model**

Create `FavoritesViewModel.kt`:

```kotlin
package com.dwell.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.model.Wallpaper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    favorites: FavoritesRepository,
) : ViewModel() {

    val favorites: StateFlow<List<Wallpaper>> =
        favorites.observeFavoriteWallpapers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 2: Create the favorites screen**

Create `FavoritesScreen.kt`:

```kotlin
package com.dwell.app.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.R
import com.dwell.app.data.model.Wallpaper
import com.dwell.app.ui.wallpapers.components.WallpaperCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onWallpaperClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (favorites.isEmpty()) {
                Text(
                    text = stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = favorites, key = { it.id }) { wallpaper: Wallpaper ->
                        WallpaperCard(wallpaper = wallpaper, onClick = { onWallpaperClick(wallpaper.id) })
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Make the More screen a real list with a Favorites row**

Replace the body of `MoreScreen.kt`:

```kotlin
package com.dwell.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dwell.app.R

@Composable
fun MoreScreen(
    onOpenFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenFavorites)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}
```

- [ ] **Step 4: Add the favorites route and wire More**

In `DwellApp.kt`, add a route constant near the others:

```kotlin
private const val ROUTE_FAVORITES = "favorites"
```

Add a top-level composable for it inside the outer `NavHost` (sibling of the preview route):

```kotlin
        composable(ROUTE_FAVORITES) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onWallpaperClick = { id -> navController.navigate("$ROUTE_PREVIEW/$id") },
            )
        }
```

Change the `MainShell(...)` call in the `ROUTE_MAIN` composable to pass the favorites navigation down:

```kotlin
        composable(ROUTE_MAIN) {
            MainShell(
                onWallpaperClick = { id -> navController.navigate("$ROUTE_PREVIEW/$id") },
                onOpenFavorites = { navController.navigate(ROUTE_FAVORITES) },
            )
        }
```

Update `MainShell`'s signature and the `MoreScreen` call inside its `NavHost`:

```kotlin
@Composable
private fun MainShell(
    onWallpaperClick: (String) -> Unit,
    onOpenFavorites: () -> Unit,
) {
```

```kotlin
            composable(DwellDestination.MORE.route) { MoreScreen(onOpenFavorites = onOpenFavorites) }
```

Add imports at the top of `DwellApp.kt`:

```kotlin
import com.dwell.app.ui.favorites.FavoritesScreen
```

- [ ] **Step 5: Add the title string**

In `res/values/strings.xml`, add:

```xml
    <string name="favorites_title">Favorites</string>
```

- [ ] **Step 6: Build**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/favorites/ app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt app/src/main/java/com/dwell/app/ui/DwellApp.kt app/src/main/res/values/strings.xml
git commit -m "feat: add Favorites screen reachable from the More tab"
```

---

## Task 11: Console gate, rules deploy, and on-device end-to-end

**Files:** none changed (`firestore.rules` already contains the `users`/`favorites` block — verify, do not duplicate).

- [ ] **Step 1: Verify the rules already cover favorites**

Open `firestore.rules` and confirm this block is present inside `match /databases/{database}/documents`:

```
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
      match /favorites/{wallpaperId} {
        allow read, write: if request.auth != null && request.auth.uid == uid;
      }
    }
```

It is already there from Phase 1 scaffolding. No edit needed. If it is somehow missing, add it before deploying.

- [ ] **Step 2: Enable the Anonymous provider (manual, console)**

In the Firebase console: Authentication > Sign-in method > **Anonymous** > Enable > Save. This is a prerequisite; the app cannot get a uid without it.

- [ ] **Step 3: Deploy the rules**

Run: `firebase deploy --only firestore:rules`
Expected: "Deploy complete!" with the rules released.

- [ ] **Step 4: Full unit-test pass**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (smoke + repository + view-model).

- [ ] **Step 5: Install and verify on device**

Run: `./gradlew.bat :app:installDebug`

Then verify by hand on the device:
1. Open a wallpaper preview. Tap the heart. It fills with the green accent.
2. Check logcat shows no `PERMISSION_DENIED`:
   `adb logcat -d | findstr /C:"PERMISSION_DENIED"` (expected: no favorites lines).
3. On the Wallpapers tab, tap the **Saved** chip. The favorited wallpaper shows.
4. Open the **More** tab, tap **Favorites**. The same wallpaper shows. Tap it, the preview opens.
5. Kill the app, reopen. The heart is still filled (Room persisted).
6. Turn on airplane mode. Favorite a different wallpaper from its preview. It appears in the Saved chip and the More > Favorites list. (Write-through is deferred; local state holds.)
7. Unfavorite from the preview. It disappears from both surfaces.

- [ ] **Step 6: Nothing to commit**

No source changed in this task (rules were already in place). The slice is
complete at the end of Task 10's commit plus the verified device run here. If
the device run surfaces a bug, fix it as its own task with its own commit.

---

## Done

At the end of Task 11 the slice is complete: anonymous auth, offline-first favorites, a working heart in the preview, and both read surfaces, all verified on device. Deferred to the next slice: the sign-in sheet, anonymous->real upgrade, cross-device realtime sync, settings sync, and account deletion.
