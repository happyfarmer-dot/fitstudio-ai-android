package com.tryonwardrobe.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tryonwardrobe.app.WardrobeApplication
import com.tryonwardrobe.app.data.catalog.MallBrandItem
import com.tryonwardrobe.app.data.catalog.MallCatalog
import com.tryonwardrobe.app.data.local.ApiUsageRecord
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.SafeImageStorage
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.data.preferences.PersonalStyleProfile
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.data.repository.OuterStylingMode
import com.tryonwardrobe.app.data.repository.TopStylingMode
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

enum class AppTab(val label: String) {
    WARDROBE("數位衣櫃"),
    TRY_ON("寫實試穿"),
    STYLIST("AI 穿搭"),
    MALL("商城穿搭")
}

enum class WardrobeSortOption(val label: String) {
    NEWEST("預設最新"),
    MOST_WORN("最常穿排行"),
    DORMANT_90_DAYS("超過 90 天未穿")
}

data class ApiUsageSummary(
    val todayCalls: Int = 0,
    val todayTokens: Int = 0,
    val todayImages: Int = 0,
    val monthCalls: Int = 0,
    val monthTokens: Int = 0,
    val monthImages: Int = 0,
    val estimatedMonthUsd: Double = 0.0,
    val estimatedMonthTwd: Double = 0.0
)

data class WardrobeUiState(
    val selectedTab: AppTab = AppTab.WARDROBE,
    val selectedCategoryFilter: String = "全部",
    val selectedSortOption: WardrobeSortOption = WardrobeSortOption.NEWEST,
    val clothingItems: List<ClothingItem> = emptyList(),
    val personPhotos: List<PersonPhoto> = emptyList(),
    val activePersonPhoto: PersonPhoto? = null,
    val personalProfile: PersonalStyleProfile = PersonalStyleProfile(),
    val isAnalyzingPersonalProfile: Boolean = false,
    val recentCaches: List<TryOnCache> = emptyList(),
    val apiUsageRecords: List<ApiUsageRecord> = emptyList(),
    val apiUsageSummary: ApiUsageSummary = ApiUsageSummary(),
    val selectedClothingIds: Set<Long> = emptySet(),
    val selectedMallItemsForTryOn: List<MallBrandItem> = emptyList(),
    val topStylingMode: TopStylingMode = TopStylingMode.AUTO,
    val outerStylingMode: OuterStylingMode = OuterStylingMode.AUTO,
    val geminiApiKey: String = "",
    val taggingModel: String = SettingsDataStore.DEFAULT_TAGGING_MODEL,
    val tryOnModel: String = SettingsDataStore.DEFAULT_TRY_ON_MODEL,
    val isImportingGarments: Boolean = false,
    val importStatusText: String = "",
    val importProgressCurrent: Int = 0,
    val importProgressTotal: Int = 0,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: GeminiWardrobeService.ConnectionTestResult? = null,
    val isGeneratingTryOn: Boolean = false,
    val tryOnStatusText: String = "",
    val beforePhotoPath: String? = null,
    val afterResultPath: String? = null,
    val isResultFromCache: Boolean = false,
    val selectedStylePreset: String = "日系簡約",
    val customStylistPrompt: String = "",
    val anchorItemId: Long? = null,
    val isGeneratingRecommendations: Boolean = false,
    val recommendations: List<GeminiWardrobeService.OutfitRecommendation> = emptyList(),
    val randomBlindBoxRecommendation: GeminiWardrobeService.OutfitRecommendation? = null,
    val isDrawingBlindBox: Boolean = false,
    val selectedMallBrand: String = MallCatalog.BRAND_ALL,
    val mallMixWithWardrobe: Boolean = true,
    val isGeneratingMallRecommendations: Boolean = false,
    val mallRecommendations: List<GeminiWardrobeService.MallOutfitRecommendation> = emptyList(),
    val isRunningBackupOrRestore: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val showBaseCameraDialog: Boolean = false,
    val snackbarMessage: String? = null
)

private data class LocalActionState(
    val selectedTab: AppTab = AppTab.WARDROBE,
    val selectedCategoryFilter: String = "全部",
    val selectedSortOption: WardrobeSortOption = WardrobeSortOption.NEWEST,
    val selectedClothingIds: Set<Long> = emptySet(),
    val selectedMallItemsForTryOn: List<MallBrandItem> = emptyList(),
    val topStylingMode: TopStylingMode = TopStylingMode.AUTO,
    val outerStylingMode: OuterStylingMode = OuterStylingMode.AUTO,
    val isAnalyzingPersonalProfile: Boolean = false,
    val isImportingGarments: Boolean = false,
    val importStatusText: String = "",
    val importProgressCurrent: Int = 0,
    val importProgressTotal: Int = 0,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: GeminiWardrobeService.ConnectionTestResult? = null,
    val isGeneratingTryOn: Boolean = false,
    val tryOnStatusText: String = "",
    val beforePhotoPath: String? = null,
    val afterResultPath: String? = null,
    val isResultFromCache: Boolean = false,
    val selectedStylePreset: String = "日系簡約",
    val customStylistPrompt: String = "",
    val anchorItemId: Long? = null,
    val isGeneratingRecommendations: Boolean = false,
    val recommendations: List<GeminiWardrobeService.OutfitRecommendation> = emptyList(),
    val randomBlindBoxRecommendation: GeminiWardrobeService.OutfitRecommendation? = null,
    val isDrawingBlindBox: Boolean = false,
    val selectedMallBrand: String = MallCatalog.BRAND_ALL,
    val mallMixWithWardrobe: Boolean = true,
    val isGeneratingMallRecommendations: Boolean = false,
    val mallRecommendations: List<GeminiWardrobeService.MallOutfitRecommendation> = emptyList(),
    val isRunningBackupOrRestore: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val showBaseCameraDialog: Boolean = false,
    val snackbarMessage: String? = null
)

