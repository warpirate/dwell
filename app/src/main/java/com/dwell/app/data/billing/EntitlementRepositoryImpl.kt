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
