package com.praveen.bchat.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Visualizes a stylized P2P mesh network with dual connected speech nodes
 * and an energetic turbo data wave in an expressive M3 squircle container.
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
                .padding(size.dp * 0.18f)
        ) {
            val w = this.size.width
            val h = this.size.height

            // 1. Left Peer Node (Chat Bubble / Radio Node)
            val leftNodeCenter = Offset(w * 0.32f, h * 0.42f)
            val leftNodeRadius = w * 0.22f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanNeon.copy(alpha = 0.9f), CyanNeon.copy(alpha = 0.4f)),
                    center = leftNodeCenter,
                    radius = leftNodeRadius
                ),
                center = leftNodeCenter,
                radius = leftNodeRadius
            )

            // 2. Right Peer Node
            val rightNodeCenter = Offset(w * 0.68f, h * 0.58f)
            val rightNodeRadius = w * 0.20f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(PurpleNeon.copy(alpha = 0.95f), BlueElectric.copy(alpha = 0.5f)),
                    center = rightNodeCenter,
                    radius = rightNodeRadius
                ),
                center = rightNodeCenter,
                radius = rightNodeRadius
            )

            // 3. Lightning / Data Beam bridging left and right nodes
            val beamPath = Path().apply {
                moveTo(leftNodeCenter.x, leftNodeCenter.y)
                lineTo(w * 0.48f, h * 0.34f)
                lineTo(w * 0.42f, h * 0.66f)
                lineTo(rightNodeCenter.x, rightNodeCenter.y)
            }

            drawPath(
                path = beamPath,
                color = Color.White.copy(alpha = 0.95f),
                style = Stroke(
                    width = (w * 0.08f).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 4. Little glowing beacon dots
            drawCircle(
                color = Color.White,
                radius = (w * 0.06f).coerceAtLeast(1.5f),
                center = leftNodeCenter
            )
            drawCircle(
                color = Color.White,
                radius = (w * 0.05f).coerceAtLeast(1.5f),
                center = rightNodeCenter
            )
        }
    }
}
