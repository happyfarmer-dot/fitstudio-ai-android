package com.tryonwardrobe.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ClothingItem::class,
        PersonPhoto::class,
        TryOnCache::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {

    abstract fun wardrobeDao(): WardrobeDao

    companion object {
        @Volatile
        private var INSTANCE: WardrobeDatabase? = null

        fun getInstance(context: Context): WardrobeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WardrobeDatabase::class.java,
                    "fitstudio_wardrobe.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
