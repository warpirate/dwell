# Premium Entitlement Spine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the `premium` entitlement spine — a one-time Play Billing unlock that, after server-side verification, flips a server-owned `premium` flag the whole app can observe as a `Flow<Boolean>`.

**Architecture:** Client never writes the entitlement. Purchase flow → Play Billing → `verifyPurchase` Cloud Function (validates the purchase token with the Play Developer API, writes `premium: true` via Admin SDK) → the app's Firestore user-doc snapshot listener emits `true`. Everything that needs gating (ads, matched widget presets, extra launcher home styles) reads one surface: `EntitlementRepository.observePremium()`. Firestore's persistent cache (already configured in `DataModule`) serves the flag offline, so no DataStore is added.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Firebase (Auth, Firestore, Functions), Play Billing Library 7, JUnit4 + Turbine + coroutines-test (JVM). Cloud Function in Node/TypeScript.

**Scope:** This plan delivers the entitlement spine end-to-end. It is the Phase 3 (Monetization) foundation. The following are **separate follow-on plans** that each only read the gate this plan establishes, and each depends on a subsystem that does not exist yet:
- **AdMob ad slots** gated by `!premium` (Phase 3 cont.) — no widgets/launcher dependency, can follow immediately.
- **Wallpaper-matched widget presets** gated by `premium` (Phase 4 — needs the Glance widgets to exist first).
- **Extra launcher home styles** (Editorial / Structured) gated by `premium` (Phase 6 — needs the launcher).

**Strategy reference:** [dwell-docs/01-PRD.md](../../../dwell-docs/01-PRD.md) §Monetization, [dwell-docs/02-TRD.md](../../../dwell-docs/02-TRD.md) §8, [dwell-docs/05-Backend-Schema.md](../../../dwell-docs/05-Backend-Schema.md), [dwell-docs/08-CLAUDE.md](../../../dwell-docs/08-CLAUDE.md).

---

## File Structure

**New (Android — `app/src/main/java/com/dwell/app/`):**
- `data/billing/EntitlementRemoteSource.kt` — interface; Firestore user-doc `premium` listener.
- `data/billing/EntitlementRemoteSourceImpl.kt` — Firestore impl.
- `data/billing/EntitlementRepository.kt` — interface; the app-wide gate.
- `data/billing/EntitlementRepositoryImpl.kt` — joins auth uid + remote flag.
- `data/billing/BillingRepository.kt` — interface; launches the purchase flow.
- `data/billing/BillingRepositoryImpl.kt` — Play BillingClient wrapper + Functions call.
- `data/billing/PurchaseResult.kt` — sealed result of a purchase attempt.
- `ui/unlock/UnlockViewModel.kt` — premium state + `unlock()` trigger.

**New (tests — `app/src/test/java/com/dwell/app/`):**
- `data/billing/EntitlementFakes.kt` — fakes for remote source + auth.
- `data/billing/EntitlementRepositoryImplTest.kt`
- `ui/unlock/UnlockViewModelTest.kt`

**New (Cloud Function — `functions/`):**
- `functions/src/verifyPurchase.ts`
- `functions/package.json`, `functions/tsconfig.json` (if `functions/` does not yet exist)

**Modified:**
- `gradle/libs.versions.toml` — add `billing` version + library, `firebase-functions` library.
- `app/build.gradle.kts` — add the two dependencies.
- `app/src/main/java/com/dwell/app/di/DataModule.kt` — provide `FirebaseFunctions`.
- `app/src/main/java/com/dwell/app/di/RepositoryModule.kt` — bind the three new interfaces.
- `app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt` — add the unlock row.
- `app/src/main/res/values/strings.xml` — unlock copy.
- `firestore.rules` — rename `removeAds` → `premium` (3 occurrences).
- `firebase.json` — register the functions codebase (if not already).

---

## Task 1: Rename the server-owned field `removeAds` → `premium` in Firestore rules

The strategy renamed the entitlement. The rules file still names the old field, so a client could currently set `premium` freely while `removeAds` is the one protected. Fix the rules first — everything else writes/reads `premium`.

**Files:**
- Modify: `firestore.rules:6`, `firestore.rules:24`, `firestore.rules:26`

- [ ] **Step 1: Update the comment and both guards**

