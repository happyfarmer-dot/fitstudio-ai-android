package com.tryonwardrobe.app

import android.app.Application
import com.tryonwardrobe.app.data.local.WardrobeDatabase
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.data.repository.WardrobeRepository
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import com.tryonwardrobe.app.service.vision.BackgroundRemover

class WardrobeApplication : Application() {

    val database: WardrobeDatabase by lazy { WardrobeDatabase.getInstance(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    val wardrobeRepository: WardrobeRepository by lazy {
        WardrobeRepository(
            context = applicationContext,
            dao = database.wardrobeDao(),
            settings = settingsDataStore
        )
    }
    val backgroundRemover: BackgroundRemover by lazy { BackgroundRemover(applicationContext) }
    val geminiWardrobeService: GeminiWardrobeService by lazy { GeminiWardrobeService() }
}
