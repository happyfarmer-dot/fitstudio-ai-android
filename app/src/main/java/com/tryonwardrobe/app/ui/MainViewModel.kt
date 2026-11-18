package com.tryonwardrobe.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tryonwardrobe.app.WardrobeApplication
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.SafeImageStorage
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab(val label: String) {
    WARDROBE("數位衣櫃"),
    TRY_ON("寫實試穿"),
    STYLIST("AI 穿搭建議")
}

data class WardrobeUiState(
    val selectedTab: AppTab = AppTab.WARDROBE,
    val selectedCategoryFilter: String = "全部",
    val clothingItems: List<ClothingItem> = emptyList(),
    val personPhotos: List<PersonPhoto> = emptyList(),
    val activePersonPhoto: PersonPhoto? = null,
    val recentCaches: List<TryOnCache> = emptyList(),
    val selectedClothingIds: Set<Long> = emptySet(),
    val geminiApiKey: String = "",
    val taggingModel: String = SettingsDataStore.DEFAULT_TAGGING_MODEL,
    val tryOnModel: String = SettingsDataStore.DEFAULT_TRY_ON_MODEL,
    val isImportingGarments: Boolean = false,
    val importStatusText: String = "",
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
    val showApiKeyDialog: Boolean = false,
    val showBaseCameraDialog: Boolean = false,
    val snackbarMessage: String? = null
)

