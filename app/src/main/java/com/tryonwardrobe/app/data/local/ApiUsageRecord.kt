package com.tryonwardrobe.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_usage_records")
data class ApiUsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String,
    val taskType: String,
    val promptTokens: Int,
    val candidateTokens: Int,
    val totalTokens: Int,
    val generatedImages: Int
) {
    companion object {
        const val TASK_TAGGING = "TAGGING"
        const val TASK_COLOR_BODY_PROFILE = "COLOR_BODY_PROFILE"
        const val TASK_STYLING = "STYLING"
        const val TASK_TRY_ON = "TRY_ON"
        const val TASK_MALL_STYLING = "MALL_STYLING"
        const val TASK_CONNECTION_TEST = "CONNECTION_TEST"
    }
}
