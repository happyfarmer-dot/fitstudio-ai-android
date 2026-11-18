package com.tryonwardrobe.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val imagePath: String,
    val category: String,
    val subCategory: String,
    val color: String,
    val fit: String,
    val styleTags: List<String>,
    val season: List<String>,
    val createdAt: Long = System.currentTimeMillis()
)
