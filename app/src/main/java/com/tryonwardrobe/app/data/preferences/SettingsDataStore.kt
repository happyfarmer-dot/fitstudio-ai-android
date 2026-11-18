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

data class PersonalColorOption(
    val id: String,
    val title: String,
    val recommendedColors: String,
    val avoidColors: String
)

data class BodyShapeOption(
    val id: String,
    val title: String,
    val fitAdvice: String
)

data class PersonalStyleProfile(
    val personalColorSeason: String = "",
    val bodyShape: String = "",
    val recommendedColors: String = "",
    val avoidColors: String = "",
    val fitAdvice: String = "",
    val updatedAt: Long = 0L
) {
    val isConfigured: Boolean
        get() = personalColorSeason.isNotBlank() || bodyShape.isNotBlank()
}

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

        val PERSONAL_COLOR_OPTIONS = listOf(
            PersonalColorOption(
                id = "春暖型",
                title = "春暖型（Warm Spring）",
                recommendedColors = "暖米白、蜜桃粉、珊瑚橘、亮駝色、嫩草綠、奶油黃",
                avoidColors = "死黑、冷灰、深紫藍等過度沉重暗冷色系"
            ),
            PersonalColorOption(
                id = "夏冷型",
                title = "夏冷型（Cool Summer）",
                recommendedColors = "霧霾藍、薰衣草紫、莫蘭迪灰、冷米白、乾燥玫瑰粉、薄荷綠",
                avoidColors = "高飽和螢光橘、土黃、深棕等濃烈暖色"
            ),
            PersonalColorOption(
                id = "秋暖型",
                title = "秋暖型（Warm Autumn）",
                recommendedColors = "赤陶橘、橄欖綠、焦糖棕、芥末黃、燕麥色、磚紅",
                avoidColors = "冷亮粉、冰藍色、純白與高冷螢光色"
            ),
            PersonalColorOption(
                id = "冬冷型",
                title = "冬冷型（Cool Winter）",
                recommendedColors = "純淨白、曜石黑、深海軍藍、寶石紅、冰灰、冷杉綠",
                avoidColors = "混濁大地色、暖卡其、淺橘棕等低對比暖濁色"
            )
        )

        val BODY_SHAPE_OPTIONS = listOf(
            BodyShapeOption(
                id = "H 型（直筒勻稱）",
                title = "H 型（直筒勻稱）",
                fitAdvice = "適合透過硬挺外套、疊穿或紮衣服營造腰線層次與上下身比例。"
            ),
            BodyShapeOption(
                id = "倒三角（寬肩挺拔）",
                title = "倒三角（寬肩挺拔）",
                fitAdvice = "上衣宜選簡潔合身或落肩V領，下著搭配直筒或微寬褲平衡視覺重心。"
            ),
            BodyShapeOption(
                id = "梨形（下身穩重）",
                title = "梨形（下身穩重）",
                fitAdvice = "上身可運用亮色或結構感領口吸引視線，下著優選高腰A字或挺版直筒剪裁。"
            ),
            BodyShapeOption(
                id = "沙漏型（腰線明顯）",
                title = "沙漏型（腰線明顯）",
                fitAdvice = "適合順應身形曲線的合身剪裁或高腰紮衣穿搭，避免過度寬大遮掩腰線。"
            ),
            BodyShapeOption(
                id = "O 型（圓潤舒適）",
                title = "O 型（圓潤舒適）",
                fitAdvice = "推薦垂墜感佳的微寬版上衣搭配敞開式挺版外套，拉長縱向視覺線條。"
            )
        )

        private val VALID_TAGGING_IDS = TAGGING_MODEL_OPTIONS.map { it.id }.toSet()
        private val VALID_TRY_ON_IDS = TRY_ON_MODEL_OPTIONS.map { it.id }.toSet()

        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_ACTIVE_PERSON_PHOTO_ID = longPreferencesKey("active_person_photo_id")
        private val KEY_TAGGING_MODEL = stringPreferencesKey("tagging_logic_model")
        private val KEY_TRY_ON_MODEL = stringPreferencesKey("try_on_image_model")

        private val KEY_PERSONAL_COLOR_SEASON = stringPreferencesKey("personal_color_season")
        private val KEY_BODY_SHAPE = stringPreferencesKey("body_shape")
        private val KEY_RECOMMENDED_COLORS = stringPreferencesKey("recommended_colors")
        private val KEY_AVOID_COLORS = stringPreferencesKey("avoid_colors")
        private val KEY_FIT_ADVICE = stringPreferencesKey("fit_advice")
        private val KEY_PROFILE_UPDATED_AT = longPreferencesKey("profile_updated_at")
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

    val personalStyleProfileFlow: Flow<PersonalStyleProfile> = context.settingsDataStore.data.map { prefs ->
        PersonalStyleProfile(
            personalColorSeason = prefs[KEY_PERSONAL_COLOR_SEASON].orEmpty(),
            bodyShape = prefs[KEY_BODY_SHAPE].orEmpty(),
            recommendedColors = prefs[KEY_RECOMMENDED_COLORS].orEmpty(),
            avoidColors = prefs[KEY_AVOID_COLORS].orEmpty(),
            fitAdvice = prefs[KEY_FIT_ADVICE].orEmpty(),
            updatedAt = prefs[KEY_PROFILE_UPDATED_AT] ?: 0L
        )
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

    suspend fun savePersonalStyleProfile(profile: PersonalStyleProfile) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_PERSONAL_COLOR_SEASON] = profile.personalColorSeason.trim()
            prefs[KEY_BODY_SHAPE] = profile.bodyShape.trim()
            prefs[KEY_RECOMMENDED_COLORS] = profile.recommendedColors.trim()
            prefs[KEY_AVOID_COLORS] = profile.avoidColors.trim()
            prefs[KEY_FIT_ADVICE] = profile.fitAdvice.trim()
            prefs[KEY_PROFILE_UPDATED_AT] = System.currentTimeMillis()
        }
    }
}
