package com.tryonwardrobe.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fitstudio_settings"
)

class SettingsDataStore(private val context: Context) {

    companion object {
        const val MODEL_FLASH_IMAGE = "gemini-2.5-flash-image"
        const val MODEL_PRO_IMAGE_PREVIEW = "gemini-3-pro-image-preview"
        const val MODEL_LOGIC_FLASH = "gemini-2.5-flash"

        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_ACTIVE_PERSON_PHOTO_ID = longPreferencesKey("active_person_photo_id")
        private val KEY_TRY_ON_MODEL = stringPreferencesKey("try_on_image_model")
    }

    val geminiApiKeyFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY].orEmpty()
    }

    val activePersonPhotoIdFlow: Flow<Long?> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PERSON_PHOTO_ID]?.takeIf { it > 0L }
    }

    val tryOnModelFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_TRY_ON_MODEL] ?: MODEL_FLASH_IMAGE
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setActivePersonPhotoId(photoId: Long?) {
        context.settingsDataStore.edit { prefs ->
            if (photoId == null || photoId <= 0L) {
                prefs.remove(KEY_ACTIVE_PERSON_PHOTO_ID)
            } else {
                prefs[KEY_ACTIVE_PERSON_PHOTO_ID] = photoId
            }
        }
    }

    suspend fun setTryOnModel(modelId: String) {
        val valid = if (modelId == MODEL_PRO_IMAGE_PREVIEW) {
            MODEL_PRO_IMAGE_PREVIEW
        } else {
            MODEL_FLASH_IMAGE
        }
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TRY_ON_MODEL] = valid
        }
    }
}