private data class PersistentBundle(
    val clothing: List<ClothingItem>,
    val persons: List<PersonPhoto>,
    val activePersonId: Long?,
    val caches: List<TryOnCache>,
    val apiKey: String,
    val taggingModel: String = SettingsDataStore.DEFAULT_TAGGING_MODEL,
    val tryOnModel: String = SettingsDataStore.DEFAULT_TRY_ON_MODEL,
    val personalProfile: PersonalStyleProfile = PersonalStyleProfile(),
    val usageRecords: List<ApiUsageRecord> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WardrobeApplication
    private val repository = app.wardrobeRepository
    private val imageStorage = app.safeImageStorage
    private val backgroundRemover = app.backgroundRemover
    private val geminiService = app.geminiWardrobeService
    private val backupManager = app.wardrobeBackupManager

    // Semaphore(permits = 3) for concurrent local downsampling + offline ML Kit segmentation
    private val localProcessingSemaphore = Semaphore(permits = 3)

    private val localState = MutableStateFlow(LocalActionState())

    private val persistentFlow = combine(
        repository.allClothingFlow,
        repository.allPersonPhotosFlow,
        repository.settings.activePersonPhotoIdFlow,
        repository.recentTryOnCachesFlow,
        repository.settings.geminiApiKeyFlow
    ) { clothing, persons, activeId, caches, apiKey ->
        PersistentBundle(
            clothing = clothing,
            persons = persons,
            activePersonId = activeId,
            caches = caches,
            apiKey = apiKey
        )
    }.combine(repository.settings.taggingModelFlow) { bundle, taggingModel ->
        bundle.copy(taggingModel = taggingModel)
    }.combine(repository.settings.tryOnModelFlow) { bundle, tryOnModel ->
        bundle.copy(tryOnModel = tryOnModel)
    }.combine(repository.settings.personalStyleProfileFlow) { bundle, profile ->
        bundle.copy(personalProfile = profile)
    }.combine(repository.allApiUsageFlow) { bundle, usageRecords ->
        bundle.copy(usageRecords = usageRecords)
    }

    val uiState: StateFlow<WardrobeUiState> = combine(persistentFlow, localState) { db, local ->
        val activePerson = db.persons.find { it.id == db.activePersonId } ?: db.persons.firstOrNull()
        val validIds = db.clothing.map { it.id }.toSet()
        val cleanedSelected = local.selectedClothingIds.intersect(validIds)

        WardrobeUiState(
            selectedTab = local.selectedTab,
            selectedCategoryFilter = local.selectedCategoryFilter,
            selectedSortOption = local.selectedSortOption,
            clothingItems = db.clothing,
            personPhotos = db.persons,
            activePersonPhoto = activePerson,
            personalProfile = db.personalProfile,
            isAnalyzingPersonalProfile = local.isAnalyzingPersonalProfile,
            recentCaches = db.caches,
            apiUsageRecords = db.usageRecords,
            apiUsageSummary = calculateUsageSummary(db.usageRecords),
            selectedClothingIds = cleanedSelected,
            selectedMallItemsForTryOn = local.selectedMallItemsForTryOn,
            topStylingMode = local.topStylingMode,
            outerStylingMode = local.outerStylingMode,
            geminiApiKey = db.apiKey,
            taggingModel = db.taggingModel,
            tryOnModel = db.tryOnModel,
            isImportingGarments = local.isImportingGarments,
            importStatusText = local.importStatusText,
            importProgressCurrent = local.importProgressCurrent,
            importProgressTotal = local.importProgressTotal,
            isTestingConnection = local.isTestingConnection,
            connectionTestResult = local.connectionTestResult,
            isGeneratingTryOn = local.isGeneratingTryOn,
            tryOnStatusText = local.tryOnStatusText,
            beforePhotoPath = local.beforePhotoPath ?: activePerson?.imagePath,
            afterResultPath = local.afterResultPath,
            isResultFromCache = local.isResultFromCache,
            selectedStylePreset = local.selectedStylePreset,
            customStylistPrompt = local.customStylistPrompt,
            anchorItemId = local.anchorItemId?.takeIf { it in validIds },
            isGeneratingRecommendations = local.isGeneratingRecommendations,
            recommendations = local.recommendations,
            randomBlindBoxRecommendation = local.randomBlindBoxRecommendation,
            isDrawingBlindBox = local.isDrawingBlindBox,
            selectedMallBrand = local.selectedMallBrand,
            mallMixWithWardrobe = local.mallMixWithWardrobe,
            isGeneratingMallRecommendations = local.isGeneratingMallRecommendations,
            mallRecommendations = local.mallRecommendations,
            isRunningBackupOrRestore = local.isRunningBackupOrRestore,
            showApiKeyDialog = local.showApiKeyDialog,
            showBaseCameraDialog = local.showBaseCameraDialog,
            snackbarMessage = local.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WardrobeUiState()
    )

    private fun calculateUsageSummary(records: List<ApiUsageRecord>): ApiUsageSummary {
        if (records.isEmpty()) return ApiUsageSummary()

        val nowCal = Calendar.getInstance()
        val todayStartCal = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStartCal = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val todayStartMs = todayStartCal.timeInMillis
        val monthStartMs = monthStartCal.timeInMillis

        var todayCalls = 0
        var todayTokens = 0
        var todayImages = 0
        var monthCalls = 0
        var monthTokens = 0
        var monthImages = 0
        var monthUsd = 0.0

        records.forEach { r ->
            if (r.timestamp >= todayStartMs) {
                todayCalls++
                todayTokens += r.totalTokens
                todayImages += r.generatedImages
            }
            if (r.timestamp >= monthStartMs) {
                monthCalls++
                monthTokens += r.totalTokens
                monthImages += r.generatedImages
                monthUsd += estimateRecordCostUsd(r)
            }
        }

        return ApiUsageSummary(
            todayCalls = todayCalls,
            todayTokens = todayTokens,
            todayImages = todayImages,
            monthCalls = monthCalls,
            monthTokens = monthTokens,
            monthImages = monthImages,
            estimatedMonthUsd = monthUsd,
            estimatedMonthTwd = monthUsd * 32.5
        )
    }

    private fun estimateRecordCostUsd(record: ApiUsageRecord): Double {
        val tokenRatePerMillion = when {
            record.modelId.contains("lite", ignoreCase = true) -> 0.10
            record.modelId.contains("pro", ignoreCase = true) -> 1.25
            else -> 0.30
        }
        val perImageUsd = when {
            record.modelId.contains("pro-image", ignoreCase = true) -> 0.04
            record.modelId.contains("lite-image", ignoreCase = true) -> 0.015
            else -> 0.025
        }
        val tokenCost = (record.totalTokens.toDouble() / 1_000_000.0) * tokenRatePerMillion
        val imageCost = record.generatedImages.toDouble() * perImageUsd
        return tokenCost + imageCost
    }

    fun selectTab(tab: AppTab) {
        localState.update { it.copy(selectedTab = tab) }
    }

    fun selectCategoryFilter(category: String) {
        localState.update { it.copy(selectedCategoryFilter = category) }
    }

    fun selectSortOption(option: WardrobeSortOption) {
        localState.update { it.copy(selectedSortOption = option) }
    }

    fun setTopStylingMode(mode: TopStylingMode) {
        localState.update { it.copy(topStylingMode = mode) }
    }

    fun setOuterStylingMode(mode: OuterStylingMode) {
        localState.update { it.copy(outerStylingMode = mode) }
    }

    fun toggleClothingSelection(itemId: Long) {
        localState.update { state ->
            val updated = state.selectedClothingIds.toMutableSet()
            if (!updated.add(itemId)) {
                updated.remove(itemId)
            }
            state.copy(selectedClothingIds = updated)
        }
    }

    fun clearSelectedClothing() {
        localState.update {
            it.copy(
                selectedClothingIds = emptySet(),
                selectedMallItemsForTryOn = emptyList()
            )
        }
    }

    fun removeSelectedMallItem(mallId: String) {
        localState.update { state ->
            state.copy(selectedMallItemsForTryOn = state.selectedMallItemsForTryOn.filterNot { it.id == mallId })
        }
    }

    fun incrementSingleItemWearCount(item: ClothingItem) {
        viewModelScope.launch {
            repository.incrementWearCount(listOf(item.id))
            localState.update {
                it.copy(snackbarMessage = "「${item.subCategory}」今日穿著次數 +1（累計 ${item.wearCount + 1} 次）")
            }
        }
    }

    fun confirmWearCurrentTryOnOutfit() {
        val ids = uiState.value.selectedClothingIds.toList()
        if (ids.isEmpty()) {
            localState.update { it.copy(snackbarMessage = "目前試穿組合中無個人衣櫃單品可記錄穿著次數") }
            return
        }
        viewModelScope.launch {
            repository.incrementWearCount(ids)
            localState.update {
                it.copy(snackbarMessage = "已採用今日穿搭！組合內 ${ids.size} 件衣櫃單品穿著次數已 +1")
            }
        }
    }

    fun setApiKeyDialogVisible(visible: Boolean) {
        localState.update {
            it.copy(
                showApiKeyDialog = visible,
                connectionTestResult = if (!visible) null else it.connectionTestResult
            )
        }
    }

    fun setBaseCameraDialogVisible(visible: Boolean) {
        localState.update { it.copy(showBaseCameraDialog = visible) }
    }

    fun clearSnackbar() {
        localState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        localState.update { it.copy(snackbarMessage = msg) }
    }

    fun clearConnectionTestResult() {
        localState.update { it.copy(connectionTestResult = null) }
    }

    fun resetApiUsageStats() {
        viewModelScope.launch {
            repository.clearAllApiUsage()
            localState.update { it.copy(snackbarMessage = "已重設 API Token 與費用統計紀錄") }
        }
    }

    fun testGeminiConnection(
        apiKey: String,
        taggingModelId: String,
        tryOnModelId: String
    ) {
        viewModelScope.launch {
            localState.update {
                it.copy(isTestingConnection = true, connectionTestResult = null)
            }
            val result = geminiService.testConnection(
                apiKey = apiKey,
                taggingModelId = taggingModelId,
                tryOnModelId = tryOnModelId
            )
            if (result.success) {
                repository.recordApiUsage(
                    ApiUsageRecord(
                        modelId = taggingModelId,
                        taskType = ApiUsageRecord.TASK_CONNECTION_TEST,
                        promptTokens = 4,
                        candidateTokens = 2,
                        totalTokens = 6,
                        generatedImages = 0
                    )
                )
            }
            localState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestResult = result
                )
            }
        }
    }

    fun saveSettings(apiKey: String, taggingModelId: String, tryOnModelId: String) {
        viewModelScope.launch {
            repository.settings.saveGeminiApiKey(apiKey)
            repository.settings.setTaggingModel(taggingModelId)
            repository.settings.setTryOnModel(tryOnModelId)
            localState.update {
                it.copy(
                    showApiKeyDialog = false,
                    connectionTestResult = null,
                    snackbarMessage = "設定已儲存（打標：$taggingModelId ／ 換裝：$tryOnModelId）"
                )
            }
        }
    }

    // --- Personal Color & Body Shape Profile ---

    fun analyzeActivePersonColorAndBody() {
        val activePerson = uiState.value.activePersonPhoto
        if (activePerson == null) {
            localState.update {
                it.copy(
                    showBaseCameraDialog = true,
                    snackbarMessage = "請先拍攝或選取一張全身基準照以進行個人色彩與身形分析"
                )
            }
            return
        }

        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 以執行 AI 個人色彩與身形分析"
                    )
                }
                return@launch
            }

            localState.update { it.copy(isAnalyzingPersonalProfile = true) }
            runCatching {
                val bmp = imageStorage.decodeStoredBitmap(activePerson.imagePath, maxEdge = 1024)
                    ?: error("無法讀取全身基準照檔案")
                geminiService.analyzePersonalColorAndBody(
                    apiKey = apiKey,
                    modelId = taggingModel,
                    personBitmap = bmp
                )
            }.onSuccess { tracked ->
                repository.settings.savePersonalStyleProfile(tracked.data)
                repository.recordApiUsage(
                    tracked.usage.toRecord(taggingModel, ApiUsageRecord.TASK_COLOR_BODY_PROFILE)
                )
                localState.update {
                    it.copy(
                        isAnalyzingPersonalProfile = false,
                        snackbarMessage = "已完成個人色彩（${tracked.data.personalColorSeason}）與身形（${tracked.data.bodyShape}）建檔！"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        isAnalyzingPersonalProfile = false,
                        snackbarMessage = err.message ?: "個人色彩與身形分析失敗"
                    )
                }
            }
        }
    }

    fun updateManualPersonalProfile(colorSeasonId: String, bodyShapeId: String) {
        viewModelScope.launch {
            val seasonOpt = SettingsDataStore.PERSONAL_COLOR_OPTIONS.find { it.id == colorSeasonId }
                ?: SettingsDataStore.PERSONAL_COLOR_OPTIONS.first()
            val shapeOpt = SettingsDataStore.BODY_SHAPE_OPTIONS.find { it.id == bodyShapeId }
                ?: SettingsDataStore.BODY_SHAPE_OPTIONS.first()

            val updated = PersonalStyleProfile(
                personalColorSeason = seasonOpt.id,
                bodyShape = shapeOpt.id,
                recommendedColors = seasonOpt.recommendedColors,
                avoidColors = seasonOpt.avoidColors,
                fitAdvice = shapeOpt.fitAdvice,
                updatedAt = System.currentTimeMillis()
            )
            repository.settings.savePersonalStyleProfile(updated)
            localState.update {
                it.copy(snackbarMessage = "已手動更新個人色彩與身形檔案：${seasonOpt.id} · ${shapeOpt.id}")
            }
        }
    }

    fun setActivePersonPhoto(photo: PersonPhoto) {
        viewModelScope.launch {
            repository.settings.setActivePersonPhotoId(photo.id)
            localState.update {
                it.copy(
                    beforePhotoPath = photo.imagePath,
                    afterResultPath = null,
                    isResultFromCache = false,
                    snackbarMessage = "已切換預設試穿模特兒：${photo.label}"
                )
            }
        }
    }

    fun deletePersonPhoto(photo: PersonPhoto) {
        viewModelScope.launch {
            repository.deletePersonPhoto(photo)
            localState.update {
                it.copy(
                    beforePhotoPath = null,
                    afterResultPath = null,
                    isResultFromCache = false,
                    snackbarMessage = "已刪除全身基準照"
                )
            }
        }
    }

    fun addBasePersonPhotoFromFile(file: File, labelPrefix: String = "全身基準照") {
        viewModelScope.launch {
            runCatching {
                val bitmap = imageStorage.loadSafeBitmapFromFile(file, SafeImageStorage.MAX_IMAGE_EDGE_PX)
                val savedPath = repository.savePersonBitmapToInternal(bitmap)
                val count = uiState.value.personPhotos.size + 1
                repository.insertPersonPhotoAndSetActive(
                    label = "$labelPrefix #$count",
                    imagePath = savedPath
                )
            }.also {
                runCatching { file.delete() }
            }.onSuccess { savedPhoto ->
                localState.update {
                    it.copy(
                        showBaseCameraDialog = false,
                        beforePhotoPath = savedPhoto.imagePath,
                        afterResultPath = null,
                        isResultFromCache = false,
                        snackbarMessage = "已離線儲存並套用「${savedPhoto.label}」"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(snackbarMessage = err.message ?: "儲存全身照失敗")
                }
            }
        }
    }

    fun addBasePersonPhotoFromUri(uri: Uri, labelPrefix: String = "相簿全身照") {
        viewModelScope.launch {
            runCatching {
                val bitmap = imageStorage.loadSafeBitmapFromUri(uri, SafeImageStorage.MAX_IMAGE_EDGE_PX)
                val savedPath = repository.savePersonBitmapToInternal(bitmap)
                val count = uiState.value.personPhotos.size + 1
                repository.insertPersonPhotoAndSetActive(
                    label = "$labelPrefix #$count",
                    imagePath = savedPath
                )
            }.onSuccess { savedPhoto ->
                localState.update {
                    it.copy(
                        showBaseCameraDialog = false,
                        beforePhotoPath = savedPhoto.imagePath,
                        afterResultPath = null,
                        isResultFromCache = false,
                        snackbarMessage = "已離線儲存並套用「${savedPhoto.label}」"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(snackbarMessage = err.message ?: "匯入全身照失敗")
                }
            }
        }
    }

    /**
     * Batch Wardrobe Ingestion (up to 30 photos) with:
     * 1. Semaphore(permits = 3) limiting concurrent Bitmap downsampling + ML Kit Subject Segmentation to prevent OOM.
     * 2. Immediate DB insert after each item finishes Stage 1 so the UI updates progressively.
     * 3. Sequential Stage 2 Gemini JSON auto-tagging spaced by 300ms to prevent HTTP 429 rate-limit bursts.
     */
    fun ingestClothingUris(uris: List<Uri>, autoSelectForTryOn: Boolean = false) {
        val boundedUris = uris.take(30)
        if (boundedUris.isEmpty()) return

        viewModelScope.launch {
            val total = boundedUris.size
            localState.update {
                it.copy(
                    selectedCategoryFilter = "全部",
                    isImportingGarments = true,
                    importProgressCurrent = 0,
                    importProgressTotal = total,
                    importStatusText = "正在處理匯入單品 0 / $total..."
                )
            }

            val currentApiKey = repository.settings.geminiApiKeyFlow.first()
            val currentTaggingModel = repository.settings.taggingModelFlow.first()
            val completedCounter = AtomicInteger(0)
            var lastSaveError: String? = null

            val insertedItems = coroutineScope {
                boundedUris.map { uri ->
                    async {
                        localProcessingSemaphore.withPermit {
                            runCatching {
                                val sanitized = imageStorage.loadSafeBitmapFromUri(
                                    uri = uri,
                                    maxEdge = SafeImageStorage.MAX_IMAGE_EDGE_PX
                                )
                                val segmented = backgroundRemover.removeBackgroundWithFallback(sanitized)
                                val permanentPath = repository.saveClothingBitmapToInternal(
                                    bitmap = segmented.cutoutBitmap,
                                    preferPng = segmented.foregroundDetected
                                )

                                val hasKey = currentApiKey.isNotBlank()
                                val initialEntity = ClothingItem(
                                    imagePath = permanentPath,
                                    category = "待分類",
                                    subCategory = if (hasKey) "排隊分析標籤中..." else "待同步標籤",
                                    color = "待分析",
                                    fit = "合身",
                                    styleTags = emptyList(),
                                    season = emptyList(),
                                    tagStatus = if (hasKey) {
                                        ClothingItem.TAG_STATUS_PENDING
                                    } else {
                                        ClothingItem.TAG_STATUS_FAILED
                                    },
                                    tagError = if (hasKey) {
                                        null
                                    } else {
                                        "尚未設定 Gemini API Key，請先至設定輸入後點擊重試"
                                    }
                                )
                                val newId = repository.insertClothingItem(initialEntity)
                                val currentDone = completedCounter.incrementAndGet()
                                localState.update {
                                    it.copy(
                                        importProgressCurrent = currentDone,
                                        importProgressTotal = total,
                                        importStatusText = "正在處理匯入單品 $currentDone / $total..."
                                    )
                                }
                                initialEntity.copy(id = newId)
                            }.onFailure { err ->
                                lastSaveError = err.message
                                val currentDone = completedCounter.incrementAndGet()
                                localState.update {
                                    it.copy(
                                        importProgressCurrent = currentDone,
                                        importProgressTotal = total,
                                        importStatusText = "正在處理匯入單品 $currentDone / $total..."
                                    )
                                }
                            }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (autoSelectForTryOn && insertedItems.isNotEmpty()) {
                val newIds = insertedItems.map { it.id }.toSet()
                localState.update { state ->
                    state.copy(
                        selectedClothingIds = state.selectedClothingIds + newIds,
                        selectedTab = AppTab.TRY_ON
                    )
                }
            }

            localState.update {
                it.copy(
                    isImportingGarments = false,
                    importStatusText = "",
                    importProgressCurrent = 0,
                    importProgressTotal = 0,
                    snackbarMessage = when {
                        insertedItems.isEmpty() -> lastSaveError ?: "無法讀取並儲存選取的圖片"
                        currentApiKey.isBlank() -> "已離線儲存 ${insertedItems.size} 件單品（尚未設定 API Key，可稍後一鍵補標籤）"
                        else -> "已立即入庫 ${insertedItems.size} 件單品，背景序列 AI 打標同步中..."
                    }
                )
            }

            // Stage 2: Sequential background Gemini tagging with 300ms spacing to prevent HTTP 429
            if (currentApiKey.isNotBlank() && insertedItems.isNotEmpty()) {
                launch {
                    insertedItems.forEachIndexed { idx, savedItem ->
                        if (idx > 0) delay(300L)
                        runBackgroundTagging(savedItem, currentApiKey, currentTaggingModel)
                    }
                }
            }
        }
    }

    fun retryGarmentTagging(item: ClothingItem) {
        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 後再同步衣物標籤"
                    )
                }
                return@launch
            }

            val pendingItem = item.copy(
                subCategory = "重新分析中...",
                tagStatus = ClothingItem.TAG_STATUS_PENDING,
                tagError = null
            )
            repository.updateClothingItem(pendingItem)
            runBackgroundTagging(pendingItem, apiKey, taggingModel)
        }
    }

    fun retryAllFailedTagging() {
        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 後再重試同步標籤"
                    )
                }
                return@launch
            }

            val failedItems = repository.getAllClothingSnapshot().filter {
                it.tagStatus == ClothingItem.TAG_STATUS_FAILED
            }
            if (failedItems.isEmpty()) return@launch

            localState.update {
                it.copy(snackbarMessage = "正在背景序列同步 ${failedItems.size} 件單品標籤（每件間隔 300ms）...")
            }
            failedItems.forEach { item ->
                repository.updateClothingItem(
                    item.copy(
                        subCategory = "排隊分析中...",
                        tagStatus = ClothingItem.TAG_STATUS_PENDING,
                        tagError = null
                    )
                )
            }
            launch {
                failedItems.forEachIndexed { idx, item ->
                    if (idx > 0) delay(300L)
                    runBackgroundTagging(
                        item.copy(
                            subCategory = "重新分析中...",
                            tagStatus = ClothingItem.TAG_STATUS_PENDING,
                            tagError = null
                        ),
                        apiKey,
                        taggingModel
                    )
                }
            }
        }
    }

    private suspend fun runBackgroundTagging(
        item: ClothingItem,
        apiKey: String,
        taggingModelId: String
    ) {
        runCatching {
            val bitmap = imageStorage.decodeStoredBitmap(item.imagePath, maxEdge = 768)
                ?: error("找不到本機衣物影像檔案")
            geminiService.autoTagGarment(
                apiKey = apiKey,
                modelId = taggingModelId,
                garmentBitmap = bitmap
            )
        }.onSuccess { tracked ->
            val tags = tracked.data
            repository.updateClothingItem(
                item.copy(
                    category = tags.category,
                    subCategory = tags.subCategory,
                    color = tags.color,
                    fit = tags.fit,
                    styleTags = tags.styleTags,
                    season = tags.season,
                    tagStatus = ClothingItem.TAG_STATUS_DONE,
                    tagError = null
                )
            )
            repository.recordApiUsage(
                tracked.usage.toRecord(taggingModelId, ApiUsageRecord.TASK_TAGGING)
            )
        }.onFailure { err ->
            repository.updateClothingItem(
                item.copy(
                    subCategory = "待同步標籤",
                    tagStatus = ClothingItem.TAG_STATUS_FAILED,
                    tagError = err.message ?: "Gemini 自動打標失敗"
                )
            )
        }
    }

    fun deleteClothingItem(item: ClothingItem) {
        viewModelScope.launch {
            repository.deleteClothingItem(item)
            localState.update { state ->
                state.copy(
                    selectedClothingIds = state.selectedClothingIds - item.id,
                    snackbarMessage = "已刪除「${item.subCategory}」"
                )
            }
        }
    }

    fun startTryOnWithSelectedFromWardrobe() {
        localState.update { it.copy(selectedTab = AppTab.TRY_ON) }
        runVirtualTryOn(forceRegenerate = false)
    }

    fun runVirtualTryOn(
        forceRegenerate: Boolean = false,
        overrideClothingIds: List<Long>? = null,
        overrideMallItems: List<MallBrandItem>? = null
    ) {
        val snapshot = uiState.value
        val activePerson = snapshot.activePersonPhoto
        if (activePerson == null) {
            localState.update {
                it.copy(
                    showBaseCameraDialog = true,
                    snackbarMessage = "請先拍攝或選取一張全身基準照"
                )
            }
            return
        }

        val selectedIds = (overrideClothingIds ?: localState.value.selectedClothingIds.toList()).distinct()
        val selectedMall = overrideMallItems ?: localState.value.selectedMallItemsForTryOn
        val topMode = localState.value.topStylingMode
        val outerMode = localState.value.outerStylingMode

        if (selectedIds.isEmpty() && selectedMall.isEmpty()) {
            localState.update {
                it.copy(snackbarMessage = "請先從衣櫃或商城推薦勾選至少一件試穿單品")
            }
            return
        }

        viewModelScope.launch {
            val mallIds = selectedMall.map { it.id }
            if (!forceRegenerate) {
                val cached = repository.findValidTryOnCache(
                    personPhotoId = activePerson.id,
                    clothingIds = selectedIds,
                    topMode = topMode,
                    outerMode = outerMode,
                    mallItemIds = mallIds
                )
                if (cached != null) {
                    localState.update {
                        it.copy(
                            beforePhotoPath = activePerson.imagePath,
                            afterResultPath = cached.resultImagePath,
                            isResultFromCache = true,
                            snackbarMessage = "快取命中！秒開本地全身實穿照（未消耗 Gemini 額度）"
                        )
                    }
                    return@launch
                }
            }

            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val tryOnModel = repository.settings.tryOnModelFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 才能執行雲端換裝合成"
                    )
                }
                return@launch
            }

            localState.update {
                it.copy(
                    isGeneratingTryOn = true,
                    tryOnStatusText = "正在透過 $tryOnModel 寫實合成全身實穿照...",
                    beforePhotoPath = activePerson.imagePath
                )
            }

            runCatching {
                val personBitmap = imageStorage.decodeStoredBitmap(
                    activePerson.imagePath,
                    maxEdge = SafeImageStorage.MAX_IMAGE_EDGE_PX
                ) ?: error("找不到全身基準照檔案")

                val orderedClothing = repository.getClothingByIds(selectedIds)
                    .sortedWith(compareBy { categoryLayerOrder(it.category) })

                val clothingBitmaps = orderedClothing.mapNotNull { garment ->
                    imageStorage.decodeStoredBitmap(garment.imagePath, maxEdge = 1024)
                        ?.let { bmp -> garment to bmp }
                }

                val trackedResult = geminiService.generateVirtualTryOn(
                    apiKey = apiKey,
                    modelId = tryOnModel,
                    personBitmap = personBitmap,
                    clothingItems = clothingBitmaps,
                    topStylingMode = topMode,
                    outerStylingMode = outerMode,
                    mallSpecs = selectedMall
                )

                repository.recordApiUsage(
                    trackedResult.usage.toRecord(tryOnModel, ApiUsageRecord.TASK_TRY_ON)
                )

                repository.storeTryOnCache(
                    personPhoto = activePerson,
                    clothingIds = selectedIds,
                    topMode = topMode,
                    outerMode = outerMode,
                    mallItemIds = mallIds,
                    resultBitmap = trackedResult.data,
                    modelUsed = tryOnModel
                )
            }.onSuccess { cachedEntry ->
                localState.update {
                    it.copy(
                        isGeneratingTryOn = false,
                        tryOnStatusText = "",
                        beforePhotoPath = activePerson.imagePath,
                        afterResultPath = cachedEntry.resultImagePath,
                        isResultFromCache = false,
                        snackbarMessage = "全身寫實實穿合成完成！左右滑動可對比，點擊「確認採用」可計入穿著次數"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        isGeneratingTryOn = false,
                        tryOnStatusText = "",
                        snackbarMessage = err.message ?: "實穿生成失敗，請稍後再試"
                    )
                }
            }
        }
    }

    fun openCachedEntry(entry: TryOnCache) {
        viewModelScope.launch {
            val matchingPerson = uiState.value.personPhotos.find { it.id == entry.personPhotoId }
            if (matchingPerson != null) {
                repository.settings.setActivePersonPhotoId(matchingPerson.id)
            }
            localState.update {
                it.copy(
                    selectedTab = AppTab.TRY_ON,
                    selectedClothingIds = entry.clothingIds.toSet(),
                    beforePhotoPath = entry.personPhotoPath,
                    afterResultPath = entry.resultImagePath,
                    isResultFromCache = true,
                    snackbarMessage = "已載入歷史實穿紀錄"
                )
            }
        }
    }

    fun saveCurrentResultToGallery() {
        val resultPath = uiState.value.afterResultPath ?: return
        val currentIds = uiState.value.selectedClothingIds.toList()
        viewModelScope.launch {
            runCatching {
                val bmp = imageStorage.decodeStoredBitmap(resultPath, maxEdge = 2400)
                    ?: error("找不到實穿結果影像")
                repository.saveBitmapToGallery(bmp).getOrThrow()
            }.onSuccess {
                if (currentIds.isNotEmpty()) {
                    repository.incrementWearCount(currentIds)
                }
                localState.update {
                    it.copy(
                        snackbarMessage = if (currentIds.isNotEmpty()) {
                            "已儲存至相簿 Pictures/FitStudioAI，並為組合內 ${currentIds.size} 件單品穿著次數 +1"
                        } else {
                            "已儲存至手機相簿 Pictures/FitStudioAI"
                        }
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(snackbarMessage = err.message ?: "儲存至相簿失敗")
                }
            }
        }
    }

    // --- AI Stylist & Random Blind-Box Inspiration ---

    fun selectStylePreset(preset: String) {
        localState.update { it.copy(selectedStylePreset = preset) }
    }

    fun updateCustomStylistPrompt(text: String) {
        localState.update { it.copy(customStylistPrompt = text) }
    }

    fun setAnchorItem(itemId: Long?) {
        localState.update { it.copy(anchorItemId = itemId) }
    }

    fun jumpToStylistWithAnchor(item: ClothingItem) {
        val displayTitle = if (item.tagStatus == ClothingItem.TAG_STATUS_DONE) {
            "${item.color}${item.subCategory}"
        } else {
            "這件選中單品（ID=${item.id}）"
        }
        localState.update {
            it.copy(
                selectedTab = AppTab.STYLIST,
                anchorItemId = item.id,
                customStylistPrompt = "我想穿${displayTitle}，幫我從衣櫃配一套"
            )
        }
    }

    fun drawRandomBlindBoxOutfit() {
        viewModelScope.launch {
            val allItems = repository.getAllClothingSnapshot()
            if (allItems.isEmpty()) {
                localState.update {
                    it.copy(snackbarMessage = "請先匯入幾件衣物再使用「🎲 隨機靈感穿搭」")
                }
                return@launch
            }

            localState.update { it.copy(isDrawingBlindBox = true) }
            val profile = repository.settings.personalStyleProfileFlow.first()

            val tops = allItems.filter { it.category == ClothingItem.CAT_TOP }
            val bottoms = allItems.filter { it.category == ClothingItem.CAT_BOTTOM }
            val onePieces = allItems.filter { it.category == ClothingItem.CAT_ONE_PIECE }
            val outers = allItems.filter { it.category == ClothingItem.CAT_OUTER }
            val shoes = allItems.filter { it.category == ClothingItem.CAT_SHOES }
            val accessories = allItems.filter {
                it.category in listOf(
                    ClothingItem.CAT_HAT_SCARF,
                    ClothingItem.CAT_EYEWEAR,
                    ClothingItem.CAT_HAIR_ACCESSORY,
                    ClothingItem.CAT_BAG,
                    ClothingItem.CAT_ACCESSORY
                )
            }

            val chosen = mutableListOf<ClothingItem>()
            if (onePieces.isNotEmpty() && (tops.isEmpty() || bottoms.isEmpty() || Math.random() < 0.28)) {
                chosen.add(onePieces.random())
            } else {
                if (tops.isNotEmpty()) chosen.add(tops.random())
                if (bottoms.isNotEmpty()) chosen.add(bottoms.random())
            }
            if (outers.isNotEmpty() && Math.random() < 0.55) {
                chosen.add(outers.random())
            }
            if (shoes.isNotEmpty()) {
                chosen.add(shoes.random())
            }
            if (accessories.isNotEmpty() && Math.random() < 0.70) {
                chosen.add(accessories.random())
            }
            if (chosen.isEmpty()) {
                chosen.addAll(allItems.shuffled().take(minOf(3, allItems.size)))
            }

            val distinctChosen = chosen.distinctBy { it.id }
            val colorsSummary = distinctChosen.map { it.color }.distinct().joinToString(" × ")
            val profileHint = if (profile.personalColorSeason.isNotBlank()) {
                "呼應您的「${profile.personalColorSeason}」色調，"
            } else ""

            val blindBoxRec = GeminiWardrobeService.OutfitRecommendation(
                title = "🎲 盲盒靈感：$colorsSummary 混搭提案",
                rationale = "${profileHint}以 ${distinctChosen.joinToString("、") { it.subCategory }} 碰撞出日常新鮮層次感！",
                itemIds = distinctChosen.map { it.id }
            )

            localState.update {
                it.copy(
                    isDrawingBlindBox = false,
                    randomBlindBoxRecommendation = blindBoxRec,
                    snackbarMessage = "🎲 已抽出隨機靈感穿搭：${blindBoxRec.title}"
                )
            }
        }
    }

    fun dismissRandomBlindBox() {
        localState.update { it.copy(randomBlindBoxRecommendation = null) }
    }

    fun generateOutfitRecommendations() {
        val snapshot = uiState.value
        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
            val profile = repository.settings.personalStyleProfileFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 以產生 AI 穿搭推薦"
                    )
                }
                return@launch
            }

            localState.update {
                it.copy(isGeneratingRecommendations = true)
            }
            runCatching {
                val allItems = repository.getAllClothingSnapshot()
                require(allItems.isNotEmpty()) { "數位衣櫃目前沒有單品，請先拍攝或匯入衣物" }
                val readyItems = allItems.filter {
                    it.tagStatus == ClothingItem.TAG_STATUS_DONE || it.id == snapshot.anchorItemId
                }
                require(allItems.any { it.tagStatus == ClothingItem.TAG_STATUS_DONE }) {
                    "衣櫃單品尚待完成 AI 標籤同步，請先於衣櫃卡片點擊重試標籤"
                }
                geminiService.recommendOutfits(
                    apiKey = apiKey,
                    modelId = taggingModel,
                    wardrobe = readyItems,
                    stylePreset = snapshot.selectedStylePreset,
                    customRequest = snapshot.customStylistPrompt,
                    anchorItemId = snapshot.anchorItemId,
                    personalProfile = profile
                )
            }.onSuccess { tracked ->
                repository.recordApiUsage(
                    tracked.usage.toRecord(taggingModel, ApiUsageRecord.TASK_STYLING)
                )
                localState.update {
                    it.copy(
                        isGeneratingRecommendations = false,
                        recommendations = tracked.data,
                        snackbarMessage = "已透過 $taggingModel 結合個人檔案挑選 ${tracked.data.size} 套穿搭"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        isGeneratingRecommendations = false,
                        snackbarMessage = err.message ?: "AI 推薦產生失敗"
                    )
                }
            }
        }
    }

    fun applyRecommendationAndTryOn(recommendation: GeminiWardrobeService.OutfitRecommendation) {
        localState.update {
            it.copy(
                selectedClothingIds = recommendation.itemIds.toSet(),
                selectedMallItemsForTryOn = emptyList(),
                randomBlindBoxRecommendation = null,
                selectedTab = AppTab.TRY_ON
            )
        }
        runVirtualTryOn(
            forceRegenerate = false,
            overrideClothingIds = recommendation.itemIds,
            overrideMallItems = emptyList()
        )
    }

    // --- Online Mall Styling (UNIQLO / GU / MUJI) ---

    fun selectMallBrand(brand: String) {
        localState.update { it.copy(selectedMallBrand = brand) }
    }

    fun setMallMixWithWardrobe(mix: Boolean) {
        localState.update { it.copy(mallMixWithWardrobe = mix) }
    }

    fun generateMallOutfitRecommendations() {
        val snapshot = uiState.value
        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
            val profile = repository.settings.personalStyleProfileFlow.first()
            if (apiKey.isBlank()) {
                localState.update {
                    it.copy(
                        showApiKeyDialog = true,
                        snackbarMessage = "請先設定 Gemini API Key 以產生線上商城穿搭提案"
                    )
                }
                return@launch
            }

            localState.update { it.copy(isGeneratingMallRecommendations = true) }
            runCatching {
                val filteredMall = MallCatalog.filterByBrand(snapshot.selectedMallBrand)
                val readyWardrobe = repository.getAllClothingSnapshot()
                    .filter { it.tagStatus == ClothingItem.TAG_STATUS_DONE }

                geminiService.recommendMallOutfits(
                    apiKey = apiKey,
                    modelId = taggingModel,
                    mallCatalog = filteredMall,
                    wardrobe = readyWardrobe,
                    mixWithWardrobe = snapshot.mallMixWithWardrobe,
                    stylePreset = snapshot.selectedStylePreset,
                    personalProfile = profile
                )
            }.onSuccess { tracked ->
                repository.recordApiUsage(
                    tracked.usage.toRecord(taggingModel, ApiUsageRecord.TASK_MALL_STYLING)
                )
                localState.update {
                    it.copy(
                        isGeneratingMallRecommendations = false,
                        mallRecommendations = tracked.data,
                        snackbarMessage = "已生成 ${tracked.data.size} 套 ${snapshot.selectedMallBrand} 穿搭提案"
                    )
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(
                        isGeneratingMallRecommendations = false,
                        snackbarMessage = err.message ?: "商城穿搭推薦產生失敗"
                    )
                }
            }
        }
    }

    fun tryOnMallRecommendation(rec: GeminiWardrobeService.MallOutfitRecommendation) {
        val mallObjects = rec.mallItemIds.mapNotNull { id ->
            MallCatalog.EVERGREEN_ITEMS.find { it.id == id }
        }
        localState.update {
            it.copy(
                selectedClothingIds = rec.wardrobeItemIds.toSet(),
                selectedMallItemsForTryOn = mallObjects,
                selectedTab = AppTab.TRY_ON
            )
        }
        runVirtualTryOn(
            forceRegenerate = false,
            overrideClothingIds = rec.wardrobeItemIds,
            overrideMallItems = mallObjects
        )
    }

    fun tryOnSingleMallItem(mallItem: MallBrandItem) {
        localState.update {
            it.copy(
                selectedMallItemsForTryOn = listOf(mallItem),
                selectedTab = AppTab.TRY_ON
            )
        }
        runVirtualTryOn(
            forceRegenerate = false,
            overrideClothingIds = localState.value.selectedClothingIds.toList(),
            overrideMallItems = listOf(mallItem)
        )
    }

    // --- One-Click ZIP Backup & Restore ---

    fun exportWardrobeBackupZip(targetUri: Uri) {
        viewModelScope.launch {
            localState.update { it.copy(isRunningBackupOrRestore = true) }
            backupManager.exportBackupToZipUri(targetUri)
                .onSuccess { summary ->
                    localState.update {
                        it.copy(
                            isRunningBackupOrRestore = false,
                            snackbarMessage = "備份成功！已打包 ${summary.clothingCount} 件單品與 ${summary.personPhotoCount} 張全身照 (.zip)"
                        )
                    }
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isRunningBackupOrRestore = false,
                            snackbarMessage = "匯出備份失敗：${err.message}"
                        )
                    }
                }
        }
    }

    fun importWardrobeBackupZip(sourceUri: Uri, overwriteExisting: Boolean) {
        viewModelScope.launch {
            localState.update { it.copy(isRunningBackupOrRestore = true) }
            backupManager.importBackupFromZipUri(sourceUri, overwriteExisting)
                .onSuccess { summary ->
                    val modeLabel = if (overwriteExisting) "覆蓋還原" else "合併還原"
                    localState.update {
                        it.copy(
                            isRunningBackupOrRestore = false,
                            snackbarMessage = "$modeLabel 完成！已載入 ${summary.clothingCount} 件單品與 ${summary.personPhotoCount} 張全身照"
                        )
                    }
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isRunningBackupOrRestore = false,
                            snackbarMessage = "還原備份失敗：${err.message}"
                        )
                    }
                }
        }
    }

    private fun categoryLayerOrder(category: String): Int = when (category) {
        ClothingItem.CAT_ONE_PIECE -> 0
        ClothingItem.CAT_TOP -> 1
        ClothingItem.CAT_BOTTOM -> 2
        ClothingItem.CAT_OUTER -> 3
        ClothingItem.CAT_SHOES -> 4
        ClothingItem.CAT_HAT_SCARF -> 5
        ClothingItem.CAT_EYEWEAR -> 6
        ClothingItem.CAT_HAIR_ACCESSORY -> 7
        ClothingItem.CAT_BAG -> 8
        ClothingItem.CAT_ACCESSORY -> 9
        else -> 10
    }
}