private data class LocalActionState(
    val selectedTab: AppTab = AppTab.WARDROBE,
    val selectedCategoryFilter: String = "全部",
    val selectedClothingIds: Set<Long> = emptySet(),
    val isImportingGarments: Boolean = false,
    val importStatusText: String = "",
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
    val tryOnModel: String = SettingsDataStore.DEFAULT_TRY_ON_MODEL
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WardrobeApplication
    private val repository = app.wardrobeRepository
    private val imageStorage = app.safeImageStorage
    private val backgroundRemover = app.backgroundRemover
    private val geminiService = app.geminiWardrobeService

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
    }

    val uiState: StateFlow<WardrobeUiState> = combine(persistentFlow, localState) { db, local ->
        val activePerson = db.persons.find { it.id == db.activePersonId } ?: db.persons.firstOrNull()
        val validIds = db.clothing.map { it.id }.toSet()
        val cleanedSelected = local.selectedClothingIds.intersect(validIds)

        WardrobeUiState(
            selectedTab = local.selectedTab,
            selectedCategoryFilter = local.selectedCategoryFilter,
            clothingItems = db.clothing,
            personPhotos = db.persons,
            activePersonPhoto = activePerson,
            recentCaches = db.caches,
            selectedClothingIds = cleanedSelected,
            geminiApiKey = db.apiKey,
            taggingModel = db.taggingModel,
            tryOnModel = db.tryOnModel,
            isImportingGarments = local.isImportingGarments,
            importStatusText = local.importStatusText,
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
            showApiKeyDialog = local.showApiKeyDialog,
            showBaseCameraDialog = local.showBaseCameraDialog,
            snackbarMessage = local.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WardrobeUiState()
    )

    fun selectTab(tab: AppTab) {
        localState.update { it.copy(selectedTab = tab) }
    }

    fun selectCategoryFilter(category: String) {
        localState.update { it.copy(selectedCategoryFilter = category) }
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
        localState.update { it.copy(selectedClothingIds = emptySet()) }
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

    /**
     * Offline-first Full-Body Base Photo ingestion from CameraX capture File:
     * downsamples <= 1280px, fixes EXIF orientation, writes permanently to filesDir/full_body.
     */
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

    /**
     * Offline-first Full-Body Base Photo ingestion from Photo Picker / Gallery Uri:
     * copies content:// stream into app filesDir/full_body immediately to prevent URI expiry SecurityException.
     */
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
     * Two-Stage Decoupled Wardrobe Upload Pipeline:
     * Step 1 (Local immediate save): Sanitize URI via SafeImageStorage (<= 1280px + EXIF),
     * run offline ML Kit background removal with runCatching fallback, write permanent file
     * to filesDir/wardrobe, and immediately insert ClothingItem into Room so UI updates 100% offline.
     * Step 2 (Background async AI tagging): Launch non-blocking Gemini tagging per saved item;
     * if API fails or API Key is blank, preserve the image and mark "待同步標籤（可點擊重試）" with HTTP detail.
     */
    fun ingestClothingUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            localState.update {
                it.copy(
                    selectedCategoryFilter = "全部",
                    isImportingGarments = true,
                    importStatusText = "正在本機儲存 ${uris.size} 件衣物影像..."
                )
            }

            val currentApiKey = repository.settings.geminiApiKeyFlow.first()
            val currentTaggingModel = repository.settings.taggingModelFlow.first()
            var savedCount = 0
            var lastSaveError: String? = null
            val pendingItems = mutableListOf<ClothingItem>()

            uris.forEachIndexed { index, uri ->
                runCatching {
                    localState.update {
                        it.copy(importStatusText = "(${index + 1}/${uris.size}) 本機安全壓縮與去背中...")
                    }
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
                        subCategory = if (hasKey) "分析標籤中..." else "待同步標籤",
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
                    initialEntity.copy(id = newId)
                }.onSuccess { inserted ->
                    savedCount++
                    if (currentApiKey.isNotBlank()) {
                        pendingItems.add(inserted)
                    }
                }.onFailure { err ->
                    lastSaveError = err.message
                }
            }

            localState.update {
                it.copy(
                    isImportingGarments = false,
                    importStatusText = "",
                    snackbarMessage = when {
                        savedCount == 0 -> lastSaveError ?: "無法讀取並儲存選取的圖片"
                        currentApiKey.isBlank() -> "已離線儲存 $savedCount 件單品（尚未設定 API Key，可稍後一鍵補標籤）"
                        else -> "已立即入庫 $savedCount 件單品，背景 AI 打標同步中..."
                    }
                )
            }

            // Stage 2: Non-blocking background Gemini tagging for each saved item
            pendingItems.forEach { savedItem ->
                launch {
                    runBackgroundTagging(savedItem, currentApiKey, currentTaggingModel)
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
                it.copy(snackbarMessage = "正在背景重新同步 ${failedItems.size} 件單品標籤...")
            }
            failedItems.forEach { item ->
                launch {
                    val pendingItem = item.copy(
                        subCategory = "重新分析中...",
                        tagStatus = ClothingItem.TAG_STATUS_PENDING,
                        tagError = null
                    )
                    repository.updateClothingItem(pendingItem)
                    runBackgroundTagging(pendingItem, apiKey, taggingModel)
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
        }.onSuccess { tags ->
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
        overrideClothingIds: List<Long>? = null
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
        if (selectedIds.isEmpty()) {
            localState.update {
                it.copy(snackbarMessage = "請先從衣櫃勾選至少一件試穿衣物")
            }
            return
        }

        viewModelScope.launch {
            if (!forceRegenerate) {
                val cached = repository.findValidTryOnCache(activePerson.id, selectedIds)
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
                require(clothingBitmaps.isNotEmpty()) { "無法讀取選定衣物的圖片檔案" }

                val generated = geminiService.generateVirtualTryOn(
                    apiKey = apiKey,
                    modelId = tryOnModel,
                    personBitmap = personBitmap,
                    clothingItems = clothingBitmaps
                )

                repository.storeTryOnCache(
                    personPhoto = activePerson,
                    clothingIds = selectedIds,
                    resultBitmap = generated,
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
                        snackbarMessage = "全身寫實實穿合成完成！左右滑動可對比前後效果"
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
        viewModelScope.launch {
            runCatching {
                val bmp = imageStorage.decodeStoredBitmap(resultPath, maxEdge = 2400)
                    ?: error("找不到實穿結果影像")
                repository.saveBitmapToGallery(bmp).getOrThrow()
            }.onSuccess {
                localState.update {
                    it.copy(snackbarMessage = "已儲存至手機相簿 Pictures/FitStudioAI")
                }
            }.onFailure { err ->
                localState.update {
                    it.copy(snackbarMessage = err.message ?: "儲存至相簿失敗")
                }
            }
        }
    }

    // --- AI Stylist Actions ---

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

    fun generateOutfitRecommendations() {
        val snapshot = uiState.value
        viewModelScope.launch {
            val apiKey = repository.settings.geminiApiKeyFlow.first()
            val taggingModel = repository.settings.taggingModelFlow.first()
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
                    anchorItemId = snapshot.anchorItemId
                )
            }.onSuccess { recs ->
                localState.update {
                    it.copy(
                        isGeneratingRecommendations = false,
                        recommendations = recs,
                        snackbarMessage = "已透過 $taggingModel 為您挑選 ${recs.size} 套衣櫃穿搭組合"
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
                selectedTab = AppTab.TRY_ON
            )
        }
        runVirtualTryOn(
            forceRegenerate = false,
            overrideClothingIds = recommendation.itemIds
        )
    }

    private fun categoryLayerOrder(category: String): Int = when (category) {
        "連身" -> 0
        "上衣" -> 1
        "下著" -> 2
        "外套" -> 3
        "鞋履" -> 4
        else -> 5
    }
}
