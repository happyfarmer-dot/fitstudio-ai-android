package com.tryonwardrobe.app.ui.screens

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
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import com.tryonwardrobe.app.ui.WardrobeUiState
import java.io.File

private val STYLE_PRESETS = listOf(
    "日系簡約",
    "商務休閒",
    "約會穿搭",
    "美式街頭",
    "依今日氣溫推薦"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStylistScreen(
    uiState: WardrobeUiState,
    onSelectStylePreset: (String) -> Unit,
    onUpdateCustomPrompt: (String) -> Unit,
    onSetAnchorItem: (Long?) -> Unit,
    onGenerateRecommendations: () -> Unit,
    onApplyRecommendation: (GeminiWardrobeService.OutfitRecommendation) -> Unit,
    onGoToWardrobe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wardrobeById = remember(uiState.clothingItems) {
        uiState.clothingItems.associateBy { it.id }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "兩階段低額度管線：僅傳送衣櫃文字標籤 JSON 給 gemini-2.5-flash 挑選組合（0 圖片傳輸），確認套用後再生成全身寫實實穿。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.clothingItems.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Checkroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "請先匯入幾件衣物建立數位衣櫃",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = onGoToWardrobe) {
                            Text("前往數位衣櫃")
                        }
                    }
                }
            }
        } else {
            // Style / Scenario Picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. 選擇風格與穿搭情境",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        STYLE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = uiState.selectedStylePreset == preset,
                                onClick = { onSelectStylePreset(preset) },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
            }

            // Optional Anchor Item Lock
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "2. 指定必選單品（可選，一衣多穿鎖定）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = uiState.anchorItemId == null,
                                onClick = { onSetAnchorItem(null) },
                                label = { Text("不鎖定單品") }
                            )
                        }
                        items(uiState.clothingItems, key = { "anchor_${it.id}" }) { item ->
                            val isAnchored = uiState.anchorItemId == item.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAnchored) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = if (isAnchored) 2.dp else 1.dp,
                                    color = if (isAnchored) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    }
                                ),
                                modifier = Modifier
                                    .width(86.dp)
                                    .clickable {
                                        onSetAnchorItem(if (isAnchored) null else item.id)
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    AsyncImage(
                                        model = File(item.imagePath),
                                        contentDescription = item.subCategory,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(48.dp)
                                    )
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

            // Custom Prompt Input
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "3. 補充氣溫、場合或個人要求（可留空）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = uiState.customStylistPrompt,
                        onValueChange = onUpdateCustomPrompt,
                        placeholder = {
                            Text("例如：今天台北 18 度微涼，我想穿這件軍綠色外套，幫我配一套通勤穿搭")
                        },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Generate CTA
            item {
                Button(
                    onClick = onGenerateRecommendations,
                    enabled = !uiState.isGeneratingRecommendations,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (uiState.isGeneratingRecommendations) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("gemini-2.5-flash 正在挑揀衣櫃組合...")
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "產生 AI 智慧穿搭建議",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Outfit Recommendation Cards
            if (uiState.recommendations.isNotEmpty()) {
                item {
                    Text(
                        text = "為您精選的衣櫃實穿組合",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(uiState.recommendations) { rec ->
                val matchedItems = rec.itemIds.mapNotNull { wardrobeById[it] }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rec.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${matchedItems.size} 件搭配",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = rec.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(matchedItems, key = { "rec_${rec.title}_${it.id}" }) { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(74.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(66.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .padding(6.dp)
                                    ) {
                                        AsyncImage(
                                            model = File(item.imagePath),
                                            contentDescription = item.subCategory,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.subCategory,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { onApplyRecommendation(rec) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("一鍵套用並合成全身實穿")
                        }
                    }
                }
            }
        }
    }
}
