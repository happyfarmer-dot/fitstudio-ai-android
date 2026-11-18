package com.tryonwardrobe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryonwardrobe.app.ui.AppTab
import com.tryonwardrobe.app.ui.MainViewModel
import com.tryonwardrobe.app.ui.components.BasePhotoCameraDialog
import com.tryonwardrobe.app.ui.screens.AiStylistScreen
import com.tryonwardrobe.app.ui.screens.ApiKeySettingsDialog
import com.tryonwardrobe.app.ui.screens.TryOnCanvasScreen
import com.tryonwardrobe.app.ui.screens.WardrobeScreen
import com.tryonwardrobe.app.ui.theme.FitStudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FitStudioTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(uiState.snackbarMessage) {
                    val message = uiState.snackbarMessage
                    if (!message.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(message)
                        viewModel.clearSnackbar()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            ),
                            title = {
                                Column {
                                    Text(
                                        text = "FitStudio AI",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = uiState.activePersonPhoto?.let { "模特兒：${it.label}" }
                                            ?: "點擊右側人物圖示設定全身基準照",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            actions = {
                                Surface(
                                    onClick = { viewModel.setBaseCameraDialogVisible(true) },
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = "拍攝全身基準照",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "全身照 (${uiState.personPhotos.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.setApiKeyDialogVisible(true) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Gemini API 與模型設定",
                                        tint = if (uiState.geminiApiKey.isBlank()) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        }
                                    )
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.WARDROBE,
                                onClick = { viewModel.selectTab(AppTab.WARDROBE) },
                                icon = { Icon(Icons.Rounded.Checkroom, contentDescription = null) },
                                label = { Text(AppTab.WARDROBE.label) }
                            )
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.TRY_ON,
                                onClick = { viewModel.selectTab(AppTab.TRY_ON) },
                                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                                label = { Text(AppTab.TRY_ON.label) }
                            )
                            NavigationBarItem(
                                selected = uiState.selectedTab == AppTab.STYLIST,
                                onClick = { viewModel.selectTab(AppTab.STYLIST) },
                                icon = { Icon(Icons.Rounded.Style, contentDescription = null) },
                                label = { Text(AppTab.STYLIST.label) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.selectedTab) {
                            AppTab.WARDROBE -> WardrobeScreen(
                                uiState = uiState,
                                onSelectCategoryFilter = viewModel::selectCategoryFilter,
                                onToggleItemSelection = viewModel::toggleClothingSelection,
                                onClearSelection = viewModel::clearSelectedClothing,
                                onImportUris = viewModel::ingestClothingUris,
                                onDeleteItem = viewModel::deleteClothingItem,
                                onStyleWithAnchor = viewModel::jumpToStylistWithAnchor,
                                onStartTryOn = viewModel::startTryOnWithSelectedFromWardrobe,
                                onShowMessage = viewModel::showMessage
                            )

                            AppTab.TRY_ON -> TryOnCanvasScreen(
                                uiState = uiState,
                                onOpenBaseCameraDialog = { viewModel.setBaseCameraDialogVisible(true) },
                                onSelectPersonPhoto = viewModel::setActivePersonPhoto,
                                onDeletePersonPhoto = viewModel::deletePersonPhoto,
                                onToggleClothingSelection = viewModel::toggleClothingSelection,
                                onRunTryOn = { force -> viewModel.runVirtualTryOn(forceRegenerate = force) },
                                onOpenCachedEntry = viewModel::openCachedEntry,
                                onSaveToGallery = viewModel::saveCurrentResultToGallery,
                                onGoToWardrobe = { viewModel.selectTab(AppTab.WARDROBE) }
                            )

                            AppTab.STYLIST -> AiStylistScreen(
                                uiState = uiState,
                                onSelectStylePreset = viewModel::selectStylePreset,
                                onUpdateCustomPrompt = viewModel::updateCustomStylistPrompt,
                                onSetAnchorItem = viewModel::setAnchorItem,
                                onGenerateRecommendations = viewModel::generateOutfitRecommendations,
                                onApplyRecommendation = viewModel::applyRecommendationAndTryOn,
                                onGoToWardrobe = { viewModel.selectTab(AppTab.WARDROBE) }
                            )
                        }
                    }
                }

                if (uiState.showBaseCameraDialog) {
                    BasePhotoCameraDialog(
                        onDismiss = { viewModel.setBaseCameraDialogVisible(false) },
                        onCapturedFile = { file -> viewModel.addBasePersonPhotoFromFile(file) },
                        onPickedGalleryUri = { uri -> viewModel.addBasePersonPhotoFromUri(uri) }
                    )
                }

                if (uiState.showApiKeyDialog) {
                    ApiKeySettingsDialog(
                        initialApiKey = uiState.geminiApiKey,
                        initialModel = uiState.tryOnModel,
                        onDismiss = { viewModel.setApiKeyDialogVisible(false) },
                        onSave = { key, model -> viewModel.saveSettings(key, model) }
                    )
                }
            }
        }
    }
}
