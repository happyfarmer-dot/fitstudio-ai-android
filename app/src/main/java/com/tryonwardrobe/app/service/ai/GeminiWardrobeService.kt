package com.tryonwardrobe.app.service.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tryonwardrobe.app.data.catalog.MallBrandItem
import com.tryonwardrobe.app.data.local.ApiUsageRecord
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.preferences.PersonalStyleProfile
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.data.repository.OuterStylingMode
import com.tryonwardrobe.app.data.repository.TopStylingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

class GeminiApiException(
    val statusCode: Int?,
    val rawDetail: String?,
    override val message: String
) : IllegalStateException(message)

class GeminiWardrobeService {

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val VIRTUAL_TRY_ON_PROMPT =
            "Edit the first image (the person) so they are wearing the exact clothing item(s) shown in the subsequent reference image(s). STRICT PRESERVATION RULES: You must keep the person's exact face, facial features, hair, skin tone, body proportions, pose, hands, and the entire background environment completely unchanged. REALISM RULES: Render natural fabric drape, realistic folds, wrinkles, stitching details, and accurate ambient lighting and shadows matching the original photo's light source. Ensure proper layering (e.g., outerwear worn open or layered naturally over inner tops, tops naturally meeting the waistband). Photorealistic, raw DSLR photography quality, no artificial sticker look."
    }

    data class GarmentTagResult(
        val category: String,
        val subCategory: String,
        val color: String,
        val fit: String,
        val styleTags: List<String>,
        val season: List<String>
    )

    data class OutfitRecommendation(
        val title: String,
        val rationale: String,
        val itemIds: List<Long>
    )

    data class MallOutfitRecommendation(
        val title: String,
        val rationale: String,
        val mallItemIds: List<String>,
        val wardrobeItemIds: List<Long> = emptyList()
    )

    data class PersonalProfileAnalysisDto(
        val personalColorSeason: String? = null,
        val bodyShape: String? = null,
        val recommendedColors: String? = null,
        val avoidColors: String? = null,
        val fitAdvice: String? = null
    )

    data class ConnectionTestResult(
        val success: Boolean,
        val testedTaggingModel: String,
        val testedTryOnModel: String,
        val latencyMs: Long?,
        val httpCode: Int?,
        val message: String
    )

    data class UsageMetrics(
        val promptTokens: Int,
        val candidateTokens: Int,
        val totalTokens: Int,
        val generatedImages: Int
    ) {
        fun toRecord(modelId: String, taskType: String): ApiUsageRecord {
            return ApiUsageRecord(
                modelId = modelId,
                taskType = taskType,
                promptTokens = promptTokens,
                candidateTokens = candidateTokens,
                totalTokens = totalTokens,
                generatedImages = generatedImages
            )
        }
    }

    data class GeminiTrackedResult<T>(
        val data: T,
        val usage: UsageMetrics
    )

    private data class OutfitResponseDto(
        val outfits: List<OutfitRecommendation>? = null
    )

    private data class MallOutfitResponseDto(
        val outfits: List<MallOutfitRecommendation>? = null
    )

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /**
     * Tests real generateContent connectivity against the selected tagging model and try-on model
     * using minimal tokens (maxOutputTokens = 5).
     */
    suspend fun testConnection(
        apiKey: String,
        taggingModelId: String,
        tryOnModelId: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext ConnectionTestResult(
                success = false,
                testedTaggingModel = taggingModelId,
                testedTryOnModel = tryOnModelId,
                latencyMs = null,
                httpCode = null,
                message = "請先輸入 Gemini API Key（AIza...）再進行測試"
            )
        }

        val minimalPayload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", "OK") })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("maxOutputTokens", 5)
                addProperty("temperature", 0.0)
            })
        }

        val startTagMs = SystemClock.elapsedRealtime()
        try {
            executeGenerateContent(
                apiKey = trimmedKey,
                modelId = taggingModelId,
                payload = minimalPayload
            )
        } catch (ex: GeminiApiException) {
            return@withContext ConnectionTestResult(
                success = false,
                testedTaggingModel = taggingModelId,
                testedTryOnModel = tryOnModelId,
                latencyMs = SystemClock.elapsedRealtime() - startTagMs,
                httpCode = ex.statusCode,
                message = "打標模型 $taggingModelId 測試失敗：${ex.message}"
            )
        }
        val tagLatencyMs = SystemClock.elapsedRealtime() - startTagMs

        val startTryOnMs = SystemClock.elapsedRealtime()
        try {
            executeGenerateContent(
                apiKey = trimmedKey,
                modelId = tryOnModelId,
                payload = minimalPayload
            )
            val tryOnLatencyMs = SystemClock.elapsedRealtime() - startTryOnMs
            return@withContext ConnectionTestResult(
                success = true,
                testedTaggingModel = taggingModelId,
                testedTryOnModel = tryOnModelId,
                latencyMs = tagLatencyMs + tryOnLatencyMs,
                httpCode = 200,
                message = "連線驗證成功！\n• 打標模型 $taggingModelId：${tagLatencyMs} ms\n• 換裝模型 $tryOnModelId：${tryOnLatencyMs} ms"
            )
        } catch (ex: GeminiApiException) {
            val tryOnLatencyMs = SystemClock.elapsedRealtime() - startTryOnMs
            val isImageInputOnlyModalityError = ex.statusCode == 400 && (
                ex.rawDetail.orEmpty().contains("modality", ignoreCase = true) ||
                    ex.rawDetail.orEmpty().contains("image", ignoreCase = true) ||
                    ex.rawDetail.orEmpty().contains("support", ignoreCase = true)
                )

            if (isImageInputOnlyModalityError) {
                return@withContext ConnectionTestResult(
                    success = true,
                    testedTaggingModel = taggingModelId,
                    testedTryOnModel = tryOnModelId,
                    latencyMs = tagLatencyMs,
                    httpCode = 200,
                    message = "API Key 與打標模型 $taggingModelId 連線成功（${tagLatencyMs} ms）；換裝模型 $tryOnModelId 端點已可連通（正式合成需搭配圖片輸入）。"
                )
            }

            return@withContext ConnectionTestResult(
                success = false,
                testedTaggingModel = taggingModelId,
                testedTryOnModel = tryOnModelId,
                latencyMs = tryOnLatencyMs,
                httpCode = ex.statusCode,
                message = "打標模型 $taggingModelId 測試成功（${tagLatencyMs} ms），但換裝模型 $tryOnModelId 失敗：${ex.message}"
            )
        }
    }

    /**
     * Stage 2 Background Auto-Tagging supporting all 10 expanded wardrobe categories.
     */
    suspend fun autoTagGarment(
        apiKey: String,
        modelId: String,
        garmentBitmap: Bitmap
    ): GeminiTrackedResult<GarmentTagResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(null, null, "尚未設定 Gemini API Key，請先於右上角設定輸入後重試")
        }

        val prepared = resizeIfNeeded(garmentBitmap, maxEdge = 768)
        val usePng = prepared.hasAlpha()
        val imgBase64 = bitmapToBase64(
            prepared,
            if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
            if (usePng) 100 else 88
        )

        val parts = JsonArray().apply {
            add(JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", if (usePng) "image/png" else "image/jpeg")
                    addProperty("data", imgBase64)
                })
            })
            add(JsonObject().apply {
                addProperty(
                    "text",
                    """
                    請辨識這張服飾、鞋履或穿搭配件圖片，以繁體中文輸出精準結構化衣櫃標籤。
                    十大分類定義：
                    - 上衣（T恤、襯衫、針織衫、衛衣、背心等）
                    - 下著（長褲、短褲、牛仔褲、西裝褲、長裙、短裙等）
                    - 外套（西裝外套、夾克、風衣、大衣、開襟外套、羽絨衣等）
                    - 連身（洋裝、連身褲、吊帶褲等）
                    - 鞋履（運動鞋、樂福鞋、靴子、涼鞋、皮鞋等）
                    - 帽子圍巾（毛帽、鴨舌帽、漁夫帽、圍巾、絲巾等）
                    - 眼鏡（光學眼鏡、墨鏡、太陽眼鏡等）
                    - 髮飾（髮夾、髮箍、鯊魚夾、髮帶等）
                    - 包袋（托特包、斜背包、後背包、手提包、半月包等）
                    - 飾品配件（手錶、項鍊、耳環、皮帶、襪子等）
                    """.trimIndent()
                )
            })
        }

        val schema = JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", JsonObject().apply {
                add("category", JsonObject().apply {
                    addProperty("type", "STRING")
                    add("enum", JsonArray().apply {
                        ClothingItem.ALL_CATEGORIES.forEach { add(it) }
                    })
                })
                add("subCategory", JsonObject().apply {
                    addProperty("type", "STRING")
                    addProperty("description", "具體單品名稱，例如：重磅白T、水洗直筒牛仔褲、漁夫帽、玳瑁墨鏡、斜背半月包")
                })
                add("color", JsonObject().apply {
                    addProperty("type", "STRING")
                    addProperty("description", "主色系中文描述，例如：奶油白、炭黑、深海軍藍、琥珀棕")
                })
                add("fit", JsonObject().apply {
                    addProperty("type", "STRING")
                    add("enum", JsonArray().apply {
                        add("緊身"); add("合身"); add("寬鬆")
                    })
                })
                add("styleTags", JsonObject().apply {
                    addProperty("type", "ARRAY")
                    add("items", JsonObject().apply { addProperty("type", "STRING") })
                    addProperty("description", "2至3個風格標籤，例如：日系簡約、韓系質感、City Boy 城市機能、Clean Fit 極簡俐落、美式街頭、戶外山系 Gorpcore")
                })
                add("season", JsonObject().apply {
                    addProperty("type", "ARRAY")
                    add("items", JsonObject().apply {
                        addProperty("type", "STRING")
                        add("enum", JsonArray().apply {
                            add("春"); add("夏"); add("秋"); add("冬")
                        })
                    })
                })
            })
            add("required", JsonArray().apply {
                add("category"); add("subCategory"); add("color"); add("fit"); add("styleTags"); add("season")
            })
        }

        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply { add("parts", parts) })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.2)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", schema)
            })
        }

        val rawResponse = executeGenerateContent(
            apiKey = apiKey,
            modelId = modelId,
            payload = payload
        )
        val usage = parseUsageMetrics(rawResponse, generatedImages = 0)
        val jsonText = extractFirstTextPart(rawResponse)
        val parsed = runCatching { gson.fromJson(jsonText, GarmentTagResult::class.java) }.getOrNull()

        val result = GarmentTagResult(
            category = parsed?.category?.takeIf { it in ClothingItem.ALL_CATEGORIES } ?: ClothingItem.CAT_TOP,
            subCategory = parsed?.subCategory?.takeIf { it.isNotBlank() } ?: "基本款單品",
            color = parsed?.color?.takeIf { it.isNotBlank() } ?: "中性色",
            fit = parsed?.fit?.takeIf { it in listOf("緊身", "合身", "寬鬆") } ?: "合身",
            styleTags = parsed?.styleTags?.filter { it.isNotBlank() }?.ifEmpty { listOf("日系簡約") } ?: listOf("日系簡約"),
            season = parsed?.season?.filter { it in listOf("春", "夏", "秋", "冬") }
                ?.ifEmpty { listOf("春", "秋") } ?: listOf("春", "秋")
        )
        GeminiTrackedResult(data = result, usage = usage)
    }

    /**
     * AI Personal Color (4-Season) & Body Silhouette Analysis from Full-Body Base Photo.
     */
    suspend fun analyzePersonalColorAndBody(
        apiKey: String,
        modelId: String,
        personBitmap: Bitmap
    ): GeminiTrackedResult<PersonalStyleProfile> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(null, null, "請先於右上角設定頁輸入 Gemini API Key")
        }

        val prepared = resizeIfNeeded(personBitmap, maxEdge = 1024)
        val imgBase64 = bitmapToBase64(prepared, Bitmap.CompressFormat.JPEG, 90)

        val parts = JsonArray().apply {
            add(JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", "image/jpeg")
                    addProperty("data", imgBase64)
                })
            })
            add(JsonObject().apply {
                addProperty(
                    "text",
                    """
                    請根據這張全身基準照的膚色冷暖色調、髮色對比度與全身肩腰臀腿輪廓比例，進行專業「四季型個人色彩」與「身形輪廓」視覺推論分析。
                    請以繁體中文回傳結構化 JSON：
                    1. personalColorSeason：必選其一 ["春暖型", "夏冷型", "秋暖型", "冬冷型"]
                    2. bodyShape：必選其一 ["H 型（直筒勻稱）", "倒三角（寬肩挺拔）", "梨形（下身穩重）", "沙漏型（腰線明顯）", "O 型（圓潤舒適）"]
                    3. recommendedColors：最適合襯托膚色的 4~6 種具體穿搭色系建議（35字內）
                    4. avoidColors：建議避開或僅作小面積點綴的避雷色系（30字內）
                    5. fitAdvice：針對該身形輪廓的上下身版型、領口、紮衣服或外套剪裁修飾建議（45字內）
                    """.trimIndent()
                )
            })
        }

        val schema = JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", JsonObject().apply {
                add("personalColorSeason", JsonObject().apply {
                    addProperty("type", "STRING")
                    add("enum", JsonArray().apply {
                        SettingsDataStore.PERSONAL_COLOR_OPTIONS.forEach { add(it.id) }
                    })
                })
                add("bodyShape", JsonObject().apply {
                    addProperty("type", "STRING")
                    add("enum", JsonArray().apply {
                        SettingsDataStore.BODY_SHAPE_OPTIONS.forEach { add(it.id) }
                    })
                })
                add("recommendedColors", JsonObject().apply { addProperty("type", "STRING") })
                add("avoidColors", JsonObject().apply { addProperty("type", "STRING") })
                add("fitAdvice", JsonObject().apply { addProperty("type", "STRING") })
            })
            add("required", JsonArray().apply {
                add("personalColorSeason")
                add("bodyShape")
                add("recommendedColors")
                add("avoidColors")
                add("fitAdvice")
            })
        }

        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply { add("parts", parts) })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.3)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", schema)
            })
        }

        val rawResponse = executeGenerateContent(apiKey, modelId, payload)
        val usage = parseUsageMetrics(rawResponse, generatedImages = 0)
        val jsonText = extractFirstTextPart(rawResponse)
        val dto = runCatching { gson.fromJson(jsonText, PersonalProfileAnalysisDto::class.java) }.getOrNull()

        val matchedSeason = SettingsDataStore.PERSONAL_COLOR_OPTIONS.find {
            it.id == dto?.personalColorSeason
        } ?: SettingsDataStore.PERSONAL_COLOR_OPTIONS.first()

        val matchedShape = SettingsDataStore.BODY_SHAPE_OPTIONS.find {
            it.id == dto?.bodyShape
        } ?: SettingsDataStore.BODY_SHAPE_OPTIONS.first()

        val profile = PersonalStyleProfile(
            personalColorSeason = matchedSeason.id,
            bodyShape = matchedShape.id,
            recommendedColors = dto?.recommendedColors?.takeIf { it.isNotBlank() } ?: matchedSeason.recommendedColors,
            avoidColors = dto?.avoidColors?.takeIf { it.isNotBlank() } ?: matchedSeason.avoidColors,
            fitAdvice = dto?.fitAdvice?.takeIf { it.isNotBlank() } ?: matchedShape.fitAdvice,
            updatedAt = System.currentTimeMillis()
        )
        GeminiTrackedResult(data = profile, usage = usage)
    }

    /**
     * Stage 1 AI Stylist: Sends lightweight metadata JSON (zero images) + Personal Color & Body Shape profile.
     */
    suspend fun recommendOutfits(
        apiKey: String,
        modelId: String,
        wardrobe: List<ClothingItem>,
        stylePreset: String,
        customRequest: String,
        anchorItemId: Long? = null,
        personalProfile: PersonalStyleProfile? = null
    ): GeminiTrackedResult<List<OutfitRecommendation>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(null, null, "請先於右上角設定頁輸入 Gemini API Key")
        }
        require(wardrobe.isNotEmpty()) { "數位衣櫃目前沒有單品，請先拍攝或匯入衣物" }

        val compactCatalog = JsonArray().apply {
            wardrobe.forEach { item ->
                add(JsonObject().apply {
                    addProperty("id", item.id)
                    addProperty("category", item.category)
                    addProperty("subCategory", item.subCategory)
                    addProperty("color", item.color)
                    addProperty("fit", item.fit)
                    addProperty("wearCount", item.wearCount)
                    add("styleTags", gson.toJsonTree(item.styleTags))
                    add("season", gson.toJsonTree(item.season))
                })
            }
        }

        val promptBuilder = StringBuilder().apply {
            appendLine("你是專業個人造型師。請僅從以下使用者的衣櫃清單中挑選 2 至 3 套最適合且可真實穿戴在身上的完整穿搭組合（可搭配服裝、外套、鞋履及帽子/眼鏡/包袋/飾品等配件）。")
            appendLine("【目標風格／情境】：$stylePreset")
            if (personalProfile != null && personalProfile.isConfigured) {
                appendLine("【使用者個人色彩與身形檔案（請優先挑選襯膚色與修飾比例的單品）】：")
                if (personalProfile.personalColorSeason.isNotBlank()) {
                    appendLine("- 四季個人色彩：${personalProfile.personalColorSeason}（推薦色系：${personalProfile.recommendedColors}；避雷色系：${personalProfile.avoidColors}）")
                }
                if (personalProfile.bodyShape.isNotBlank()) {
                    appendLine("- 身形輪廓：${personalProfile.bodyShape}（修飾建議：${personalProfile.fitAdvice}）")
                }
            }
            if (anchorItemId != null) {
                val anchor = wardrobe.find { it.id == anchorItemId }
                if (anchor != null) {
                    appendLine("【必選鎖定單品】：id=${anchor.id} (${anchor.color} ${anchor.subCategory}, ${anchor.category})，每套推薦都必須包含此 ID。")
                }
            }
            if (customRequest.isNotBlank()) {
                appendLine("【使用者補充需求】：$customRequest")
            }
            appendLine("規則：")
            appendLine("1. 每套必須挑選衣櫃中實際存在的 itemIds（例如：上衣+下著，或連身+外套，並鼓勵適度搭配帽子圍巾、眼鏡、包袋、鞋履或飾品配件）。")
            appendLine("2. itemIds 不可包含重複 ID，且不可虛構清單外的 ID。")
            appendLine("3. rationale 請以簡潔有感的繁體中文說明為何這樣搭配（結合個人色彩或身形修飾亮點，限 55 字內）。")
            appendLine("【衣櫃 JSON 清單】：")
            append(gson.toJson(compactCatalog))
        }

        val schema = JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", JsonObject().apply {
                add("outfits", JsonObject().apply {
                    addProperty("type", "ARRAY")
                    add("items", JsonObject().apply {
                        addProperty("type", "OBJECT")
                        add("properties", JsonObject().apply {
                            add("title", JsonObject().apply { addProperty("type", "STRING") })
                            add("rationale", JsonObject().apply { addProperty("type", "STRING") })
                            add("itemIds", JsonObject().apply {
                                addProperty("type", "ARRAY")
                                add("items", JsonObject().apply { addProperty("type", "INTEGER") })
                            })
                        })
                        add("required", JsonArray().apply {
                            add("title"); add("rationale"); add("itemIds")
                        })
                    })
                })
            })
            add("required", JsonArray().apply { add("outfits") })
        }

        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", promptBuilder.toString()) })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.55)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", schema)
            })
        }

        val rawResponse = executeGenerateContent(
            apiKey = apiKey,
            modelId = modelId,
            payload = payload
        )
        val usage = parseUsageMetrics(rawResponse, generatedImages = 0)
        val jsonText = extractFirstTextPart(rawResponse)
        val dto = gson.fromJson(jsonText, OutfitResponseDto::class.java)
        val validIds = wardrobe.map { it.id }.toSet()

        val cleaned = dto?.outfits.orEmpty().mapNotNull { rec ->
            val filteredIds = rec.itemIds.filter { it in validIds }.distinct()
            if (filteredIds.isEmpty()) null
            else rec.copy(itemIds = filteredIds)
        }
        require(cleaned.isNotEmpty()) { "無法從目前衣櫃單品組成符合條件的推薦，請試著多匯入幾件上下著或調整條件" }
        GeminiTrackedResult(data = cleaned, usage = usage)
    }

    /**
     * Online Mall Outfit Recommendation (UNIQLO / GU / MUJI) — Pure Mall or Hybrid Mix with user's wardrobe.
     */
    suspend fun recommendMallOutfits(
        apiKey: String,
        modelId: String,
        mallCatalog: List<MallBrandItem>,
        wardrobe: List<ClothingItem>,
        mixWithWardrobe: Boolean,
        stylePreset: String,
        personalProfile: PersonalStyleProfile?
    ): GeminiTrackedResult<List<MallOutfitRecommendation>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(null, null, "請先於右上角設定頁輸入 Gemini API Key")
        }
        require(mallCatalog.isNotEmpty()) { "所選品牌目前無符合的常青單品" }

        val mallJson = JsonArray().apply {
            mallCatalog.forEach { m ->
                add(JsonObject().apply {
                    addProperty("id", m.id)
                    addProperty("brand", m.brand)
                    addProperty("name", m.name)
                    addProperty("category", m.category)
                    addProperty("color", m.color)
                    addProperty("fit", m.fit)
                    addProperty("material", m.material)
                })
            }
        }

        val wardrobeJson = JsonArray().apply {
            if (mixWithWardrobe) {
                wardrobe.forEach { w ->
                    add(JsonObject().apply {
                        addProperty("id", w.id)
                        addProperty("category", w.category)
                        addProperty("subCategory", w.subCategory)
                        addProperty("color", w.color)
                        addProperty("fit", w.fit)
                    })
                }
            }
        }

        val promptBuilder = StringBuilder().apply {
            appendLine("你是精通 UNIQLO、GU、無印良品 MUJI 的日系與現代穿搭顧問。")
            appendLine("【目標風格】：$stylePreset")
            appendLine("【搭配模式】：${if (mixWithWardrobe && wardrobe.isNotEmpty()) "商城新品 + 使用者現有衣櫃混搭（一衣多搭購衣建議）" else "全套品牌商城單品搭配"}")
            if (personalProfile != null && personalProfile.isConfigured) {
                appendLine("【使用者個人色彩與身形檔案】：")
                if (personalProfile.personalColorSeason.isNotBlank()) {
                    appendLine("- 個人色彩季型：${personalProfile.personalColorSeason}（推薦色系：${personalProfile.recommendedColors}）")
                }
                if (personalProfile.bodyShape.isNotBlank()) {
                    appendLine("- 身形輪廓：${personalProfile.bodyShape}（修飾建議：${personalProfile.fitAdvice}）")
                }
            }
            appendLine("請規劃 2 至 3 套高實穿度的穿搭提案：")
            appendLine("1. mallItemIds 必須從下方【商城單品清單】挑選 1 至 3 件（填寫字串 ID 如 UQ-01、GU-01、MJ-01）。")
            if (mixWithWardrobe && wardrobe.isNotEmpty()) {
                appendLine("2. wardrobeItemIds 請從下方【使用者衣櫃清單】挑選 1 至 2 件可與商城新品完美互搭的現有單品 ID。")
                appendLine("【使用者衣櫃清單】：${gson.toJson(wardrobeJson)}")
            } else {
                appendLine("2. wardrobeItemIds 請回傳空陣列 []。")
            }
            appendLine("3. rationale 請以繁體中文說明選品色彩、材質版型與個人身形/膚色的搭配理由（55字內）。")
            appendLine("【商城單品清單】：${gson.toJson(mallJson)}")
        }

        val schema = JsonObject().apply {
            addProperty("type", "OBJECT")
            add("properties", JsonObject().apply {
                add("outfits", JsonObject().apply {
                    addProperty("type", "ARRAY")
                    add("items", JsonObject().apply {
                        addProperty("type", "OBJECT")
                        add("properties", JsonObject().apply {
                            add("title", JsonObject().apply { addProperty("type", "STRING") })
                            add("rationale", JsonObject().apply { addProperty("type", "STRING") })
                            add("mallItemIds", JsonObject().apply {
                                addProperty("type", "ARRAY")
                                add("items", JsonObject().apply { addProperty("type", "STRING") })
                            })
                            add("wardrobeItemIds", JsonObject().apply {
                                addProperty("type", "ARRAY")
                                add("items", JsonObject().apply { addProperty("type", "INTEGER") })
                            })
                        })
                        add("required", JsonArray().apply {
                            add("title"); add("rationale"); add("mallItemIds"); add("wardrobeItemIds")
                        })
                    })
                })
            })
            add("required", JsonArray().apply { add("outfits") })
        }

        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", promptBuilder.toString()) })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.5)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", schema)
            })
        }

        val rawResponse = executeGenerateContent(apiKey, modelId, payload)
        val usage = parseUsageMetrics(rawResponse, generatedImages = 0)
        val jsonText = extractFirstTextPart(rawResponse)
        val dto = gson.fromJson(jsonText, MallOutfitResponseDto::class.java)
        val validMallIds = mallCatalog.map { it.id }.toSet()
        val validWardrobeIds = wardrobe.map { it.id }.toSet()

        val cleaned = dto?.outfits.orEmpty().mapNotNull { rec ->
            val mIds = rec.mallItemIds.filter { it in validMallIds }.distinct()
            val wIds = rec.wardrobeItemIds.filter { it in validWardrobeIds }.distinct()
            if (mIds.isEmpty()) null
            else rec.copy(mallItemIds = mIds, wardrobeItemIds = wIds)
        }
        require(cleaned.isNotEmpty()) { "無法產生商城推薦組合，請調整品牌篩選後重試" }
        GeminiTrackedResult(data = cleaned, usage = usage)
    }

    /**
     * Stage 2 Photorealistic Virtual Try-On:
     * Sends the user's base full-body photo FIRST, followed by garment/accessory reference images,
     * optional Mall text specifications, and explicit tuck-in / outerwear open-closed directives.
     */
    suspend fun generateVirtualTryOn(
        apiKey: String,
        modelId: String,
        personBitmap: Bitmap,
        clothingItems: List<Pair<ClothingItem, Bitmap>>,
        topStylingMode: TopStylingMode = TopStylingMode.AUTO,
        outerStylingMode: OuterStylingMode = OuterStylingMode.AUTO,
        mallSpecs: List<MallBrandItem> = emptyList()
    ): GeminiTrackedResult<Bitmap> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw GeminiApiException(null, null, "請先於右上角設定頁輸入 Gemini API Key")
        }
        require(clothingItems.isNotEmpty() || mallSpecs.isNotEmpty()) {
            "請至少選擇一件衣櫃單品或商城推薦單品進行實穿合成"
        }

        val personBase64 = bitmapToBase64(
            resizeIfNeeded(personBitmap, maxEdge = 1280),
            Bitmap.CompressFormat.JPEG,
            92
        )

        val parts = JsonArray().apply {
            add(JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", "image/jpeg")
                    addProperty("data", personBase64)
                })
            })

            clothingItems.forEach { (_, garmentBitmap) ->
                val resized = resizeIfNeeded(garmentBitmap, maxEdge = 1024)
                val usePng = resized.hasAlpha()
                val garmentBase64 = bitmapToBase64(
                    resized,
                    if (usePng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    if (usePng) 100 else 90
                )
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", if (usePng) "image/png" else "image/jpeg")
                        addProperty("data", garmentBase64)
                    })
                })
            }

            add(JsonObject().apply {
                addProperty(
                    "text",
                    buildEnhancedTryOnPrompt(
                        clothingItems = clothingItems.map { it.first },
                        topStylingMode = topStylingMode,
                        outerStylingMode = outerStylingMode,
                        mallSpecs = mallSpecs
                    )
                )
            })
        }

        val initialPayload = buildTryOnPayload(parts, listOf("IMAGE"))
        val responseJson = try {
            executeGenerateContent(apiKey = apiKey, modelId = modelId, payload = initialPayload)
        } catch (ex: GeminiApiException) {
            val msg = ex.rawDetail.orEmpty()
            if (ex.statusCode == 400 && (
                    msg.contains("response_modalities", ignoreCase = true) ||
                        msg.contains("responseModalities", ignoreCase = true)
                    )
            ) {
                executeGenerateContent(
                    apiKey = apiKey,
                    modelId = modelId,
                    payload = buildTryOnPayload(parts, listOf("TEXT", "IMAGE"))
                )
            } else {
                throw ex
            }
        }

        val generatedBitmap = extractGeneratedBitmap(responseJson)
        val usage = parseUsageMetrics(responseJson, generatedImages = 1)
        GeminiTrackedResult(data = generatedBitmap, usage = usage)
    }

    private fun buildEnhancedTryOnPrompt(
        clothingItems: List<ClothingItem>,
        topStylingMode: TopStylingMode,
        outerStylingMode: OuterStylingMode,
        mallSpecs: List<MallBrandItem>
    ): String {
        val sb = StringBuilder()
        sb.append(VIRTUAL_TRY_ON_PROMPT)

        if (clothingItems.isNotEmpty()) {
            sb.append("\n\nREFERENCE GARMENTS & ACCESSORIES PLACEMENT RULES:")
            clothingItems.forEachIndexed { index, item ->
                val refIndex = index + 2
                val placementHint = categoryPlacementDirective(item.category)
                sb.append("\n- Reference Image #$refIndex (${item.category}: ${item.color} ${item.subCategory}, fit=${item.fit}): $placementHint")
            }
        }

        if (mallSpecs.isNotEmpty()) {
            sb.append("\n\nADDITIONAL CATALOG GARMENTS TO RENDER ON THE PERSON:")
            mallSpecs.forEach { mall ->
                val placementHint = categoryPlacementDirective(mall.category)
                sb.append("\n- ${mall.toVirtualTryOnSpecification()} -> $placementHint")
            }
        }

        val hasTop = clothingItems.any { it.category == ClothingItem.CAT_TOP } ||
            mallSpecs.any { it.category == ClothingItem.CAT_TOP }
        val hasOuter = clothingItems.any { it.category == ClothingItem.CAT_OUTER } ||
            mallSpecs.any { it.category == ClothingItem.CAT_OUTER }

        if (hasTop && topStylingMode != TopStylingMode.AUTO) {
            sb.append("\n\nEXPLICIT TOP STYLING CONSTRAINT: ${topStylingMode.promptDirective}.")
        }
        if (hasOuter && outerStylingMode != OuterStylingMode.AUTO) {
            sb.append("\n\nEXPLICIT OUTERWEAR STYLING CONSTRAINT: ${outerStylingMode.promptDirective}.")
        }

        return sb.toString()
    }

    private fun categoryPlacementDirective(category: String): String = when (category) {
        ClothingItem.CAT_TOP -> "Wear on upper torso with realistic shoulder seams and natural sleeve drape."
        ClothingItem.CAT_BOTTOM -> "Wear on lower body from waist down with accurate leg silhouette and hem length."
        ClothingItem.CAT_OUTER -> "Layer naturally as outerwear over the inner top."
        ClothingItem.CAT_ONE_PIECE -> "Wear as a full one-piece dress/jumpsuit fitting the torso and legs naturally."
        ClothingItem.CAT_SHOES -> "Wear on both feet matching the floor perspective, foot angle, and ground contact shadow."
        ClothingItem.CAT_HAT_SCARF -> "Wear hats naturally on the head preserving face and hairstyle, or drape scarves around the neck."
        ClothingItem.CAT_EYEWEAR -> "Wear eyeglasses/sunglasses properly aligned on the nose bridge and ears over the face without distorting facial identity."
        ClothingItem.CAT_HAIR_ACCESSORY -> "Clip or wear the hair accessory naturally in the hair."
        ClothingItem.CAT_BAG -> "Carry naturally on the shoulder, crossbody, or in hand matching arm posture."
        ClothingItem.CAT_ACCESSORY -> "Wear watches/jewelry/belts/socks on their anatomically correct body position."
        else -> "Wear naturally on the appropriate body part."
    }

    private fun parseUsageMetrics(responseJson: JsonObject, generatedImages: Int): UsageMetrics {
        val usageObj = responseJson.getAsJsonObject("usageMetadata")
        val promptTokens = usageObj?.get("promptTokenCount")?.asInt ?: 0
        val candidateTokens = usageObj?.get("candidatesTokenCount")?.asInt ?: 0
        val totalTokens = usageObj?.get("totalTokenCount")?.asInt ?: (promptTokens + candidateTokens)
        return UsageMetrics(
            promptTokens = promptTokens,
            candidateTokens = candidateTokens,
            totalTokens = totalTokens,
            generatedImages = generatedImages
        )
    }

    private fun buildTryOnPayload(parts: JsonArray, modalities: List<String>): JsonObject {
        return JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply { add("parts", parts) })
            })
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply {
                    modalities.forEach { add(it) }
                })
            })
        }
    }

    private fun executeGenerateContent(
        apiKey: String,
        modelId: String,
        payload: JsonObject
    ): JsonObject {
        val url = "$BASE_URL/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val extracted = extractRawErrorMessage(bodyString)
                    val friendly = formatGeminiHttpError(response.code, extracted, modelId)
                    throw GeminiApiException(response.code, extracted, friendly)
                }
                if (bodyString.isBlank()) {
                    throw GeminiApiException(response.code, null, "Gemini API 回傳空白內容")
                }
                return JsonParser.parseString(bodyString).asJsonObject
            }
        } catch (ex: GeminiApiException) {
            throw ex
        } catch (ex: UnknownHostException) {
            throw GeminiApiException(null, ex.message, "無法連線至 Google Gemini 伺服器，請檢查手機網路狀態")
        } catch (ex: SocketTimeoutException) {
            throw GeminiApiException(null, ex.message, "Gemini API 請求逾時，請稍後再試")
        } catch (ex: IOException) {
            throw GeminiApiException(null, ex.message, "網路傳輸異常：${ex.message ?: "請重試"}")
        }
    }

    private fun extractRawErrorMessage(bodyString: String): String? = runCatching {
        val obj = JsonParser.parseString(bodyString).asJsonObject
        obj.getAsJsonObject("error")?.get("message")?.asString
    }.getOrNull()

    private fun formatGeminiHttpError(
        statusCode: Int,
        extracted: String?,
        modelId: String
    ): String {
        val detail = extracted?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
        return when (statusCode) {
            400 -> "HTTP 400：API Key 格式錯誤或請求參數無效$detail"
            401 -> "HTTP 401：API Key 驗證失敗，請檢查金鑰是否正確$detail"
            403 -> "HTTP 403：API Key 權限不足或所在地區／專案受限$detail"
            404 -> "HTTP 404：找不到模型「$modelId」或該模型無權限／已停用$detail"
            429 -> "HTTP 429：API 額度已耗盡或請求過於頻繁，請稍後再試$detail"
            in 500..599 -> "HTTP $statusCode：Gemini 伺服器暫時異常，請稍後重試$detail"
            else -> "HTTP $statusCode：Gemini API 呼叫失敗$detail"
        }
    }

    private fun extractFirstTextPart(responseJson: JsonObject): String {
        val candidates = responseJson.getAsJsonArray("candidates")
            ?: throw GeminiApiException(null, null, "Gemini 未回傳任何候選結果")
        if (candidates.size() == 0) {
            throw GeminiApiException(null, null, "Gemini 候選結果為空")
        }
        val parts = candidates[0].asJsonObject
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?: throw GeminiApiException(null, null, "Gemini 回應缺少 content.parts")

        for (elem in parts) {
            val part = elem.asJsonObject
            if (part.has("text")) {
                return part.get("text").asString
            }
        }
        throw GeminiApiException(null, null, "Gemini 回應中找不到文字結果")
    }

    private fun extractGeneratedBitmap(responseJson: JsonObject): Bitmap {
        val candidates = responseJson.getAsJsonArray("candidates")
            ?: throw GeminiApiException(null, null, "影像生成失敗：未收到候選結果")
        if (candidates.size() == 0) {
            throw GeminiApiException(null, null, "影像生成失敗：候選結果為空")
        }

        val candidate = candidates[0].asJsonObject
        val finishReason = candidate.get("finishReason")?.asString
        val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
            ?: throw GeminiApiException(null, finishReason, "影像生成失敗（狀態：$finishReason）")

        var fallbackText: String? = null
        for (elem in parts) {
            val part = elem.asJsonObject
            val inlineObj = part.getAsJsonObject("inlineData")
                ?: part.getAsJsonObject("inline_data")
            if (inlineObj != null && inlineObj.has("data")) {
                val base64Data = inlineObj.get("data").asString
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded != null) return decoded
            }
            if (part.has("text")) {
                fallbackText = part.get("text").asString
            }
        }

        throw GeminiApiException(
            null,
            fallbackText ?: finishReason,
            fallbackText?.takeIf { it.isNotBlank() }
                ?: "模型未輸出影像檔案（狀態：${finishReason ?: "UNKNOWN"}），請確認模型與素材是否符合安全政策"
        )
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val w = max(1, (bitmap.width * ratio).roundToInt())
        val h = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun bitmapToBase64(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
