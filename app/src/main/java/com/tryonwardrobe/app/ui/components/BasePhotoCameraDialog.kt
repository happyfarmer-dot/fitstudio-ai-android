package com.tryonwardrobe.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

/**
 * Full-screen CameraX modal for capturing a standardized full-body base reference photo.
 * Features a silhouette framing overlay, live accelerometer spirit level, 3s self-timer,
 * front/back lens switching, and direct gallery fallback.
 */
@Composable
fun BasePhotoCameraDialog(
    onDismiss: () -> Unit,
    onCapturedFile: (File) -> Unit,
    onPickedGalleryUri: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickedGalleryUri(uri)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var useSelfTimer by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var isTakingPicture by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Live roll angle from accelerometer for horizontal/vertical balance guide
    var tiltDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                // Upright portrait: x ≈ 0, y ≈ 9.8 -> roll error in approximate degrees
                val rawDeg = (x / (y.coerceAtLeast(1f))) * 45f
                tiltDegrees = tiltDegrees * 0.75f + rawDeg * 0.25f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    fun executeCapture() {
        if (isTakingPicture) return
        isTakingPicture = true
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val outputFile = File(dir, "base_body_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isTakingPicture = false
                    onCapturedFile(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    isTakingPicture = false
                    cameraError = exception.message ?: "拍照失敗，請重試"
                }
            }
        )
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L)
            if (countdown == 1) {
                countdown = 0
                executeCapture()
            } else {
                countdown -= 1
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                val previewView = remember {
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }

                DisposableEffect(lensFacing, lifecycleOwner) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    var boundProvider: ProcessCameraProvider? = null

                    cameraProviderFuture.addListener({
                        runCatching {
                            val provider = cameraProviderFuture.get()
                            boundProvider = provider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val selector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                            )
                        }.onFailure { err ->
                            cameraError = "無法啟動相機：${err.message}"
                        }
                    }, ContextCompat.getMainExecutor(context))

                    onDispose {
                        boundProvider?.unbindAll()
                    }
                }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Human Full-Body Silhouette + Spirit Level Canvas Overlay
                val isLevel = abs(tiltDegrees) < 3.5f
                val guideColor = if (isLevel) Color(0xFF7CE8A5) else Color.White.copy(alpha = 0.72f)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Subtle dashed silhouette bounding box & human outline guide
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                    val cx = w / 2f
                    val topY = h * 0.14f
                    val bottomY = h * 0.80f
                    val bodyH = bottomY - topY

                    // Head oval
                    val headRadiusW = w * 0.09f
                    val headRadiusH = bodyH * 0.095f
                    drawOval(
                        color = guideColor,
                        topLeft = Offset(cx - headRadiusW, topY),
                        size = Size(headRadiusW * 2f, headRadiusH * 2f),
                        style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect)
                    )

                    // Shoulders, torso, and straight standing legs guide
                    val silhouette = Path().apply {
                        val shoulderY = topY + headRadiusH * 2.25f
                        val waistY = topY + bodyH * 0.45f
                        val hipY = topY + bodyH * 0.56f
                        val shoulderHalf = w * 0.21f
                        val waistHalf = w * 0.13f
                        val ankleHalf = w * 0.14f

                        moveTo(cx - shoulderHalf, shoulderY)
                        lineTo(cx + shoulderHalf, shoulderY)
                        lineTo(cx + waistHalf, waistY)
                        lineTo(cx + shoulderHalf * 0.85f, hipY)
                        lineTo(cx + ankleHalf, bottomY)
                        moveTo(cx - shoulderHalf, shoulderY)
                        lineTo(cx - waistHalf, waistY)
                        lineTo(cx - shoulderHalf * 0.85f, hipY)
                        lineTo(cx - ankleHalf, bottomY)
                    }
                    drawPath(
                        path = silhouette,
                        color = guideColor,
                        style = Stroke(width = 2.5.dp.toPx(), pathEffect = dashEffect)
                    )

                    // Spirit level horizontal bar near center-bottom
                    val levelY = h * 0.83f
                    val halfSpan = w * 0.20f
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(cx - halfSpan, levelY),
                        end = Offset(cx + halfSpan, levelY),
                        strokeWidth = 2.dp.toPx()
                    )
                    val clampedRatio = (tiltDegrees / 25f).coerceIn(-1f, 1f)
                    drawCircle(
                        color = if (isLevel) Color(0xFF7CE8A5) else Color(0xFFFFB74D),
                        radius = 6.dp.toPx(),
                        center = Offset(cx + clampedRatio * halfSpan, levelY)
                    )
                }

                // Top status & close bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "關閉")
                    }

                    Surface(
                        color = if (isLevel) {
                            Color(0xFF1B5E20).copy(alpha = 0.82f)
                        } else {
                            Color.Black.copy(alpha = 0.68f)
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = if (isLevel) {
                                "水平已對齊 • 請將全身置於輪廓框內"
                            } else {
                                "水平校正提示 • 請直立握持手機對齊全身"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.FlipCameraAndroid, contentDescription = "切換前後鏡頭")
                    }
                }

                if (countdown > 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$countdown",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                cameraError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Bottom Controls: Gallery Pick | Shutter | 3s Timer Toggle
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = "從相簿挑選")
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(enabled = !isTakingPicture && countdown == 0) {
                                if (useSelfTimer) {
                                    countdown = 3
                                } else {
                                    executeCapture()
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "拍攝全身照",
                                tint = Color(0xFF141518),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { useSelfTimer = !useSelfTimer },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (useSelfTimer) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            },
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = "3秒倒數自拍",
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (useSelfTimer) "3s" else "關",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            } else {
                // Camera permission fallback UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "需要相機權限以拍攝全身基準照",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "您也可以直接從手機相簿挑選現有的全身直立照片。",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("改從相簿選取")
                        }
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("授權相機")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onDismiss) {
                        Text("返回", color = Color.White)
                    }
                }
            }
        }
    }
}
