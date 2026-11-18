package com.tryonwardrobe.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "try_on_cache")
data class TryOnCache(
    @PrimaryKey
    val cacheKey: String,
    val personPhotoId: Long,
    val personPhotoPath: String,
    val clothingIds: List<Long>,
    val resultImagePath: String,
    val modelUsed: String,
    val createdAt: Long = System.currentTimeMillis()
)
