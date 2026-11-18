package com.tryonwardrobe.app.service.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

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

    private data class OutfitResponseDto(
        val outfits: List<OutfitRecommendation>? = null
    )

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /**
     * Stage 1 Ingestion: Uses gemini-2.5-flash with structured JSON output
     * to tag a background-removed garment cutout image in a single lightweight call.
     */
    suspend fun autoTagGarment(apiKey: String, garmentBitmap: Bitmap): GarmentTagResult =
        withContext(Dispatchers.IO) {
            require(apiKey.isNotBlank()) { "請先於右上角設定頁輸入 Gemini API Key" }

            val thumbBase64 = bitmapToBase64(
                resizeIfNeeded(garmentBitmap, maxEdge = 768),
                Bitmap.CompressFormat.PNG,
                100
            )

            val parts = JsonArray().apply {
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", "image/png")
                        addProperty("data", thumbBase64)
                    })
                })
                add(JsonObject().apply {
                    addProperty(
                        "text",
                        "請辨識這張已去背的服飾或鞋履單品圖片，以繁體中文輸出精準結構化衣櫃標籤。"
                    )
                })
            }

            val schema = JsonObject().apply {
                addProperty("type", "OBJECT")
                add("properties", JsonObject().apply {
                    add("category", JsonObject().apply {
                        addProperty("type", "STRING")
                        add("enum", JsonArray().apply {
                            add("上衣"); add("下著"); add("外套"); add("連身"); add("鞋履")
                        })
                    })
                    add("subCategory", JsonObject().apply {
                        addProperty("type", "STRING")
                        addProperty("description", "具體單品名稱，例如：重磅白T、水洗直筒牛仔褲、飛行夾克、樂福鞋")
                    })
                    add("color", JsonObject().apply {
                        addProperty("type", "STRING")
                        addProperty("description", "主色系中文描述，例如：奶油白、炭黑、深海軍藍、軍綠")
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
                        addProperty("description", "2至3個風格標籤，例如：極簡、美式復古、Smart Casual、街頭、運動休閒")
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
                modelId = SettingsDataStore.MODEL_LOGIC_FLASH,
                payload = payload
            )
            val jsonText = extractFirstTextPart(rawResponse)
            val parsed = runCatching { gson.fromJson(jsonText, GarmentTagResult::class.java) }.getOrNull()

            GarmentTagResult(
                category = parsed?.category?.takeIf { it in listOf("上衣", "下著", "外套", "連身", "鞋履") } ?: "上衣",
                subCategory = parsed?.subCategory?.takeIf { it.isNotBlank() } ?: "基本款單品",
                color = parsed?.color?.takeIf { it.isNotBlank() } ?: "中性色",
                fit = parsed?.fit?.takeIf { it in listOf("緊身", "合身", "寬鬆") } ?: "合身",
                styleTags = parsed?.styleTags?.filter { it.isNotBlank() }?.ifEmpty { listOf("簡約") } ?: listOf("簡約"),
                season = parsed?.season?.filter { it in listOf("春", "夏", "秋", "冬") }
                    ?.ifEmpty { listOf("春", "秋") } ?: listOf("春", "秋")
            )
        }

    /**
     * Stage 1 AI Stylist: Sends ONLY lightweight metadata JSON (zero images)
     * to gemini-2.5-flash to pick 2-3 coherent wardrobe combinations with minimal token usage.
     */
    suspend fun recommendOutfits(
        apiKey: String,
        wardrobe: List<ClothingItem>,
        stylePreset: String,
        customRequest: String,
        anchorItemId: Long? = null
    ): List<OutfitRecommendation> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "請先於右上角設定頁輸入 Gemini API Key" }
        require(wardrobe.isNotEmpty()) { "數位衣櫃目前沒有單品，請先拍攝或匯入衣物" }

        val compactCatalog = JsonArray().apply {
            wardrobe.forEach { item ->
                add(JsonObject().apply {
                    addProperty("id", item.id)
                    addProperty("category", item.category)
                    addProperty("subCategory", item.subCategory)
                    addProperty("color", item.color)
                    addProperty("fit", item.fit)
                    add("styleTags", gson.toJsonTree(item.styleTags))
                    add("season", gson.toJsonTree(item.season))
                })
            }
        }

        val promptBuilder = StringBuilder().apply {
            appendLine("你是專業造型師。請僅從以下使用者的衣櫃清單中挑選 2 至 3 套最適合且可真實穿在身上的完整穿搭組合。")
            appendLine("【目標風格／情境】：$stylePreset")
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
            appendLine("1. 每套必須挑選衣櫃中實際存在的 itemIds（例如：上衣+下著，或連身+外套，可視現有庫存搭配外套或鞋履）。")
            appendLine("2. itemIds 不可包含重複 ID，且不可虛構清單外的 ID。")
            appendLine("3. rationale 請以簡潔有感的繁體中文說明為何這樣搭配（限 45 字內）。")
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
                addProperty("temperature", 0.5)
                addProperty("responseMimeType", "application/json")
                add("responseSchema", schema)
            })
        }

        val rawResponse = executeGenerateContent(
            apiKey = apiKey,
            modelId = SettingsDataStore.MODEL_LOGIC_FLASH,
            payload = payload
        )
        val jsonText = extractFirstTextPart(rawResponse)
        val dto = gson.fromJson(jsonText, OutfitResponseDto::class.java)
        val validIds = wardrobe.map { it.id }.toSet()

        val cleaned = dto?.outfits.orEmpty().mapNotNull { rec ->
            val filteredIds = rec.itemIds.filter { it in validIds }.distinct()
            if (filteredIds.isEmpty()) null
            else rec.copy(itemIds = filteredIds)
        }
        require(cleaned.isNotEmpty()) { "無法從目前衣櫃單品組成符合條件的推薦，請試著多匯入幾件上下著或調整條件" }
        cleaned
    }

    /**
     * Stage 2 Photorealistic Virtual Try-On: Sends the user's base full-body photo FIRST,
     * followed by all background-removed garment cutout PNGs and the strict realism prompt.
     * Supports gemini-2.5-flash-image and gemini-3-pro-image-preview with responseModalities = ["IMAGE"].
     */
    suspend fun generateVirtualTryOn(
        apiKey: String,
        modelId: String,
        personBitmap: Bitmap,
        clothingItems: List<Pair<ClothingItem, Bitmap>>
    ): Bitmap = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "請先於右上角設定頁輸入 Gemini API Key" }
        require(clothingItems.isNotEmpty()) { "請至少選擇一件衣物進行實穿合成" }

        val personBase64 = bitmapToBase64(
            resizeIfNeeded(personBitmap, maxEdge = 1280),
            Bitmap.CompressFormat.JPEG,
            92
        )

        val parts = JsonArray().apply {
            // 1. First image MUST be the person's full-body base photo
            add(JsonObject().apply {
                add("inlineData", JsonObject().apply {
                    addProperty("mimeType", "image/jpeg")
                    addProperty("data", personBase64)
                })
            })

            // 2. Subsequent reference images: transparent background garment cutouts
            clothingItems.forEach { (_, garmentBitmap) ->
                val garmentBase64 = bitmapToBase64(
                    resizeIfNeeded(garmentBitmap, maxEdge = 1024),
                    Bitmap.CompressFormat.PNG,
                    100
                )
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", "image/png")
                        addProperty("data", garmentBase64)
                    })
                })
            }

            // 3. Verbatim photorealism & identity-preservation rules prompt
            add(JsonObject().apply {
                addProperty("text", VIRTUAL_TRY_ON_PROMPT)
            })
        }

        val initialPayload = buildTryOnPayload(parts, listOf("IMAGE"))
        val responseJson = try {
            executeGenerateContent(apiKey = apiKey, modelId = modelId, payload = initialPayload)
        } catch (ex: IllegalStateException) {
            val msg = ex.message.orEmpty()
            if (msg.contains("response_modalities", ignoreCase = true) ||
                msg.contains("responseModalities", ignoreCase = true)
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

        extractGeneratedBitmap(responseJson)
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

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errorMessage = parseGeminiErrorMessage(bodyString, response.code)
                throw IllegalStateException(errorMessage)
            }
            if (bodyString.isBlank()) {
                throw IllegalStateException("Gemini API 回傳空白內容")
            }
            return JsonParser.parseString(bodyString).asJsonObject
        }
    }

    private fun parseGeminiErrorMessage(bodyString: String, statusCode: Int): String {
        val extracted = runCatching {
            val obj = JsonParser.parseString(bodyString).asJsonObject
            obj.getAsJsonObject("error")?.get("message")?.asString
        }.getOrNull()
        return when {
            statusCode == 401 || statusCode == 403 ->
                "API Key 驗證失敗或無權限 ($statusCode)：${extracted ?: "請確認 Gemini API Key 是否正確"}"
            statusCode == 429 ->
                "Gemini API 請求過於頻繁或額度已滿 (429)：${extracted ?: "請稍後再試"}"
            !extracted.isNullOrBlank() ->
                "Gemini API 錯誤 ($statusCode)：$extracted"
            else ->
                "Gemini API 呼叫失敗 (HTTP $statusCode)"
        }
    }

    private fun extractFirstTextPart(responseJson: JsonObject): String {
        val candidates = responseJson.getAsJsonArray("candidates")
            ?: throw IllegalStateException("Gemini 未回傳任何候選結果")
        if (candidates.size() == 0) {
            throw IllegalStateException("Gemini 候選結果為空")
        }
        val parts = candidates[0].asJsonObject
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?: throw IllegalStateException("Gemini 回應缺少 content.parts")

        for (elem in parts) {
            val part = elem.asJsonObject
            if (part.has("text")) {
                return part.get("text").asString
            }
        }
        throw IllegalStateException("Gemini 回應中找不到文字結果")
    }

    private fun extractGeneratedBitmap(responseJson: JsonObject): Bitmap {
        val candidates = responseJson.getAsJsonArray("candidates")
            ?: throw IllegalStateException("影像生成失敗：未收到候選結果")
        if (candidates.size() == 0) {
            throw IllegalStateException("影像生成失敗：候選結果為空")
        }

        val candidate = candidates[0].asJsonObject
        val finishReason = candidate.get("finishReason")?.asString
        val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
            ?: throw IllegalStateException("影像生成失敗（狀態：$finishReason）")

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

        throw IllegalStateException(
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
