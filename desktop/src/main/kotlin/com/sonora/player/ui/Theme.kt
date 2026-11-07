package com.sonora.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sonora.player.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Цвета интерфейса, которые зависят от светлой/тёмной темы. */
@Immutable
data class SonoraColors(
    val isDark: Boolean,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val onBg: Color,
    val muted: Color,
    /** Базовый цвет для полупрозрачных «стеклянных» подложек и треков. */
    val glass: Color,
    val outline: Color,
)

val DarkColors = SonoraColors(
    isDark = true,
    bg = Color(0xFF0B0B12),
    surface1 = Color(0xFF14141F),
    surface2 = Color(0xFF1E1E2C),
    onBg = Color.White,
    // Вторичный текст в тёмной теме — почти белый, чтобы хорошо читался.
    muted = Color(0xFFE2E2EC),
    glass = Color.White,
    outline = Color(0xFF34344A),
)

val LightColors = SonoraColors(
    isDark = false,
    bg = Color(0xFFF5F4FA),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEAE8F2),
    onBg = Color(0xFF15151F),
    muted = Color(0xFF66667E),
    glass = Color(0xFF1A1A2E),
    outline = Color(0xFFD3D1E0),
)

val LocalSonoraColors = staticCompositionLocalOf { DarkColors }

val Bg: Color @Composable @ReadOnlyComposable get() = LocalSonoraColors.current.bg
val Surface1: Color @Composable @ReadOnlyComposable get() = LocalSonoraColors.current.surface1
val Surface2: Color @Composable @ReadOnlyComposable get() = LocalSonoraColors.current.surface2
val Glass: Color @Composable @ReadOnlyComposable get() = LocalSonoraColors.current.glass
val IsDark: Boolean @Composable @ReadOnlyComposable get() = LocalSonoraColors.current.isDark

val DefaultAccentDark = Color(0xFFFF4D8D)
val DefaultAccentLight = Color(0xFFE0336F)
val Violet = Color(0xFF7C4DFF)
val Teal = Color(0xFF00BFA5)

/** Кривые движения в духе Material 3: плавный разгон и мягкое торможение. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
    )
}

@Composable
private fun animated(target: Color) = animateColorAsState(target, tween(500, easing = Motion.Standard), label = "theme").value

@Composable
fun SonoraTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    val base = if (dark) DarkColors else LightColors
    // Плавный переход между светлой и тёмной темой.
    val colors = SonoraColors(
        isDark = dark,
        bg = animated(base.bg),
        surface1 = animated(base.surface1),
        surface2 = animated(base.surface2),
        onBg = animated(base.onBg),
        muted = animated(base.muted),
        glass = animated(base.glass),
        outline = animated(base.outline),
    )
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent, secondary = accent, onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.22f), onSecondaryContainer = colors.onBg,
            background = colors.bg, onBackground = colors.onBg,
            surface = colors.surface1, onSurface = colors.onBg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface1, surfaceContainerHigh = colors.surface2,
            surfaceContainerLow = colors.surface1, surfaceContainerHighest = colors.surface2,
            outline = colors.outline, outlineVariant = colors.outline,
            error = Color(0xFFFF6B6B),
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent, secondary = accent, onSecondary = onAccent,
            secondaryContainer = accent.copy(alpha = 0.16f), onSecondaryContainer = colors.onBg,
            background = colors.bg, onBackground = colors.onBg,
            surface = colors.surface1, onSurface = colors.onBg,
            surfaceVariant = colors.surface2, onSurfaceVariant = colors.muted,
            surfaceContainer = colors.surface1, surfaceContainerHigh = colors.surface2,
            surfaceContainerLow = colors.surface1, surfaceContainerHighest = colors.surface2,
            outline = colors.outline, outlineVariant = colors.outline,
            error = Color(0xFFD32F2F),
        )
    }
    CompositionLocalProvider(LocalSonoraColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // Цвет по умолчанию для Text и Icon без явного color — иначе он чёрный вне Surface.
            CompositionLocalProvider(LocalContentColor provides colors.onBg, content = content)
        }
    }
}

private val accentCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

/**
 * Акцентный цвет из обложки текущего трека.
 * Возвращает целевой цвет без анимации: анимируют его только те места, где это заметно.
 */
@Composable
fun rememberArtworkAccent(url: String?, dark: Boolean): State<Color> {
    val fallback = if (dark) DefaultAccentDark else DefaultAccentLight
    val target = remember { mutableStateOf(fallback) }
    var raw by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) {
            raw = null
            return@LaunchedEffect
        }
        val rgb = accentCache[url] ?: withContext(Dispatchers.IO) { runCatching { extractAccent(url) }.getOrNull() }
        if (rgb != null) accentCache[url] = rgb
        raw = rgb?.let { Color(it) }
    }
    LaunchedEffect(raw, dark) {
        target.value = raw?.let { normalizeAccent(it, dark) } ?: fallback
    }
    return target
}

/** Самый «сочный» цвет обложки: взвешиваем пиксели по насыщенности и усредняем по оттенкам. */
private fun extractAccent(url: String): Int? {
    val bytes = Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return null
        r.body?.bytes() ?: return null
    }
    val image = Image.makeFromEncoded(bytes)
    val bitmap = Bitmap.makeFromImage(image)
    val buckets = Array(12) { DoubleArray(4) }
    val step = max(1, min(bitmap.width, bitmap.height) / 48)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap.getColor(x, y)
            val hsl = rgbToHsl(c)
            val weight = hsl[1] * (1f - abs(hsl[2] - 0.5f) * 2f)
            if (weight > 0.05f) {
                val b = buckets[(hsl[0] / 30f).toInt().coerceIn(0, 11)]
                b[0] += weight
                b[1] += (c shr 16 and 0xFF) * weight
                b[2] += (c shr 8 and 0xFF) * weight
                b[3] += (c and 0xFF) * weight
            }
            x += step
        }
        y += step
    }
    val best = buckets.maxByOrNull { it[0] } ?: return null
    if (best[0] <= 0.0) return null
    val r = (best[1] / best[0]).toInt()
    val g = (best[2] / best[0]).toInt()
    val b = (best[3] / best[0]).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

private fun rgbToHsl(argb: Int): FloatArray {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (mx) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

/** Делает цвет достаточно насыщенным и контрастным для выбранной темы. */
fun normalizeAccent(c: Color, dark: Boolean): Color {
    val argb = (0xFF shl 24) or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt()
    val hsl = rgbToHsl(argb)
    if (hsl[1] < 0.12f) return if (dark) DefaultAccentDark else DefaultAccentLight
    val s = hsl[1].coerceAtLeast(0.5f)
    val l = if (dark) hsl[2].coerceIn(0.58f, 0.72f) else hsl[2].coerceIn(0.38f, 0.5f)
    return Color.hsl(hsl[0], s, l)
}