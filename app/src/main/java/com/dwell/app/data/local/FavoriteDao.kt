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
