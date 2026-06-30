package com.dwell.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Play [BillingClient]. The client never writes the entitlement: on a
 * successful purchase it calls the `verifyPurchase` Cloud Function, which
 * validates the token server-side and writes `premium`. The flag then flips
 * via the Firestore listener in [EntitlementRepository].
 */
@Singleton
class BillingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val functions: FirebaseFunctions,
    private val scope: CoroutineScope,
) : BillingRepository {

    override val productId = "unlock_premium"

    // Each launch installs a fresh deferred the listener completes.
    @Volatile private var pending: CompletableDeferred<PurchaseResult>? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        val deferred = pending ?: return@PurchasesUpdatedListener
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { verify(it, deferred) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                deferred.complete(PurchaseResult.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                if (purchases.isNullOrEmpty()) deferred.complete(PurchaseResult.AlreadyOwned)
                else purchases.forEach { verify(it, deferred) }
            else ->
                deferred.complete(PurchaseResult.Error("billing ${result.responseCode}"))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
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

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                ).build()
            client.launchBillingFlow(activity, params)
        }.onFailure { return PurchaseResult.Error(it.message ?: "billing setup failed") }
        return deferred.await()
    }

    private fun verify(purchase: Purchase, deferred: CompletableDeferred<PurchaseResult>) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        // Fire-and-await server verification; the premium flag flips via Firestore.
        scope.launch {
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
                if (!deferred.isCompleted) {
                    deferred.complete(PurchaseResult.Error(it.message ?: "verify failed"))
                }
            }
        }
    }

    private suspend fun ensureConnected() {
        if (client.isReady) return
        val connected = CompletableDeferred<Unit>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    connected.complete(Unit)
                } else {
                    connected.completeExceptionally(
                        IllegalStateException("connect ${result.responseCode}"),
                    )
                }
            }
            override fun onBillingServiceDisconnected() { /* retried on next launch */ }
        })
        connected.await()
    }
}
