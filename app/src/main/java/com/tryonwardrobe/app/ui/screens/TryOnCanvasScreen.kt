package com.tryonwardrobe.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.tryonwardrobe.app.data.local.ClothingItem
import com.tryonwardrobe.app.data.local.PersonPhoto
import com.tryonwardrobe.app.data.local.TryOnCache
import com.tryonwardrobe.app.ui.WardrobeUiState
import com.tryonwardrobe.app.ui.components.BeforeAfterSlider
import java.io.File

@Composable
fun TryOnCanvasScreen(
    uiState: WardrobeUiState,
    onOpenBaseCameraDialog: () -> Unit,
    onSelectPersonPhoto: (PersonPhoto) -> Unit,
    onDeletePersonPhoto: (PersonPhoto) -> Unit,
    onToggleClothingSelection: (Long) -> Unit,
    onRunTryOn: (forceRegenerate: Boolean) -> Unit,
    onOpenCachedEntry: (TryOnCache) -> Unit,
    onSaveToGallery: () -> Unit,
    onGoToWardrobe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showWardrobeQuickPicker by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onSaveToGallery()
    }

    fun triggerSaveToGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onSaveToGallery()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onSaveToGallery()
            } else {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val selectedClothingItems = remember(uiState.clothingItems, uiState.selectedClothingIds) {
        uiState.clothingItems.filter { it.id in uiState.selectedClothingIds }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Multi-Base Full-Body Photo Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "全身基準照（可多組切換）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "合成時將嚴格保留臉部五官、身形與原背景",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onOpenBaseCameraDialog,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("新增全身照")
                }
            }

            if (uiState.personPhotos.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable(onClick = onOpenBaseCameraDialog)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "尚未設定預設全身模特兒底圖",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "點擊開啟 CameraX 拍攝全身輪廓引導照，或從相簿匯入。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.personPhotos, key = { it.id }) { person ->
                        val isActive = uiState.activePersonPhoto?.id == person.id
                        PersonPhotoThumbnail(
                            person = person,
                            isActive = isActive,
                            onSelect = { onSelectPersonPhoto(person) },
                            onDelete = { onDeletePersonPhoto(person) }
                        )
                    }
                }
            }
        }

        // 2. Selected Clothing Items Strip + Quick Picker Toggle
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "待試穿單品（已選 ${selectedClothingItems.size} 件）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (showWardrobeQuickPicker) "收合衣櫃" else "+ 挑選／增減衣物",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (uiState.clothingItems.isEmpty()) {
                                onGoToWardrobe()
                            } else {
                                showWardrobeQuickPicker = !showWardrobeQuickPicker
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (selectedClothingItems.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedClothingItems, key = { it.id }) { item ->
                        SelectedGarmentChip(
                            item = item,
                            onRemove = { onToggleClothingSelection(item.id) }
                        )
                    }
                }
            } else {
                Text(
                    text = "可單選一件衣物，或同時勾選「上衣 + 下著 + 外套」進行疊穿實穿。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (showWardrobeQuickPicker && uiState.clothingItems.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.clothingItems, key = { "pick_${it.id}" }) { item ->
                        val picked = item.id in uiState.selectedClothingIds
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (picked) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (picked) 2.dp else 1.dp,
                                color = if (picked) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier
                                .width(78.dp)
                                .clickable { onToggleClothingSelection(item.id) }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    AsyncImage(
                                        model = File(item.imagePath),
                                        contentDescription = item.subCategory,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (picked) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.subCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Center Photorealistic Try-On Stage (Before / After Slider)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141518)),
            contentAlignment = Alignment.Center
        ) {
            val beforePath = uiState.beforePhotoPath
            val afterPath = uiState.afterResultPath

            when {
                beforePath != null && afterPath != null -> {
                    BeforeAfterSlider(
                        beforeImagePath = beforePath,
                        afterImagePath = afterPath,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                uiState.activePersonPhoto != null -> {
                    AsyncImage(
                        model = File(uiState.activePersonPhoto.imagePath),
                        contentDescription = "預設全身基準照",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "選好上方衣物後，點擊「套用寫實實穿」即可預覽換裝",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "請先設定一張全身基準照",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(onClick = onOpenBaseCameraDialog) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("開啟全身照拍攝引導")
                        }
                    }
                }
            }

            // Cache Hit / Active Model Status Pill (top-bottom overlay when result exists)
            if (afterPath != null && !uiState.isGeneratingTryOn) {
                Surface(
                    color = if (uiState.isResultFromCache) {
                        Color(0xFF1B5E20).copy(alpha = 0.85f)
                    } else {
                        Color.Black.copy(alpha = 0.72f)
                    },
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (uiState.isResultFromCache) {
                            Icon(
                                imageVector = Icons.Rounded.Cached,
                                contentDescription = null,
                                tint = Color(0xFFA5D6A7),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = if (uiState.isResultFromCache) {
                                "本地快取命中 • 免扣 Gemini 額度"
                            } else {
                                "左右拖曳拉桿對比 • ${uiState.tryOnModel}"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Loading Scrim
            if (uiState.isGeneratingTryOn) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.68f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = uiState.tryOnStatusText.ifBlank { "Gemini 寫實實穿合成中..." },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Primary Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onRunTryOn(false) },
                enabled = !uiState.isGeneratingTryOn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "套用寫實實穿（優先讀取本地快取）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.afterResultPath != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRunTryOn(true) },
                        enabled = !uiState.isGeneratingTryOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新生成")
                    }

                    FilledTonalButton(
                        onClick = ::triggerSaveToGallery,
                        enabled = !uiState.isGeneratingTryOn,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("儲存至相簿")
                    }
                }
            }
        }

        // 5. Recent Local Cache History
        if (uiState.recentCaches.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "歷史實穿快取紀錄（秒開 0 額度）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.recentCaches, key = { it.cacheKey }) { cache ->
                        val isCurrent = uiState.afterResultPath == cache.resultImagePath
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            border = if (isCurrent) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            },
                            modifier = Modifier
                                .width(84.dp)
                                .clickable { onOpenCachedEntry(cache) }
                        ) {
                            Column {
                                AsyncImage(
                                    model = File(cache.resultImagePath),
                                    contentDescription = "快取試穿結果",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                )
                                Text(
                                    text = "${cache.clothingIds.size} 件組合",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PersonPhotoThumbnail(
    person: PersonPhoto,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.secondary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
        modifier = Modifier
            .width(74.dp)
            .clickable(onClick = onSelect)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                AsyncImage(
                    model = File(person.imagePath),
                    contentDescription = person.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "刪除全身照",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "預設",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = person.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SelectedGarmentChip(
    item: ClothingItem,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
        ) {
            AsyncImage(
                model = File(item.imagePath),
                contentDescription = item.subCategory,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = item.subCategory,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "${item.category} · ${item.color}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "移除此件",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
