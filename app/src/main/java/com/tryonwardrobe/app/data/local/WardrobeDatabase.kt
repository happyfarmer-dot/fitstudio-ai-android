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
        TryOnCache::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {

    abstract fun wardrobeDao(): WardrobeDao

    companion object {
        @Volatile
        private var INSTANCE: WardrobeDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN tagStatus TEXT NOT NULL DEFAULT 'DONE'"
                )
                db.execSQL(
                    "ALTER TABLE clothing_items ADD COLUMN tagError TEXT"
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
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
