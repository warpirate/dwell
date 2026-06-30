package com.dwell.app.di

import com.dwell.app.data.auth.AuthRepository
import com.dwell.app.data.auth.AuthRepositoryImpl
import com.dwell.app.data.billing.BillingRepository
import com.dwell.app.data.billing.BillingRepositoryImpl
import com.dwell.app.data.billing.EntitlementRemoteSource
import com.dwell.app.data.billing.EntitlementRemoteSourceImpl
import com.dwell.app.data.billing.EntitlementRepository
import com.dwell.app.data.billing.EntitlementRepositoryImpl
import com.dwell.app.data.favorites.FavoritesRemoteSource
import com.dwell.app.data.favorites.FavoritesRemoteSourceImpl
import com.dwell.app.data.favorites.FavoritesRepository
import com.dwell.app.data.favorites.FavoritesRepositoryImpl
import com.dwell.app.data.repository.WallpaperRepository
import com.dwell.app.data.repository.WallpaperRepositoryImpl
import com.dwell.app.data.wallpaper.WallpaperApplier
import com.dwell.app.data.wallpaper.WallpaperApplierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(impl: WallpaperRepositoryImpl): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperApplier(impl: WallpaperApplierImpl): WallpaperApplier

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRemoteSource(impl: FavoritesRemoteSourceImpl): FavoritesRemoteSource

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindEntitlementRemoteSource(impl: EntitlementRemoteSourceImpl): EntitlementRemoteSource

    @Binds
    @Singleton
    abstract fun bindEntitlementRepository(impl: EntitlementRepositoryImpl): EntitlementRepository

    @Binds
    @Singleton
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository
}