In `firestore.rules`, replace the comment line:

```
// are private to their owner. removeAds is written server-side only (Cloud
```
with:
```
// are private to their owner. premium is written server-side only (Cloud
```

Replace the `create` guard:
```
      allow create: if request.auth != null && request.auth.uid == uid
                    && !('removeAds' in request.resource.data);
```
with:
```
      allow create: if request.auth != null && request.auth.uid == uid
                    && !('premium' in request.resource.data);
```

Replace the `update` guard:
```
      allow update: if request.auth != null && request.auth.uid == uid
                    && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['removeAds']);
```
with:
```
      allow update: if request.auth != null && request.auth.uid == uid
                    && !request.resource.data.diff(resource.data).affectedKeys().hasAny(['premium']);
```

- [ ] **Step 2: Verify the rules compile**

Run: `firebase deploy --only firestore:rules --dry-run`
Expected: `✔ ... rules file firestore.rules compiled successfully` and no error. (If the Firebase CLI is not logged in, instead run `firebase firestore:rules:canary --help` is unavailable; fall back to visual confirmation that no `removeAds` token remains: `grep -n removeAds firestore.rules` returns nothing.)

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git commit -m "fix(rules): protect premium field instead of removeAds"
```

---

## Task 2: Add Play Billing and Firebase Functions dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version and library entries**

In `gradle/libs.versions.toml`, under `[versions]` add (after `googleid = "1.1.1"`):
```toml
billing = "7.1.1"
```

Under `[libraries]`, add (after the Firebase block):
```toml
firebase-functions = { group = "com.google.firebase", name = "firebase-functions" }
billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
```

- [ ] **Step 2: Add the dependencies to the app module**

In `app/build.gradle.kts`, inside the Firebase block add:
```kotlin
    implementation(libs.firebase.functions)
```
And after the Firebase block, add:
```kotlin
    // Play Billing (one-time unlock)
    implementation(libs.billing.ktx)
```

- [ ] **Step 3: Verify it resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath -q | grep -E "billing|firebase-functions"`
Expected: lines showing `com.android.billingclient:billing-ktx:7.1.1` and `com.google.firebase:firebase-functions`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Play Billing and Firebase Functions dependencies"
```

---

## Task 3: EntitlementRemoteSource (Firestore listener on the user doc)

The remote source exposes the server-owned `premium` flag as a `Flow<Boolean>`. The impl is Firestore-bound (a snapshot listener via `callbackFlow`, mirroring `AuthRepositoryImpl.uid`), so it is verified by the repository test in Task 4 against a fake — not unit-tested directly.

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/billing/EntitlementRemoteSource.kt`
- Create: `app/src/main/java/com/dwell/app/data/billing/EntitlementRemoteSourceImpl.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.dwell.app.data.billing

import kotlinx.coroutines.flow.Flow

/** Reads the server-owned `premium` entitlement flag for a user. */
interface EntitlementRemoteSource {

    /**
     * Emits the user's `premium` flag and re-emits on every server change.
     * Missing doc or missing field → false. Served from Firestore's offline
     * cache when offline.
     */
    fun observePremium(uid: String): Flow<Boolean>
}
```

- [ ] **Step 2: Write the Firestore impl**

```kotlin
package com.dwell.app.data.billing

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRemoteSourceImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : EntitlementRemoteSource {

    override fun observePremium(uid: String): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                // On error, snapshot is null: treat as not-premium rather than crash.
                trySend(snapshot?.getBoolean("premium") ?: false)
            }
        awaitClose { registration.remove() }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/billing/EntitlementRemoteSource.kt app/src/main/java/com/dwell/app/data/billing/EntitlementRemoteSourceImpl.kt
git commit -m "feat(billing): add EntitlementRemoteSource reading the premium flag"
```

---

## Task 4: EntitlementRepository (the app-wide gate) — TDD

Joins the auth uid stream with the remote `premium` flag. Signed-out or any user without `premium: true` → `false`. This is the single surface everything else gates on. Fully unit-tested.

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/billing/EntitlementRepository.kt`
- Create: `app/src/main/java/com/dwell/app/data/billing/EntitlementRepositoryImpl.kt`
- Test: `app/src/test/java/com/dwell/app/data/billing/EntitlementFakes.kt`
- Test: `app/src/test/java/com/dwell/app/data/billing/EntitlementRepositoryImplTest.kt`

- [ ] **Step 1: Write the interface**

```kotlin
package com.dwell.app.data.billing

