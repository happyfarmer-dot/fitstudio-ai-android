package com.tryonwardrobe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BrandCreamBg = Color(0xFFFAF6F2)
private val BrandTerracotta = Color(0xFFC86D51)
private val BrandCharcoal = Color(0xFF1C1B1F)

@Composable
fun FitStudioBrandLogo(
    size: Dp = 34.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(BrandCreamBg)
            .border(1.dp, BrandTerracotta.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Tailor Golden-Ratio Drape Arc (#C86D51)
            val arcPath = Path().apply {
                moveTo(w * 0.24f, h * 0.66f)
                cubicTo(
                    w * 0.36f, h * 0.80f,
                    w * 0.64f, h * 0.80f,
                    w * 0.76f, h * 0.66f
                )
            }
            drawPath(
                path = arcPath,
                color = BrandTerracotta,
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )

            // 2. Geometric Hanger Outline (#1C1B1F)
            val hangerPath = Path().apply {
                // Hook
                moveTo(w * 0.44f, h * 0.30f)
                cubicTo(
                    w * 0.44f, h * 0.22f,
                    w * 0.57f, h * 0.22f,
                    w * 0.56f, h * 0.31f
                )
                cubicTo(
                    w * 0.55f, h * 0.35f,
                    w * 0.50f, h * 0.37f,
                    w * 0.50f, h * 0.42f
                )
                // Shoulders triangle
                lineTo(w * 0.20f, h * 0.60f)
                lineTo(w * 0.80f, h * 0.60f)
                close()
            }
            drawPath(
                path = hangerPath,
                color = BrandCharcoal,
                style = Stroke(
                    width = w * 0.075f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Top-Right AI 4-Point Sparkle Star (✦) (#C86D51)
            val cx = w * 0.77f
            val cy = h * 0.25f
            val r = w * 0.14f
            val starPath = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(path = starPath, color = BrandTerracotta)
        }
    }
}
