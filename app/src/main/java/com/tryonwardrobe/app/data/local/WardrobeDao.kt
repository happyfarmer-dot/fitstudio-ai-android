package com.tryonwardrobe.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeDao {

    // --- Clothing Items ---
    @Query("SELECT * FROM clothing_items ORDER BY createdAt DESC")
    fun observeAllClothing(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items ORDER BY createdAt DESC")
    suspend fun getAllClothingSnapshot(): List<ClothingItem>

    @Query("SELECT * FROM clothing_items WHERE id IN (:ids)")
    suspend fun getClothingByIds(ids: List<Long>): List<ClothingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClothing(item: ClothingItem): Long

    @Update
    suspend fun updateClothing(item: ClothingItem)

    @Query("UPDATE clothing_items SET wearCount = wearCount + 1, lastWornTimestamp = :wornAt WHERE id IN (:ids)")
    suspend fun incrementWearCountForIds(ids: List<Long>, wornAt: Long)

    @Delete
    suspend fun deleteClothing(item: ClothingItem)

    @Query("DELETE FROM clothing_items")
    suspend fun deleteAllClothing()

    // --- Base Full-Body Person Photos ---
    @Query("SELECT * FROM person_photos ORDER BY createdAt DESC")
    fun observeAllPersonPhotos(): Flow<List<PersonPhoto>>

    @Query("SELECT * FROM person_photos ORDER BY createdAt DESC")
    suspend fun getAllPersonPhotosSnapshot(): List<PersonPhoto>

    @Query("SELECT * FROM person_photos WHERE id = :id LIMIT 1")
    suspend fun getPersonPhotoById(id: Long): PersonPhoto?

    @Query("SELECT * FROM person_photos ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestPersonPhoto(): PersonPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonPhoto(photo: PersonPhoto): Long

    @Delete
    suspend fun deletePersonPhoto(photo: PersonPhoto)

    @Query("DELETE FROM person_photos")
    suspend fun deleteAllPersonPhotos()

    // --- Virtual Try-On Cache ---
    @Query("SELECT * FROM try_on_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getTryOnCacheByKey(cacheKey: String): TryOnCache?

    @Query("SELECT * FROM try_on_cache ORDER BY createdAt DESC LIMIT 20")
    fun observeRecentTryOnCaches(): Flow<List<TryOnCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTryOnCache(entry: TryOnCache)

    @Query("SELECT * FROM try_on_cache WHERE personPhotoId = :personPhotoId")
    suspend fun getCachesByPersonPhotoId(personPhotoId: Long): List<TryOnCache>

    @Query("DELETE FROM try_on_cache WHERE personPhotoId = :personPhotoId")
    suspend fun deleteCachesByPersonPhotoId(personPhotoId: Long)

    @Delete
    suspend fun deleteTryOnCache(entry: TryOnCache)

    @Query("DELETE FROM try_on_cache")
    suspend fun deleteAllTryOnCaches()

    // --- API Usage Records ---
    @Query("SELECT * FROM api_usage_records ORDER BY timestamp DESC")
    fun observeAllApiUsage(): Flow<List<ApiUsageRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiUsage(record: ApiUsageRecord): Long

    @Query("DELETE FROM api_usage_records")
    suspend fun clearAllApiUsage()
}
