package com.tryonwardrobe.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "person_photos")
data class PersonPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val label: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
