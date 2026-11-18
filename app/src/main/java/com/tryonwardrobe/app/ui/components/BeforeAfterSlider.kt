package com.tryonwardrobe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/**
 * Interactive Before/After horizontal wipe slider for comparing the user's
 * original full-body photo against the Gemini photorealistic virtual try-on result.
 */
@Composable
fun BeforeAfterSlider(
    beforeImagePath: String,
    afterImagePath: String,
    modifier: Modifier = Modifier,
    beforeLabel: String = "BEFORE 原全身照",
    afterLabel: String = "AFTER 寫實實穿"
) {
    var splitFraction by remember(beforeImagePath, afterImagePath) {
        mutableFloatStateOf(0.45f)
    }
    var containerWidthPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current

    val beforeClipShape = remember(splitFraction) {
        GenericShape { size, _ ->
            addRect(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = size.width * splitFraction,
                    bottom = size.height
                )
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1B1E))
            .onSizeChanged { size ->
                if (size.width > 0) containerWidthPx = size.width
            }
            .pointerInput(beforeImagePath, afterImagePath) {
                detectTapGestures { offset ->
                    splitFraction = (offset.x / containerWidthPx.toFloat()).coerceIn(0.04f, 0.96f)
                }
            }
            .pointerInput(beforeImagePath, afterImagePath) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = dragAmount / containerWidthPx.toFloat()
                    splitFraction = (splitFraction + delta).coerceIn(0.04f, 0.96f)
                }
            }
    ) {
        // Bottom full layer: AI Generated After Image
        AsyncImage(
            model = File(afterImagePath),
            contentDescription = afterLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top clipped layer: Original Before Photo (revealed to the left of split handle)
        AsyncImage(
            model = File(beforeImagePath),
            contentDescription = beforeLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(beforeClipShape)
        )

        // Top corner labels
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Text(
                text = beforeLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.90f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Text(
                text = afterLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // Divider vertical hairline & center draggable knob
        val handleX = (containerWidthPx * splitFraction).roundToInt()
        val lineHalfPx = with(density) { 1.dp.toPx() }.roundToInt()
        val knobSize = 42.dp
        val knobHalfPx = with(density) { (knobSize / 2).toPx() }.roundToInt()

        Box(
            modifier = Modifier
                .offset { IntOffset(x = handleX - lineHalfPx, y = 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.92f))
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(x = handleX - knobHalfPx, y = 0) }
                .size(knobSize)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
        ) {
            Canvas(modifier = Modifier.size(2.dp)) { /* subtle anchor */ }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color(0xFF141518),
                    modifier = Modifier
                        .size(18.dp)
                        .offset(x = (-7).dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF141518),
                    modifier = Modifier
                        .size(18.dp)
                        .offset(x = 7.dp)
                )
            }
        }
    }
}
