package com.dwell.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.dwell.app.data.local.CategoryDao
import com.dwell.app.data.local.DwellDatabase
import com.dwell.app.data.local.FavoriteDao
import com.dwell.app.data.local.WallpaperDao
import com.dwell.app.data.util.NowProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        // Explicit persistent cache so reads serve from disk when offline.
        // (Firestore persists by default; set here to make the intent explicit.)
        return FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DwellDatabase =
        Room.databaseBuilder(context, DwellDatabase::class.java, DwellDatabase.NAME)
            // Dev-only: wipe on schema change. Replace with real migrations
            // before any production data exists.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWallpaperDao(database: DwellDatabase): WallpaperDao = database.wallpaperDao()

    @Provides
    fun provideCategoryDao(database: DwellDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideFavoriteDao(database: DwellDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideWidgetDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("widget_styles") }

    /** Application-scoped coroutine scope for fire-and-forget work (e.g. purchase verification). */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }
}