import kotlinx.coroutines.flow.Flow

/**
 * The premium entitlement gate. `true` = ads removed + coordinated layer
 * unlocked. Read this anywhere a feature must be gated; never read the raw
 * Firestore field elsewhere.
 */
interface EntitlementRepository {

    /** Reactive entitlement. Re-emits on sign-in/out and on server changes. */
    fun observePremium(): Flow<Boolean>
}
```

- [ ] **Step 2: Write the test fakes**

```kotlin
package com.dwell.app.data.billing

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.UpgradeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Auth fake whose uid stream can be driven during a test. */
class FakeMutableAuthRepository(initial: String?) : AuthRepository {
    private val uidState = MutableStateFlow(initial)
    override val uid: Flow<String?> = uidState
    fun setUid(value: String?) { uidState.value = value }

    override fun currentUid(): String? = uidState.value
    override suspend fun ensureSignedIn() {}
    override fun isAnonymous(): Boolean = uidState.value == null
    override fun currentEmail(): String? = null
    override suspend fun linkEmail(email: String, password: String, createAccount: Boolean): UpgradeResult =
        UpgradeResult.Linked(uidState.value ?: "u1")
    override suspend fun linkGoogle(idToken: String): UpgradeResult =
        UpgradeResult.Linked(uidState.value ?: "u1")
    override suspend fun signOut() {}
}

