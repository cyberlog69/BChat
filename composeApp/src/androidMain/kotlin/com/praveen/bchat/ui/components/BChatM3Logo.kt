package com.praveen.bchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.praveen.bchat.ui.theme.BlueElectric
import com.praveen.bchat.ui.theme.CyanNeon
import com.praveen.bchat.ui.theme.PurpleNeon

enum class LogoSize(val dp: Dp, val cornerDp: Dp) {
    Small(32.dp, 8.dp),
    Medium(52.dp, 14.dp),
    Large(88.dp, 22.dp)
}

/**
 * Modern Material 3 Brand Logo for BChat.
 * Visualizes a stylized 'B' monogram interwoven with an active P2P mesh network,
 * satellite peer nodes, and turbo data transmission beam.
 */
@Composable
fun BChatM3Logo(
    modifier: Modifier = Modifier,
    size: LogoSize = LogoSize.Medium,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "M3LogoPulse")
    val pulseGlow by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "M3LogoGlow"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            CyanNeon.copy(alpha = pulseGlow),
            BlueElectric,
            PurpleNeon
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .shadow(
                elevation = if (size == LogoSize.Large) 12.dp else 4.dp,
                shape = RoundedCornerShape(size.cornerDp),
                ambientColor = CyanNeon.copy(alpha = 0.3f),
                spotColor = BlueElectric.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(size.cornerDp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = if (size == LogoSize.Small) 1.5.dp else 2.dp,
                brush = gradientBrush,
                shape = RoundedCornerShape(size.cornerDp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(size.dp * 0.16f)
        ) {
            val w = this.size.width
            val h = this.size.height

            val strokeWidth = (w * 0.11f).coerceAtLeast(2.5f)
            val spineX = w * 0.22f
            val spineTop = h * 0.14f
            val spineBottom = h * 0.86f
            val midY = h * 0.50f

            // 1. Stylized "B" - Vertical Spine (Neon Cyan)
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(CyanNeon, BlueElectric),
                    startY = spineTop,
                    endY = spineBottom
                ),
                start = Offset(spineX, spineTop),
                end = Offset(spineX, spineBottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // 2. Stylized "B" - Upper Lobe (Arc curving right to Cyan/Blue)
            val upperLobePath = Path().apply {
                moveTo(spineX, spineTop)
                cubicTo(
                    w * 0.76f, spineTop,
                    w * 0.76f, midY,
                    spineX, midY
                )
            }
            drawPath(
                path = upperLobePath,
                brush = Brush.horizontalGradient(listOf(CyanNeon, BlueElectric)),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. Stylized "B" - Lower Lobe (Arc curving right to Purple Neon)
            val lowerLobePath = Path().apply {
                moveTo(spineX, midY)
                cubicTo(
                    w * 0.86f, midY,
                    w * 0.86f, spineBottom,
                    spineX, spineBottom
                )
            }
            drawPath(
                path = lowerLobePath,
                brush = Brush.horizontalGradient(listOf(BlueElectric, PurpleNeon)),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 4. P2P Turbo Transmission Beam (Zig-Zag Lightning connecting nodes)
            val beamPath = Path().apply {
                moveTo(spineX, midY)
                lineTo(w * 0.44f, h * 0.36f)
                lineTo(w * 0.40f, h * 0.64f)
                lineTo(w * 0.65f, midY)
            }
            drawPath(
                path = beamPath,
                color = Color.White.copy(alpha = 0.95f),
                style = Stroke(
                    width = (w * 0.07f).coerceAtLeast(1.8f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 5. Glowing Peer Radio Satellite Nodes
            val nodeRadius = (w * 0.08f).coerceAtLeast(2.5f)
            // Node A: On the spine junction
            drawCircle(
                color = Color.White,
                radius = nodeRadius * 0.9f,
                center = Offset(spineX, midY)
            )

            // Node B: Upper lobe satellite
            drawCircle(
                color = CyanNeon,
                radius = nodeRadius,
                center = Offset(w * 0.64f, h * 0.32f)
            )
            drawCircle(
                color = Color.White,
                radius = nodeRadius * 0.5f,
                center = Offset(w * 0.64f, h * 0.32f)
            )

            // Node C: Lower lobe satellite
            drawCircle(
                color = PurpleNeon,
                radius = nodeRadius,
                center = Offset(w * 0.70f, h * 0.68f)
            )
            drawCircle(
                color = Color.White,
                radius = nodeRadius * 0.5f,
                center = Offset(w * 0.70f, h * 0.68f)
            )

            // 6. Wireless Broadcast Wave (Top-right corner)
            drawArc(
                color = CyanNeon.copy(alpha = 0.8f * pulseGlow),
                startAngle = 270f,
                sweepAngle = 75f,
                useCenter = false,
                topLeft = Offset(w * 0.55f, h * 0.05f),
                size = Size(w * 0.38f, h * 0.38f),
                style = Stroke(width = (w * 0.05f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
            )
        }
    }
}
