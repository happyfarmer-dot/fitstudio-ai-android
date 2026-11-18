package com.tryonwardrobe.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tryonwardrobe.app.WardrobeApplication
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val tryOnModel: String = SettingsDataStore.MODEL_FLASH_IMAGE,
    val isImportingGarments: Boolean = false,
    val importStatusText: String = "",
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
    val tryOnModel: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WardrobeApplication
    private val repository = app.wardrobeRepository
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
            apiKey = apiKey,
            tryOnModel = SettingsDataStore.MODEL_FLASH_IMAGE
        )
    }.combine(repository.settings.tryOnModelFlow) { bundle, model ->
        bundle.copy(tryOnModel = model)
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
            tryOnModel = db.tryOnModel,
            isImportingGarments = local.isImportingGarments,
            importStatusText = local.importStatusText,
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
        localState.update { it.copy(showApiKeyDialog = visible) }
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

    fun saveSettings(apiKey: String, modelId: String) {
        viewModelScope.launch {
            repository.settings.saveGeminiApiKey(apiKey)
            repository.settings.setTryOnModel(modelId)
            localState.update {
                it.copy(
                    showApiKeyDialog = false,
                    snackbarMessage = "設定已儲存（切換模型：$modelId）"
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

    fun addBasePersonPhotoFromFile(file: File, labelPrefix: String = "全身基準照") {
        viewModelScope.launch {
            runCatching {
                val bitmap = backgroundRemover.prepareBasePersonPhotoFromFile(file)
                val savedPath = repository.savePersonBitmapToInternal(bitmap)
                val count = uiState.value.personPhotos.size + 1
                val savedPhoto = repository.insertPersonPhotoAndSetActive(
                    label = "$labelPrefix #$count",
                    imagePath = savedPath
                )
                runCatching { file.delete() }
                savedPhoto
            }.onSuccess { savedPhoto ->
                localState.update {
                    it.copy(
                        showBaseCameraDialog = false,
                        beforePhotoPath = savedPhoto.imagePath,
                        afterResultPath = null,
                        isResultFromCache = false,
                        snackbarMessage = "已儲存並套用「${savedPhoto.label}」"
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
                val bitmap = backgroundRemover.prepareBasePersonPhoto(uri)
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
                        snackbarMessage = "已儲存並套用「${savedPhoto.label}」"
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
     * Pipeline:
     * 1. On-device ML Kit Subject Segmentation (free background removal)
     * 2. Gemini 2.5 Flash Structured JSON Auto-Tagging (single lightweight inference)
     * 3. Save transparent PNG cutout & metadata into Room DB
     */
    fun ingestClothingUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val apiKey = uiState.value.geminiApiKey
        if (apiKey.isBlank()) {
            localState.update {
                it.copy(
                    showApiKeyDialog = true,
                    snackbarMessage = "請先設定 Gemini API Key 以啟用衣物自動去背打標"
                )
            }
            return
        }

        viewModelScope.launch {
            localState.update {
                it.copy(
                    isImportingGarments = true,
                    importStatusText = "準備處理 ${uris.size} 件衣物..."
                )
            }

            var successCount = 0
            var lastError: String? = null

            uris.forEachIndexed { index, uri ->
                runCatching {
                    localState.update {
                        it.copy(importStatusText = "(${index + 1}/${uris.size}) 端側 ML Kit 去背中...")
                    }
                    val segmented = backgroundRemover.removeBackground(uri)

                    localState.update {
                        it.copy(importStatusText = "(${index + 1}/${uris.size}) Gemini 自動辨識標籤...")
                    }
                    val tags = geminiService.autoTagGarment(apiKey, segmented.cutoutBitmap)
                    val cutoutPath = repository.saveClothingBitmapToInternal(segmented.cutoutBitmap)

                    val entity = ClothingItem(
                        imagePath = cutoutPath,
                        category = tags.category,
                        subCategory = tags.subCategory,
                        color = tags.color,
                        fit = tags.fit,
                        styleTags = tags.styleTags,
                        season = tags.season
                    )
                    repository.insertClothingItem(entity)
                    successCount++
                }.onFailure { err ->
                    lastError = err.message
                }
            }

            localState.update {
                it.copy(
                    isImportingGarments = false,
                    importStatusText = "",
                    snackbarMessage = when {
                        successCount == uris.size -> "成功去背並打標入庫 $successCount 件單品"
                        successCount > 0 -> "已入庫 $successCount/${uris.size} 件（部分失敗：${lastError ?: "未知錯誤"}）"
                        else -> lastError ?: "衣物入庫失敗"
                    }
                )
            }
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

    /**
     * Executes virtual try-on for the active base PersonPhoto + currently selected clothing IDs.
     * Checks SHA-256 Room cache first so identical combinations open instantaneously at 0 API cost.
     */
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

            val apiKey = snapshot.geminiApiKey
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
                    tryOnStatusText = "正在透過 ${snapshot.tryOnModel} 寫實合成全身實穿照...",
                    beforePhotoPath = activePerson.imagePath
                )
            }

            runCatching {
                val personBitmap = backgroundRemover.decodeFileBitmap(activePerson.imagePath, maxEdge = 1536)
                    ?: error("找不到全身基準照檔案")

                val orderedClothing = repository.getClothingByIds(selectedIds)
                    .sortedWith(compareBy { categoryLayerOrder(it.category) })

                val clothingBitmaps = orderedClothing.mapNotNull { garment ->
                    backgroundRemover.decodeFileBitmap(garment.imagePath, maxEdge = 1024)
                        ?.let { bmp -> garment to bmp }
                }
                require(clothingBitmaps.isNotEmpty()) { "無法讀取選定衣物的去背圖" }

                val generated = geminiService.generateVirtualTryOn(
                    apiKey = apiKey,
                    modelId = snapshot.tryOnModel,
                    personBitmap = personBitmap,
                    clothingItems = clothingBitmaps
                )

                repository.storeTryOnCache(
                    personPhoto = activePerson,
                    clothingIds = selectedIds,
                    resultBitmap = generated,
                    modelUsed = snapshot.tryOnModel
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
                val bmp = backgroundRemover.decodeFileBitmap(resultPath, maxEdge = 2400)
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
        localState.update {
            it.copy(
                selectedTab = AppTab.STYLIST,
                anchorItemId = item.id,
                customStylistPrompt = "我想穿這件${item.color}${item.subCategory}，幫我從衣櫃配一套"
            )
        }
    }

    fun generateOutfitRecommendations() {
        val snapshot = uiState.value
        val apiKey = snapshot.geminiApiKey
        if (apiKey.isBlank()) {
            localState.update {
                it.copy(
                    showApiKeyDialog = true,
                    snackbarMessage = "請先設定 Gemini API Key 以產生 AI 穿搭推薦"
                )
            }
            return
        }

        viewModelScope.launch {
            localState.update {
                it.copy(isGeneratingRecommendations = true)
            }
            runCatching {
                val items = repository.getAllClothingSnapshot()
                geminiService.recommendOutfits(
                    apiKey = apiKey,
                    wardrobe = items,
                    stylePreset = snapshot.selectedStylePreset,
                    customRequest = snapshot.customStylistPrompt,
                    anchorItemId = snapshot.anchorItemId
                )
            }.onSuccess { recs ->
                localState.update {
                    it.copy(
                        isGeneratingRecommendations = false,
                        recommendations = recs,
                        snackbarMessage = "已為您挑選 ${recs.size} 套衣櫃實穿組合（極簡 JSON 低 Token 推理）"
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