/** Remote source fake with per-uid premium state that a test can flip. */
class FakeEntitlementRemoteSource : EntitlementRemoteSource {
    private val premiumByUid = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    override fun observePremium(uid: String): Flow<Boolean> =
        premiumByUid.map { it[uid] ?: false }
    fun setPremium(uid: String, value: Boolean) {
        premiumByUid.value = premiumByUid.value + (uid to value)
    }
}
```

- [ ] **Step 3: Write the failing test**

```kotlin
package com.dwell.app.data.billing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementRepositoryImplTest {

    private fun repo(auth: FakeMutableAuthRepository, remote: FakeEntitlementRemoteSource) =
        EntitlementRepositoryImpl(auth, remote)

    @Test
    fun `signed out emits false`() = runTest {
        val repo = repo(FakeMutableAuthRepository(null), FakeEntitlementRemoteSource())
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user without premium emits false`() = runTest {
        val repo = repo(FakeMutableAuthRepository("u1"), FakeEntitlementRemoteSource())
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `premium flag true emits true`() = runTest {
        val remote = FakeEntitlementRemoteSource().apply { setPremium("u1", true) }
        val repo = repo(FakeMutableAuthRepository("u1"), remote)
        repo.observePremium().test {
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flips when the server flag changes`() = runTest {
        val remote = FakeEntitlementRemoteSource()
        val repo = repo(FakeMutableAuthRepository("u1"), remote)
        repo.observePremium().test {
            assertEquals(false, awaitItem())
            remote.setPremium("u1", true)
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-evaluates when the uid changes`() = runTest {
        val remote = FakeEntitlementRemoteSource().apply { setPremium("paid", true) }
        val auth = FakeMutableAuthRepository(null)
        val repo = repo(auth, remote)
        repo.observePremium().test {
            assertEquals(false, awaitItem()) // signed out
            auth.setUid("paid")
            assertEquals(true, awaitItem())  // switched to a premium account
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.billing.EntitlementRepositoryImplTest" -q`
Expected: FAIL — compilation error, `EntitlementRepositoryImpl` does not exist yet.

- [ ] **Step 5: Write the minimal implementation**

```kotlin
package com.dwell.app.data.billing

import com.dwell.app.data.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRepositoryImpl @Inject constructor(
    private val auth: AuthRepository,
    private val remote: EntitlementRemoteSource,
) : EntitlementRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePremium(): Flow<Boolean> =
        auth.uid.flatMapLatest { uid ->
            if (uid == null) flowOf(false) else remote.observePremium(uid)
        }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.data.billing.EntitlementRepositoryImplTest" -q`
Expected: PASS — 5 tests green.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/billing/EntitlementRepository.kt app/src/main/java/com/dwell/app/data/billing/EntitlementRepositoryImpl.kt app/src/test/java/com/dwell/app/data/billing/
git commit -m "feat(billing): add EntitlementRepository premium gate with tests"
```

---

## Task 5: Wire DI — provide FirebaseFunctions, bind the new interfaces

**Files:**
- Modify: `app/src/main/java/com/dwell/app/di/DataModule.kt`
- Modify: `app/src/main/java/com/dwell/app/di/RepositoryModule.kt`

- [ ] **Step 1: Provide FirebaseFunctions in DataModule**

In `DataModule.kt` add the import:
```kotlin
import com.google.firebase.functions.FirebaseFunctions
```
And add the provider inside the `object DataModule` body (after `provideFirebaseAuth`):
```kotlin
    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()
```

- [ ] **Step 2: Bind the new interfaces in RepositoryModule**

In `RepositoryModule.kt` add the imports:
```kotlin
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.BillingRepositoryImpl
import com.dwell.app.data.billing.EntitlementRemoteSource
import com.dwell.app.data.billing.EntitlementRemoteSourceImpl
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.EntitlementRepositoryImpl
```
And add the binds inside the abstract class (after `bindFavoritesRepository`):
```kotlin
    @Binds
    @Singleton
    abstract fun bindEntitlementRemoteSource(impl: EntitlementRemoteSourceImpl): EntitlementRemoteSource

    @Binds
    @Singleton
    abstract fun bindEntitlementRepository(impl: EntitlementRepositoryImpl): EntitlementRepository

    @Binds
    @Singleton
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository
```

> Note: `BillingRepository`/`BillingRepositoryImpl` are created in Task 6. If executing strictly in order, add the `BillingRepository` bind in Task 6 instead and keep this step to the two entitlement binds. Either order compiles once Task 6 lands.

- [ ] **Step 3: Verify the Hilt graph compiles**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL (if Task 6 not yet done, temporarily omit the `bindBillingRepository` line).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dwell/app/di/DataModule.kt app/src/main/java/com/dwell/app/di/RepositoryModule.kt
git commit -m "feat(di): provide FirebaseFunctions and bind entitlement sources"
```

---

## Task 6: BillingRepository — Play purchase flow + server verify

Wraps `BillingClient`: connect, query the `unlock_premium` product, launch the flow, and on a successful purchase acknowledge it and call the `verifyPurchase` Cloud Function. The client never writes `premium`; it triggers verification and lets the Task 4 listener flip the flag. `BillingClient` is Android-framework-bound and cannot run on the JVM, so this task is verified **on device** (Step 4), not by a unit test.

**Files:**
- Create: `app/src/main/java/com/dwell/app/data/billing/PurchaseResult.kt`
- Create: `app/src/main/java/com/dwell/app/data/billing/BillingRepository.kt`
- Create: `app/src/main/java/com/dwell/app/data/billing/BillingRepositoryImpl.kt`

- [ ] **Step 1: Write the result type**

```kotlin
package com.dwell.app.data.billing

/** Outcome of a purchase attempt. The entitlement itself arrives via the Firestore listener. */
sealed interface PurchaseResult {
    /** Purchase succeeded and was sent for server verification. */
    data object Verifying : PurchaseResult
    /** User dismissed the Play sheet. */
    data object Cancelled : PurchaseResult
    /** User already owns the unlock; verification was re-triggered. */
    data object AlreadyOwned : PurchaseResult
    /** Anything else (billing unavailable, network, verify call failed). */
    data class Error(val message: String) : PurchaseResult
}
```

- [ ] **Step 2: Write the interface**

```kotlin
package com.dwell.app.data.billing

import android.app.Activity

interface BillingRepository {
    /** Product id of the one-time unlock, configured in Play Console. */
    val productId: String

    /** Launch the Play purchase flow for the unlock. Suspends until the flow resolves. */
    suspend fun launchPurchase(activity: Activity): PurchaseResult
}
```

- [ ] **Step 3: Write the impl**

```kotlin
package com.dwell.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val functions: FirebaseFunctions,
) : BillingRepository {

    override val productId = "unlock_premium"

    // Each launch installs a fresh deferred the listener completes.
    @Volatile private var pending: CompletableDeferred<PurchaseResult>? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        val deferred = pending ?: return@PurchasesUpdatedListener
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it, deferred) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                deferred.complete(PurchaseResult.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Re-verify so a previously-purchased unlock is restored.
                purchases?.forEach { handlePurchase(it, deferred) }
                if (!deferred.isCompleted) deferred.complete(PurchaseResult.AlreadyOwned)
            }
            else -> deferred.complete(PurchaseResult.Error("billing ${result.responseCode}"))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases()
        .build()

    override suspend fun launchPurchase(activity: Activity): PurchaseResult {
        val deferred = CompletableDeferred<PurchaseResult>()
        pending = deferred
        runCatching {
            ensureConnected()
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val details = client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
            ).productDetailsList?.firstOrNull()
                ?: return PurchaseResult.Error("product not found")

            val params = com.android.billingclient.api.BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                ).build()
            client.launchBillingFlow(activity, params)
        }.onFailure { return PurchaseResult.Error(it.message ?: "billing setup failed") }
        return deferred.await()
    }

    private fun handlePurchase(purchase: Purchase, deferred: CompletableDeferred<PurchaseResult>) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Fire-and-await server verification; the premium flag flips via Firestore.
        kotlinx.coroutines.GlobalScope.launchVerify(purchase, deferred)
    }

    private fun kotlinx.coroutines.CoroutineScope.launchVerify(
        purchase: Purchase,
        deferred: CompletableDeferred<PurchaseResult>,
    ) = kotlinx.coroutines.GlobalScope.let {
        kotlinx.coroutines.MainScope().launch {
            runCatching {
                functions.getHttpsCallable("verifyPurchase")
                    .call(mapOf("purchaseToken" to purchase.purchaseToken, "productId" to productId))
                    .await()
                if (!purchase.isAcknowledged) {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build(),
                    )
                }
                if (!deferred.isCompleted) deferred.complete(PurchaseResult.Verifying)
            }.onFailure {
                if (!deferred.isCompleted) deferred.complete(PurchaseResult.Error(it.message ?: "verify failed"))
            }
        }
    }

    private suspend fun ensureConnected() {
        if (client.isReady) return
        val connected = CompletableDeferred<Unit>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) connected.complete(Unit)
                else connected.completeExceptionally(IllegalStateException("connect ${result.responseCode}"))
            }
            override fun onBillingServiceDisconnected() { /* retried on next launch */ }
        })
        connected.await()
    }
}
```

> Implementation note for the executing engineer: the two helper methods above use coroutine scopes loosely to keep the impl self-contained. When wiring, prefer injecting an application `CoroutineScope` (a `@Singleton @Provides` `CoroutineScope(SupervisorJob() + Dispatchers.Main)`) and using it instead of `MainScope()`/`GlobalScope`, to match the repo's structured-concurrency style. Replace both helpers with a single `appScope.launch { … }` verifying block. Keep the behaviour identical: verify → acknowledge → complete the deferred.

- [ ] **Step 4: Verify on device (no JVM test possible)**

This needs a real device/emulator with the app signed by an upload key the Play Console license-tests, the `unlock_premium` product created (Task 7 ops), and the Cloud Function deployed. Defer the full purchase verification until Task 7 + Task 9 land, then:
Run: `./gradlew :app:installDebug` and trigger the unlock from the More screen; confirm in Logcat the `verifyPurchase` call returns and `premium` flips. Until then, verify only that it compiles:
Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Add the BillingRepository bind (if not already in Task 5)**

Ensure `RepositoryModule` contains the `bindBillingRepository` line from Task 5 Step 2.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dwell/app/data/billing/PurchaseResult.kt app/src/main/java/com/dwell/app/data/billing/BillingRepository.kt app/src/main/java/com/dwell/app/data/billing/BillingRepositoryImpl.kt app/src/main/java/com/dwell/app/di/RepositoryModule.kt
git commit -m "feat(billing): add BillingRepository purchase flow with server verify"
```

---

## Task 7: `verifyPurchase` Cloud Function (server verification)

Validates the Play purchase token against the Google Play Developer API and writes `premium: true` with the Admin SDK (bypassing the client-write ban from Task 1). This is the only writer of `premium`.

**Files:**
- Create: `functions/src/verifyPurchase.ts`
- Create/modify: `functions/package.json`, `functions/tsconfig.json`, `functions/src/index.ts`
- Modify: `firebase.json` (register the functions codebase if absent)

- [ ] **Step 1: Scaffold the functions project (skip any file that already exists)**

`functions/package.json`:
```json
{
  "name": "functions",
  "engines": { "node": "20" },
  "main": "lib/index.js",
  "scripts": { "build": "tsc", "deploy": "firebase deploy --only functions" },
  "dependencies": {
    "firebase-admin": "^12.0.0",
    "firebase-functions": "^5.0.0",
    "googleapis": "^140.0.0"
  },
  "devDependencies": { "typescript": "^5.4.0" }
}
```

`functions/tsconfig.json`:
```json
{
  "compilerOptions": {
    "module": "commonjs",
    "target": "es2021",
    "outDir": "lib",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src"]
}
```

- [ ] **Step 2: Write the function**

`functions/src/verifyPurchase.ts`:
```typescript
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { google } from "googleapis";

if (getApps().length === 0) initializeApp();

const PACKAGE_NAME = "com.dwell.app";

export const verifyPurchase = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

  const purchaseToken = request.data?.purchaseToken as string | undefined;
  const productId = request.data?.productId as string | undefined;
  if (!purchaseToken || !productId) {
    throw new HttpsError("invalid-argument", "purchaseToken and productId required.");
  }

  // The function's service account needs the "View financial data" Play role.
  const auth = new google.auth.GoogleAuth({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  const androidpublisher = google.androidpublisher({ version: "v3", auth });

  let purchase;
  try {
    const res = await androidpublisher.purchases.products.get({
      packageName: PACKAGE_NAME,
      productId,
      token: purchaseToken,
    });
    purchase = res.data;
  } catch (e) {
    throw new HttpsError("permission-denied", "Could not validate purchase.");
  }

  // purchaseState: 0 = purchased, 1 = cancelled, 2 = pending.
  if (purchase.purchaseState !== 0) {
    throw new HttpsError("failed-precondition", "Purchase not in a paid state.");
  }

  await getFirestore().collection("users").doc(uid).set(
    { premium: true, updatedAt: new Date() },
    { merge: true },
  );

  return { premium: true };
});
```

`functions/src/index.ts` (add the export; create the file if absent):
```typescript
export { verifyPurchase } from "./verifyPurchase";
```

- [ ] **Step 3: Register the codebase in firebase.json (if not present)**

Ensure `firebase.json` has a `functions` entry, e.g.:
```json
"functions": { "source": "functions", "codebase": "default" }
```

- [ ] **Step 4: Build and deploy**

Run: `cd functions && npm install && npm run build && cd ..`
Expected: `tsc` succeeds, `functions/lib/` produced.
Run: `firebase deploy --only functions:verifyPurchase`
Expected: `✔ functions[verifyPurchase] ... Successful create/update operation`.

Ops note (not a code step): grant the function's service account the **View financial data** permission in Play Console → Users and permissions, and create the one-time, non-consumable product `unlock_premium` in Play Console → Monetize → Products → In-app products. The product id is immutable once created; it must equal `BillingRepositoryImpl.productId`.

- [ ] **Step 5: Commit**

```bash
git add functions firebase.json
git commit -m "feat(functions): add verifyPurchase to grant premium server-side"
```

---

## Task 8: UnlockViewModel — TDD

Exposes premium as state and triggers the purchase. Unit-tested with fakes.

**Files:**
- Create: `app/src/main/java/com/dwell/app/ui/unlock/UnlockViewModel.kt`
- Test: `app/src/test/java/com/dwell/app/ui/unlock/UnlockViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.dwell.app.ui.unlock

import android.app.Activity
import app.cash.turbine.test
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.PurchaseResult
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
import org.mockito.kotlin.mock

class UnlockViewModelTest {

    private val premium = MutableStateFlow(false)
    private val entitlements = object : EntitlementRepository {
        override fun observePremium(): Flow<Boolean> = premium
    }

    private class RecordingBilling : BillingRepository {
        var launched = false
        override val productId = "unlock_premium"
        override suspend fun launchPurchase(activity: Activity): PurchaseResult {
            launched = true
            return PurchaseResult.Verifying
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(ExperimentalCoroutinesApi::class)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes premium from the repository`() = runTest {
        val vm = UnlockViewModel(entitlements, RecordingBilling())
        vm.isPremium.test {
            assertEquals(false, awaitItem())
            premium.value = true
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unlock launches the billing flow`() = runTest {
        val billing = RecordingBilling()
        val vm = UnlockViewModel(entitlements, billing)
        vm.unlock(mock())
        assertTrue(billing.launched)
    }
}
```

> Note: this test uses `org.mockito.kotlin.mock()` only to supply a throwaway `Activity`. If `mockito-kotlin` is not already a test dependency, add `testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")` to `app/build.gradle.kts` (and a `mockitoKotlin` catalog entry) in this step, or replace `mock()` with a hand-rolled `Activity` stub.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.ui.unlock.UnlockViewModelTest" -q`
Expected: FAIL — `UnlockViewModel` does not exist.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.dwell.app.ui.unlock

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.PurchaseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnlockViewModel @Inject constructor(
    entitlements: EntitlementRepository,
    private val billing: BillingRepository,
) : ViewModel() {

    val isPremium: StateFlow<Boolean> = entitlements.observePremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Last purchase outcome, for a toast/snackbar. Null until a purchase is attempted. */
    var lastResult: PurchaseResult? = null
        private set

    fun unlock(activity: Activity) {
        viewModelScope.launch { lastResult = billing.launchPurchase(activity) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dwell.app.ui.unlock.UnlockViewModelTest" -q`
Expected: PASS — 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dwell/app/ui/unlock/ app/src/test/java/com/dwell/app/ui/unlock/
git commit -m "feat(unlock): add UnlockViewModel exposing premium and purchase trigger"
```

---

## Task 9: More-screen unlock row

Add a row to `MoreScreen` that shows "Unlock Dwell" when locked (taps → purchase) and "Unlocked" when premium. Copy lives in strings; the row reuses the existing `SettingsRow`.

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt`
- Modify: the caller that hosts `MoreScreen` (see Step 3)

- [ ] **Step 1: Add the copy**

In `strings.xml`, before the closing `</resources>`, add:
```xml
    <!-- Unlock / premium -->
    <string name="unlock_title">Unlock Dwell</string>
    <string name="unlock_subtitle">Remove ads and unlock matched widgets and home styles.</string>
    <string name="unlock_unlocked">Unlocked</string>
    <string name="unlock_thanks">Thanks — everything\'s unlocked.</string>
    <string name="unlock_cancelled">No worries, maybe later.</string>
    <string name="unlock_error">Couldn\'t complete the purchase. Try again.</string>
```

- [ ] **Step 2: Add the row to MoreScreen**

In `MoreScreen.kt`, add parameters `isPremium: Boolean` and `onUnlock: () -> Unit` to the `MoreScreen` signature, and insert this block inside the `Column`, after the favorites row + divider:
```kotlin
            if (isPremium) {
                SettingsRow(
                    title = stringResource(R.string.unlock_unlocked),
                    onClick = {},
                    leadingIcon = painterResource(R.drawable.ic_check),
                )
            } else {
                SettingsRow(
                    title = stringResource(R.string.unlock_title),
                    onClick = onUnlock,
                    leadingIcon = painterResource(R.drawable.ic_check),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
```

> Use an existing drawable to avoid a new asset; `ic_check` is present. Swap for a dedicated unlock glyph later if design wants one.

- [ ] **Step 3: Wire the ViewModel at the call site**

Find where `MoreScreen(` is invoked:
Run: `grep -rn "MoreScreen(" app/src/main/java`
In that composable, obtain the VM and pass the new params:
```kotlin
val unlockViewModel: UnlockViewModel = hiltViewModel()
val isPremium by unlockViewModel.isPremium.collectAsStateWithLifecycle()
val activity = LocalActivity.current ?: LocalContext.current as Activity
MoreScreen(
    // ...existing args...
    isPremium = isPremium,
    onUnlock = { unlockViewModel.unlock(activity) },
)
```
Add the imports used above:
```kotlin
import androidx.activity.compose.LocalActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwell.app.ui.unlock.UnlockViewModel
```
(If `LocalActivity` is unavailable in the Compose version in use, resolve the activity with `LocalContext.current.findActivity()` via a small helper, or pass the activity down from `MainActivity`.)

- [ ] **Step 4: Verify it builds and the existing tests stay green**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL; all unit tests pass.

- [ ] **Step 5: Analytics — log the unlock**

The TRD analytics list includes `purchase_unlock` ([dwell-docs/02-TRD.md](../../../dwell-docs/02-TRD.md) §analytics). Log it when premium first becomes true. In `UnlockViewModel`, inject `FirebaseAnalytics` and collect `isPremium`, firing once on the false→true transition:
```kotlin
// in UnlockViewModel constructor params:
private val analytics: com.google.firebase.analytics.FirebaseAnalytics,
// in an init block:
init {
    viewModelScope.launch {
        var was = false
        isPremium.collect { now ->
            if (now && !was) analytics.logEvent("purchase_unlock", null)
            was = now
        }
    }
}
```
Provide `FirebaseAnalytics` in `DataModule`:
```kotlin
@Provides @Singleton
fun provideAnalytics(@ApplicationContext c: Context): com.google.firebase.analytics.FirebaseAnalytics =
    com.google.firebase.analytics.FirebaseAnalytics.getInstance(c)
```
Re-run the Task 8 test (add the analytics fake/mock to the constructor) to keep it green.

- [ ] **Step 6: On-device verification (the real end-to-end)**

With the function deployed (Task 7) and a license-test account, install and buy:
Run: `./gradlew :app:installDebug`
Then: open More → tap **Unlock Dwell** → complete the test purchase → confirm the row flips to **Unlocked** and re-launching on a second signed-in device shows Unlocked. Confirm Logcat shows the `verifyPurchase` callable returning `{premium:true}`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/dwell/app/ui/screens/MoreScreen.kt app/src/main/java/com/dwell/app/ui/unlock/UnlockViewModel.kt app/src/main/java/com/dwell/app/di/DataModule.kt
git commit -m "feat(unlock): add More-screen unlock row, wire VM, log purchase_unlock"
```

---

## Self-Review

**Spec coverage** (against the repositioned strategy):
- "Unlock removes ads + coordinated layer, server-owned `premium` flag" → Tasks 1, 4, 7 (flag + gate + only-writer). ✓
- "Wallpapers/widgets free; no content gating" → nothing in this plan gates wallpapers/widgets; the gate is a `Flow<Boolean>` consumers opt into. ✓
- "One-time `unlock_premium` via Play Billing, premium respected across devices" → Tasks 6, 7 (purchase + verify), Task 4 (cross-device via Firestore listener). ✓
- "`premium` flag, server-owned, never client-writable" → Task 1 (rules), Task 7 (Admin SDK writer). ✓
- "Free launcher (Zen) / matched presets / extra styles are the paid extras" → **out of scope by design** — those subsystems don't exist yet; they will read `EntitlementRepository.observePremium()`. Listed as follow-on plans in the header. ✓
- TRD analytics `purchase_unlock` → Task 9 Step 5. ✓

**Placeholder scan:** No "TBD"/"handle errors"/"similar to". The two framework-bound tasks (6 BillingClient, 7 Cloud Function) carry full real code; their verification is on-device/deploy because JVM unit tests cannot exercise `BillingClient` or the Play Developer API — that is an honest test-boundary, not a placeholder.

**Type consistency:** `EntitlementRepository.observePremium(): Flow<Boolean>` is the single name used in Tasks 4, 8, 9 and by every follow-on. `productId = "unlock_premium"` matches the Play product and the CF `productId` argument. `premium` is the Firestore field across rules (Task 1), CF writer (Task 7), and remote reader (Task 3). `PurchaseResult` variants used in Task 6 match those defined in Task 6 Step 1.

**Known follow-ups (not gaps in this plan):**
1. Replace `MainScope()`/`GlobalScope` in `BillingRepositoryImpl` with an injected application `CoroutineScope` (noted inline in Task 6).
2. AdMob rendering gated on `!premium` — next plan.
3. Decide whether the v1 unlock ships with extra home styles or only matched presets (open call from the strategy edit) — affects whether the home-style picker is pulled earlier than P1.
