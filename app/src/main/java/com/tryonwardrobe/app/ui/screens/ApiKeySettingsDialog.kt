package com.tryonwardrobe.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tryonwardrobe.app.data.preferences.SettingsDataStore
import com.tryonwardrobe.app.service.ai.GeminiWardrobeService
import com.tryonwardrobe.app.ui.ApiUsageSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ApiKeySettingsDialog(
    initialApiKey: String,
    initialTaggingModel: String,
    initialTryOnModel: String,
    isTestingConnection: Boolean,
    connectionTestResult: GeminiWardrobeService.ConnectionTestResult?,
    usageSummary: ApiUsageSummary,
    isRunningBackupOrRestore: Boolean,
    onTestConnection: (apiKey: String, taggingModelId: String, tryOnModelId: String) -> Unit,
    onClearTestResult: () -> Unit,
    onResetUsageStats: () -> Unit,
    onExportBackupZip: (Uri) -> Unit,
    onImportBackupZip: (Uri, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, taggingModelId: String, tryOnModelId: String) -> Unit
) {
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var selectedTaggingModel by remember(initialTaggingModel) { mutableStateOf(initialTaggingModel) }
    var selectedTryOnModel by remember(initialTryOnModel) { mutableStateOf(initialTryOnModel) }
    var showKey by remember { mutableStateOf(false) }
    var overwriteOnRestore by remember { mutableStateOf(false) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            onExportBackupZip(uri)
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackupZip(uri, overwriteOnRestore)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gemini API、用量與備份設定",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "金鑰僅安全儲存於本機 App 私有 DataStore，所有圖片壓縮後直接與 Google Gemini v1beta API 通訊。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        onClearTestResult()
                    },
                    label = { Text("Gemini API Key (AIza...)") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隱藏" else "顯示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Connection Test Button
                FilledTonalButton(
                    onClick = {
                        onTestConnection(apiKey, selectedTaggingModel, selectedTryOnModel)
                    },
                    enabled = !isTestingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在測試生成端點連線...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.WifiFind,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("測試 API Key 與模型連線", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Inline Connection Test Result Card
                AnimatedVisibility(visible = connectionTestResult != null) {
                    connectionTestResult?.let { result ->
                        val bgColor = if (result.success) {
                            Color(0xFFE8F5E9)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                        val fgColor = if (result.success) {
                            Color(0xFF1B5E20)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }
                        val borderColor = if (result.success) {
                            Color(0xFF66BB6A)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        }

                        Surface(
                            color = bgColor,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (result.success) {
                                        Icons.Rounded.CheckCircle
                                    } else {
                                        Icons.Rounded.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = fgColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (result.success) {
                                            "連線測試通過${result.latencyMs?.let { "（總延遲 ${it} ms）" }.orEmpty()}"
                                        } else {
                                            "連線失敗${result.httpCode?.let { "（HTTP $it）" }.orEmpty()}"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = fgColor
                                    )
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = fgColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Selector 1: Tagging & Styling Logic Model (JSON Mode)
                Text(
                    text = "1. 衣物打標與智慧配對模型（JSON Mode）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SettingsDataStore.TAGGING_MODEL_OPTIONS.forEach { option ->
                    ModelChoiceOption(
                        title = option.displayName,
                        subtitle = option.description,
                        selected = selectedTaggingModel == option.id,
                        onSelect = {
                            selectedTaggingModel = option.id
                            onClearTestResult()
                        }
                    )
                }

                // Selector 2: Photorealistic Virtual Try-On Model (Multi-Image)
                Text(
                    text = "2. 寫實換裝合成模型（Multi-Image）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SettingsDataStore.TRY_ON_MODEL_OPTIONS.forEach { option ->
                    ModelChoiceOption(
                        title = option.displayName,
                        subtitle = option.description,
                        selected = selectedTryOnModel == option.id,
                        onSelect = {
                            selectedTryOnModel = option.id
                            onClearTestResult()
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 3. API Token Usage & Estimated Cost Dashboard Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "📊 API 用量與預估費用儀表板",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = onResetUsageStats,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("重設統計", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Text(
                            text = "• 今日統計：呼叫 ${usageSummary.todayCalls} 次 ｜ ${usageSummary.todayTokens} Tokens ｜ 生圖 ${usageSummary.todayImages} 張",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• 本月累計：呼叫 ${usageSummary.monthCalls} 次 ｜ ${usageSummary.monthTokens} Tokens ｜ 生圖 ${usageSummary.monthImages} 張",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "• 本月預估參考費用：約 $%.4f USD（約 NT$%.1f TWD）",
                                usageSummary.estimatedMonthUsd,
                                usageSummary.estimatedMonthTwd
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4. One-Click ZIP Backup & Restore Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📦 衣櫃完整資料庫一鍵打包備份與還原 (.zip)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "將單品清單、個人色彩身形檔案 (backup_manifest.json) 與所有去背圖、全身基準照打包為單一 ZIP 檔。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "還原模式：",
                                style = MaterialTheme.typography.labelSmall
                            )
                            FilterChip(
                                selected = !overwriteOnRestore,
                                onClick = { overwriteOnRestore = false },
                                label = { Text("合併現有衣櫃", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = overwriteOnRestore,
                                onClick = { overwriteOnRestore = true },
                                label = { Text("覆蓋還原", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportZipLauncher.launch("FitStudioAI_Backup_$dateStr.zip")
                                },
                                enabled = !isRunningBackupOrRestore,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("匯出備份 (.zip)", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                                enabled = !isRunningBackupOrRestore,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("從備份還原", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKey, selectedTaggingModel, selectedTryOnModel) }
            ) {
                Text("儲存設定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun ModelChoiceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
