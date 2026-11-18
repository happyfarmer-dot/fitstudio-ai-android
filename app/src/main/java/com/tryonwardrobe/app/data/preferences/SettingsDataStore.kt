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

data class GeminiModelOption(
    val id: String,
    val displayName: String,
    val description: String,
    val isRecommended: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    companion object {
        // 2026 Tagging & Styling Logic Models (JSON Mode)
        const val MODEL_TAGGING_LITE = "gemini-3.5-flash-lite"
        const val MODEL_TAGGING_PRO_ACCURACY = "gemini-3.8-flash"
        const val DEFAULT_TAGGING_MODEL = MODEL_TAGGING_LITE

        // 2026 Photorealistic Multi-Image Try-On Models
        const val MODEL_TRYON_FLASH_IMAGE = "gemini-3.1-flash-image"
        const val MODEL_TRYON_FLASH_LITE_IMAGE = "gemini-3.1-flash-lite-image"
        const val MODEL_TRYON_PRO_IMAGE = "gemini-3-pro-image"
        const val MODEL_TRYON_NANO_BANANA = "gemini-nano-banana-2.1"
        const val DEFAULT_TRY_ON_MODEL = MODEL_TRYON_FLASH_IMAGE

        val TAGGING_MODEL_OPTIONS = listOf(
            GeminiModelOption(
                id = MODEL_TAGGING_LITE,
                displayName = "gemini-3.5-flash-lite（預設推薦）",
                description = "極低 Token 延遲，適合快速結構化分類打標與衣櫃組合推理。",
                isRecommended = true
            ),
            GeminiModelOption(
                id = MODEL_TAGGING_PRO_ACCURACY,
                displayName = "gemini-3.8-flash",
                description = "高精準版型、布料與多語風格辨識，複雜穿搭情境推薦首選。"
            )
        )

        val TRY_ON_MODEL_OPTIONS = listOf(
            GeminiModelOption(
                id = MODEL_TRYON_FLASH_IMAGE,
                displayName = "gemini-3.1-flash-image（預設推薦）",
                description = "平衡畫質、寫實光影皺褶與推論成本的標準換裝主力模型。",
                isRecommended = true
            ),
            GeminiModelOption(
                id = MODEL_TRYON_FLASH_LITE_IMAGE,
                displayName = "gemini-3.1-flash-lite-image",
                description = "極速生圖與極省 API 額度，適合快速大量試搭預覽。"
            ),
            GeminiModelOption(
                id = MODEL_TRYON_PRO_IMAGE,
                displayName = "gemini-3-pro-image",
                description = "高細節布料材質、縫線與光線陰影還原，精修質感最強。"
            ),
            GeminiModelOption(
                id = MODEL_TRYON_NANO_BANANA,
                displayName = "gemini-nano-banana-2.1",
                description = "強化人物五官與多件疊穿物件一致性，降低姿態偏移。"
            )
        )

        private val VALID_TAGGING_IDS = TAGGING_MODEL_OPTIONS.map { it.id }.toSet()
        private val VALID_TRY_ON_IDS = TRY_ON_MODEL_OPTIONS.map { it.id }.toSet()

        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_ACTIVE_PERSON_PHOTO_ID = longPreferencesKey("active_person_photo_id")
        private val KEY_TAGGING_MODEL = stringPreferencesKey("tagging_logic_model")
        private val KEY_TRY_ON_MODEL = stringPreferencesKey("try_on_image_model")
    }

    val geminiApiKeyFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY].orEmpty()
    }

    val activePersonPhotoIdFlow: Flow<Long?> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PERSON_PHOTO_ID]?.takeIf { it > 0L }
    }

    val taggingModelFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[KEY_TAGGING_MODEL]
        if (stored != null && stored in VALID_TAGGING_IDS) stored else DEFAULT_TAGGING_MODEL
    }

    val tryOnModelFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        val stored = prefs[KEY_TRY_ON_MODEL]
        if (stored != null && stored in VALID_TRY_ON_IDS) stored else DEFAULT_TRY_ON_MODEL
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

    suspend fun setTaggingModel(modelId: String) {
        val valid = if (modelId in VALID_TAGGING_IDS) modelId else DEFAULT_TAGGING_MODEL
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TAGGING_MODEL] = valid
        }
    }

    suspend fun setTryOnModel(modelId: String) {
        val valid = if (modelId in VALID_TRY_ON_IDS) modelId else DEFAULT_TRY_ON_MODEL
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_TRY_ON_MODEL] = valid
        }
    }
}
