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
