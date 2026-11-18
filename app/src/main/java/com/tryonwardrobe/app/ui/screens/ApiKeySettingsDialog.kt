package com.tryonwardrobe.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun ApiKeySettingsDialog(
    initialApiKey: String,
    initialTaggingModel: String,
    initialTryOnModel: String,
    isTestingConnection: Boolean,
    connectionTestResult: GeminiWardrobeService.ConnectionTestResult?,
    onTestConnection: (apiKey: String, taggingModelId: String, tryOnModelId: String) -> Unit,
    onClearTestResult: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, taggingModelId: String, tryOnModelId: String) -> Unit
) {
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var selectedTaggingModel by remember(initialTaggingModel) { mutableStateOf(initialTaggingModel) }
    var selectedTryOnModel by remember(initialTryOnModel) { mutableStateOf(initialTryOnModel) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Gemini API 與模型設定",
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

                Spacer(Modifier.height(2.dp))

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

                Spacer(Modifier.height(4.dp))

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
                Text("取消")
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
