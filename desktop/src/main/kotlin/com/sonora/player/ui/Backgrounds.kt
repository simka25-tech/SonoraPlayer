package com.sonora.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Медленно плывущие цветные «облака» — живой фон для входа и плеера. */
@Composable
fun AuroraBackground(colors: List<Color>, modifier: Modifier = Modifier, durationMs: Int = 22000, intensity: Float = 0.55f) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "phase",
    )
    val bg = Bg
    val alpha = if (IsDark) intensity else intensity * 0.6f
    Canvas(modifier) {
        drawRect(bg)
        colors.forEachIndexed { i, c ->
            val a = phase + i * 2.1f
            val center = Offset(
                size.width * (0.5f + 0.38f * cos(a)),
                size.height * (0.38f + 0.28f * sin(a * 1.3f + i)),
            )
            val r = size.maxDimension * (0.5f + 0.08f * sin(a * 2f))
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center, r),
                r,
                center,
            )
        }
    }
}

/** Логотип: вращающееся градиентное кольцо с эквалайзером внутри. */
@Composable
fun SonoraLogo(modifier: Modifier = Modifier, playing: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "logo")
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "logoRotation",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(accent, Violet, Teal, accent))),
        )
        Box(
            Modifier
                .matchParentSize()
                .padding(5.dp)
                .clip(CircleShape)
                .background(Bg),
            contentAlignment = Alignment.Center,
        ) {
            EqualizerBars(playing, accent, Modifier.fillMaxSize(0.42f), bars = 5)
        }
    }
}
