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
    val tagStatus: String = TAG_STATUS_DONE,
    val tagError: String? = null,
    val wearCount: Int = 0,
    val lastWornTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TAG_STATUS_PENDING = "PENDING"
        const val TAG_STATUS_DONE = "DONE"
        const val TAG_STATUS_FAILED = "FAILED"

        const val CAT_TOP = "上衣"
        const val CAT_BOTTOM = "下著"
        const val CAT_OUTER = "外套"
        const val CAT_ONE_PIECE = "連身"
        const val CAT_SHOES = "鞋履"
        const val CAT_HAT_SCARF = "帽子圍巾"
        const val CAT_EYEWEAR = "眼鏡"
        const val CAT_HAIR_ACCESSORY = "髮飾"
        const val CAT_BAG = "包袋"
        const val CAT_ACCESSORY = "飾品配件"

        val ALL_CATEGORIES = listOf(
            CAT_TOP,
            CAT_BOTTOM,
            CAT_OUTER,
            CAT_ONE_PIECE,
            CAT_SHOES,
            CAT_HAT_SCARF,
            CAT_EYEWEAR,
            CAT_HAIR_ACCESSORY,
            CAT_BAG,
            CAT_ACCESSORY
        )

        const val NINETY_DAYS_MS = 90L * 24L * 60L * 60L * 1000L
    }

    fun isDormantOver90Days(nowMs: Long = System.currentTimeMillis()): Boolean {
        val refTime = lastWornTimestamp ?: createdAt
        return (nowMs - refTime) >= NINETY_DAYS_MS
    }
}
