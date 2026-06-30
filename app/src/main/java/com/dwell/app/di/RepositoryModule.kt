package com.dwell.app.di

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
}
