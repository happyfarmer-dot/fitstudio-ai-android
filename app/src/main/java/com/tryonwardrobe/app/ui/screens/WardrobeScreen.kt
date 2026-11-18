package com.tryonwardrobe.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import com.tryonwardrobe.app.ui.WardrobeSortOption
import com.tryonwardrobe.app.ui.WardrobeUiState
import java.io.File

private val CATEGORY_FILTERS = listOf("全部") + ClothingItem.ALL_CATEGORIES

@Composable
fun WardrobeScreen(
    uiState: WardrobeUiState,
    onSelectCategoryFilter: (String) -> Unit,
    onSelectSortOption: (WardrobeSortOption) -> Unit,
    onToggleItemSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onImportUris: (List<Uri>) -> Unit,
    onDeleteItem: (ClothingItem) -> Unit,
    onIncrementWearCount: (ClothingItem) -> Unit,
    onRetryTagging: (ClothingItem) -> Unit,
    onRetryAllFailedTagging: () -> Unit,
    onStyleWithAnchor: (ClothingItem) -> Unit,
    onDrawBlindBox: () -> Unit,
    onApplyBlindBox: (GeminiWardrobeService.OutfitRecommendation) -> Unit,
    onDismissBlindBox: () -> Unit,
    onStartTryOn: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val maxPickLimit = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            minOf(MediaStore.getPickImagesMaxLimit(), 30).coerceAtLeast(2)
        } else {
            30
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (saved && capturedUri != null) {
            onImportUris(listOf(capturedUri))
        }
    }

    fun launchGarmentCamera() {
        runCatching {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val tempFile = File(dir, "garment_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure { err ->
            pendingCameraUri = null
            onShowMessage(err.message ?: "無法開啟相機拍攝衣物")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchGarmentCamera()
        } else {
            onShowMessage("請允許相機權限以拍攝新衣物")
        }
    }

    val batchGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxPickLimit)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris)
        }
    }

    fun onShootGarmentClick() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            launchGarmentCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val failedTagCount = remember(uiState.clothingItems) {
        uiState.clothingItems.count { it.tagStatus == ClothingItem.TAG_STATUS_FAILED }
    }

    val displayedItems = remember(
        uiState.clothingItems,
        uiState.selectedCategoryFilter,
        uiState.selectedSortOption
    ) {
        val categoryFiltered = if (uiState.selectedCategoryFilter == "全部") {
            uiState.clothingItems
        } else {
            uiState.clothingItems.filter { it.category == uiState.selectedCategoryFilter }
        }
        val nowMs = System.currentTimeMillis()
        when (uiState.selectedSortOption) {
            WardrobeSortOption.NEWEST -> categoryFiltered.sortedByDescending { it.createdAt }
            WardrobeSortOption.MOST_WORN -> categoryFiltered.sortedWith(
                compareByDescending<ClothingItem> { it.wearCount }.thenByDescending { it.createdAt }
            )
            WardrobeSortOption.DORMANT_90_DAYS -> categoryFiltered
                .filter { it.isDormantOver90Days(nowMs) }
                .sortedBy { it.lastWornTimestamp ?: it.createdAt }
        }
    }

    val wardrobeById = remember(uiState.clothingItems) {
        uiState.clothingItems.associateBy { it.id }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Import Toolbar + Blind Box Random Outfit Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = ::onShootGarmentClick,
                    enabled = !uiState.isImportingGarments,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(0.95f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("拍攝入庫", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        batchGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !uiState.isImportingGarments,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.35f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("相簿匯入 (最多30件)", style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = onDrawBlindBox,
                    enabled = uiState.clothingItems.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Casino,
                        contentDescription = "隨機穿搭",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("🎲 隨機", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Stage 1 Queue Progress Bar (Semaphore(3) + 300ms Tagging Queue)
            AnimatedVisibility(visible = uiState.isImportingGarments) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val total = uiState.importProgressTotal.coerceAtLeast(1)
                        val cur = uiState.importProgressCurrent.coerceIn(0, total)
                        Text(
                            text = uiState.importStatusText.ifBlank {
                                "正在處理匯入單品 $cur / $total..."
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        LinearProgressIndicator(
                            progress = { cur.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Blind Box Random Outfit Drawer Card
            AnimatedVisibility(visible = uiState.randomBlindBoxRecommendation != null) {
                uiState.randomBlindBoxRecommendation?.let { blindRec ->
                    val items = blindRec.itemIds.mapNotNull { wardrobeById[it] }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = blindRec.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissBlindBox,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "關閉", modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                text = blindRec.rationale,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(items, key = { "blind_wardrobe_${it.id}" }) { garment ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            AsyncImage(
                                                model = File(garment.imagePath),
                                                contentDescription = garment.subCategory,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = garment.subCategory,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDrawBlindBox,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🔄 再抽一套")
                                }
                                Button(
                                    onClick = { onApplyBlindBox(blindRec) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Text("✨ 一鍵套用寫實試穿")
                                }
                            }
                        }
                    }
                }
            }

            // Failed Background Tagging Batch Retry Banner
            AnimatedVisibility(visible = failedTagCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "有 $failedTagCount 件單品影像已存妥，待同步 AI 標籤",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onRetryAllFailedTagging) {
                            Text("全部重試", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 10-Category Filter Chips LazyRow
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CATEGORY_FILTERS) { category ->
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == category,
                        onClick = { onSelectCategoryFilter(category) },
                        label = { Text(category) }
                    )
                }
            }

            // Wear Count & Dormant 90-Day Sort Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "排序：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(WardrobeSortOption.entries) { sortOpt ->
                    FilterChip(
                        selected = uiState.selectedSortOption == sortOpt,
                        onClick = { onSelectSortOption(sortOpt) },
                        label = {
                            Text(
                                text = sortOpt.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checkroom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = when {
                                    uiState.clothingItems.isEmpty() -> "尚無數位衣櫃單品"
                                    uiState.selectedSortOption == WardrobeSortOption.DORMANT_90_DAYS -> "太棒了！目前沒有超過 90 天未穿的沉睡單品"
                                    else -> "此分類目前沒有單品"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "支援 10 大服飾與配件分類、一次最多 30 張批次匯入（Semaphore 防爆佇列），並自動追蹤每件單品的穿著次數。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.clothingItems.isEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(onClick = ::onShootGarmentClick) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("立刻拍攝第一件衣物")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = if (uiState.selectedClothingIds.isNotEmpty()) 104.dp else 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        val isSelected = item.id in uiState.selectedClothingIds
                        GarmentCard(
                            item = item,
                            isSelected = isSelected,
                            onToggleSelect = { onToggleItemSelection(item.id) },
                            onDelete = { onDeleteItem(item) },
                            onIncrementWearCount = { onIncrementWearCount(item) },
                            onRetryTagging = { onRetryTagging(item) },
                            onStyleWithAnchor = { onStyleWithAnchor(item) }
                        )
                    }
                }
            }
        }

        // Bottom Multi-Item Try-On Action Bar
        AnimatedVisibility(
            visible = uiState.selectedClothingIds.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "已勾選 ${uiState.selectedClothingIds.size} 件搭配單品",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "支援服飾、鞋履與帽子/眼鏡/包袋同時換穿",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                    }
                    TextButton(
                        onClick = onClearSelection,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    ) {
                        Text("清除")
                    }
                    Button(
                        onClick = onStartTryOn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("全身實穿")
                    }
                }
            }
        }
    }
}

@Composable
private fun GarmentCard(
    item: ClothingItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
    onIncrementWearCount: () -> Unit,
    onRetryTagging: () -> Unit,
    onStyleWithAnchor: () -> Unit
) {
    val isPending = item.tagStatus == ClothingItem.TAG_STATUS_PENDING
    val isFailed = item.tagStatus == ClothingItem.TAG_STATUS_FAILED
    val isDormant = remember(item.lastWornTimestamp, item.createdAt) {
        item.isDormantOver90Days()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isSelected) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.secondary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                AsyncImage(
                    model = File(item.imagePath),
                    contentDescription = item.subCategory,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )

                // Selection Indicator Top-Left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                Color.White.copy(alpha = 0.88f)
                            }
                        )
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = if (isSelected) "已勾選" else "點擊勾選",
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Button Top-Right
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "刪除單品",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Category Pill Bottom-Left
                Surface(
                    color = if (isFailed) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isPending) "AI 分析中" else item.category,
                            color = if (isFailed) Color.White else MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Wear Count Badge Bottom-Right
                Surface(
                    color = if (isDormant) {
                        Color(0xFF795548).copy(alpha = 0.90f)
                    } else {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.90f)
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isDormant) "90天+未穿 · ${item.wearCount}次" else "穿過 ${item.wearCount} 次",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.subCategory,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                when {
                    isFailed -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onRetryTagging)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "重試打標",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "待同步標籤（點擊重試）",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 1
                                    )
                                }
                                if (!item.tagError.isNullOrBlank()) {
                                    Text(
                                        text = item.tagError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    isPending -> {
                        Text(
                            text = "影像已存妥，佇列同步標籤中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        Text(
                            text = "${item.color} · ${item.fit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val mergedTags = (item.styleTags.take(2) + item.season.take(2)).distinct()
                        if (mergedTags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                mergedTags.take(3).forEach { tag ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons: "+1 今日穿著" & "AI 配一套"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onIncrementWearCount)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "1 今日穿著",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .border(
                                0.5.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onStyleWithAnchor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "AI 配一套",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
