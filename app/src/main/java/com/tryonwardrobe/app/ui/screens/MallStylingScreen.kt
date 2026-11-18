package com.tryonwardrobe.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tryonwardrobe.app.data.catalog.MallBrandItem
import com.tryonwardrobe.app.data.catalog.MallCatalog
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import com.tryonwardrobe.app.ui.WardrobeUiState
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MallStylingScreen(
    uiState: WardrobeUiState,
    onSelectBrand: (String) -> Unit,
    onSetMixWithWardrobe: (Boolean) -> Unit,
    onSelectStylePreset: (String) -> Unit,
    onGenerateMallRecommendations: () -> Unit,
    onTryOnMallRecommendation: (GeminiWardrobeService.MallOutfitRecommendation) -> Unit,
    onTryOnSingleMallItem: (MallBrandItem) -> Unit,
    onImportScreenshotForTryOn: (List<Uri>) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val screenshotLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportScreenshotForTryOn(uris)
        }
    }

    fun openOfficialUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure {
            onShowMessage("無法開啟瀏覽器連結")
        }
    }

    val filteredCatalog = remember(uiState.selectedMallBrand) {
        MallCatalog.filterByBrand(uiState.selectedMallBrand)
    }

    val wardrobeById = remember(uiState.clothingItems) {
        uiState.clothingItems.associateBy { it.id }
    }

    val mallById = remember {
        MallCatalog.EVERGREEN_ITEMS.associateBy { it.id }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Header Banner + Method B Screenshot Import CTA
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "UNIQLO / GU / 無印良品 MUJI 線上商城穿搭",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• 方式 A：由 AI 根據您的個人色彩（${uiState.personalProfile.personalColorSeason.ifBlank { "未設定" }}）與身形挑選品牌經典單品，直接將規格送入生圖模型試穿。\n• 方式 B：從官網複製／截圖商品照，經本地 ML Kit 自動去背後直接存入衣櫃並套用試穿。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    FilledTonalButton(
                        onClick = {
                            screenshotLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("📷 匯入官網截圖／商品圖去背試穿（方式 B）", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 1. Brand Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1. 篩選品牌",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MallCatalog.BRAND_FILTERS) { brand ->
                        FilterChip(
                            selected = uiState.selectedMallBrand == brand,
                            onClick = { onSelectBrand(brand) },
                            label = { Text(brand) }
                        )
                    }
                }
            }
        }

        // 2. Mix Mode Toggle (Pure Mall vs Hybrid with My Wardrobe)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2. 搭配模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.mallMixWithWardrobe,
                        onClick = { onSetMixWithWardrobe(true) },
                        label = { Text("與我的衣櫃單品混搭") }
                    )
                    FilterChip(
                        selected = !uiState.mallMixWithWardrobe,
                        onClick = { onSetMixWithWardrobe(false) },
                        label = { Text("全套商城單品搭配") }
                    )
                }
            }
        }

        // 3. Style Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "3. 目標穿搭風格",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EXPANDED_STYLE_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = uiState.selectedStylePreset == preset,
                            onClick = { onSelectStylePreset(preset) },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        }

        // 4. Generate AI Mall Outfit Button
        item {
            Button(
                onClick = onGenerateMallRecommendations,
                enabled = !uiState.isGeneratingMallRecommendations,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (uiState.isGeneratingMallRecommendations) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("${uiState.taggingModel} 正在規劃商城穿搭...")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("✨ AI 推薦商城穿搭提案", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. AI Mall Recommendation Cards
        if (uiState.mallRecommendations.isNotEmpty()) {
            item {
                Text(
                    text = "AI 精選商城穿搭提案",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.mallRecommendations) { rec ->
                val mallItems = rec.mallItemIds.mapNotNull { mallById[it] }
                val wardrobeItems = rec.wardrobeItemIds.mapNotNull { wardrobeById[it] }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rec.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Mall Items in Recommendation
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            mallItems.forEach { mItem ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "[${mItem.brand}] ${mItem.name}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${mItem.category} · ${mItem.color} · ${mItem.fit} · 參考價 ${mItem.priceHintTwd}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            onClick = { openOfficialUrl(mItem.buildOfficialSearchUrl()) }
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(3.dp))
                                            Text("官網搜尋", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Mixed Wardrobe Items if any
                        if (wardrobeItems.isNotEmpty()) {
                            Text(
                                text = "搭配您的現有衣櫃單品：",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(wardrobeItems, key = { "mall_mix_${rec.title}_${it.id}" }) { wItem ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(6.dp)
                                        ) {
                                            AsyncImage(
                                                model = File(wItem.imagePath),
                                                contentDescription = wItem.subCategory,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = wItem.subCategory,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onTryOnMallRecommendation(rec) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("方式 A：一鍵送入全身照寫實試穿")
                        }
                    }
                }
            }
        }

        // 6. Evergreen Brand Catalog List
        item {
            Text(
                text = "經典常青單品庫（${uiState.selectedMallBrand}，共 ${filteredCatalog.size} 款）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(filteredCatalog, key = { it.id }) { mallItem ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${mallItem.brand} · ${mallItem.category}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = mallItem.priceHintTwd,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = mallItem.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "顏色：${mallItem.color} ｜ 版型：${mallItem.fit}\n材質：${mallItem.material}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openOfficialUrl(mallItem.buildOfficialSearchUrl()) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("台灣官網搜尋", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = { onTryOnSingleMallItem(mallItem) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("直接規格試穿", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
