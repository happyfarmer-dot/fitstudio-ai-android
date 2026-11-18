package com.tryonwardrobe.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClothingItem::class,
        PersonPhoto::class,
        TryOnCache::class,
        ApiUsageRecord::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {

    abstract fun wardrobeDao(): WardrobeDao

    companion object {
        @Volatile
        private var INSTANCE: WardrobeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN tagStatus TEXT NOT NULL DEFAULT 'DONE'"
                )
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN tagError TEXT"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN wearCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN lastWornTimestamp INTEGER"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS api_usage_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        modelId TEXT NOT NULL,
                        taskType TEXT NOT NULL,
                        promptTokens INTEGER NOT NULL,
                        candidateTokens INTEGER NOT NULL,
                        totalTokens INTEGER NOT NULL,
                        generatedImages INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): WardrobeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardrobeDatabase::class.java,
                    "fitstudio_wardrobe.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
