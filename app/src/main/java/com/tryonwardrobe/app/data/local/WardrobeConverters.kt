package com.tryonwardrobe.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WardrobeConverters {
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type
    private val longListType = object : TypeToken<List<Long>>() {}.type

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<String>>(json, stringListType) }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String = gson.toJson(value ?: emptyList<Long>())

    @TypeConverter
    fun toLongList(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { gson.fromJson<List<Long>>(json, longListType) }
            .getOrDefault(emptyList())
    }
}
