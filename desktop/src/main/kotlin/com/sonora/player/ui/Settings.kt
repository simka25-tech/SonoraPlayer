package com.sonora.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import javax.swing.JFileChooser
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import com.sonora.player.data.ThemeMode

/** Окно настроек поверх приложения: затемнение и всплывающая карточка. */
@Composable
fun SettingsDialog(app: AppState, onClose: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    val s = app.settings
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickableNoRipple(onClose),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            Modifier
                .padding(vertical = 24.dp)
                .width(560.dp)
                .heightIn(max = 720.dp)
                .graphicsLayer {
                    val v = appear.value
                    scaleX = 0.92f + 0.08f * v
                    scaleY = 0.92f + 0.08f * v
                    translationY = (1f - v) * 40f
                }
                .shadowSoft(shape)
                .clip(shape)
                .background(Surface1)
                .border(1.dp, Glass.copy(alpha = 0.08f), shape)
                .clickableNoRipple { }
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SmallIconButton(Icons.Rounded.Close, "Закрыть (Esc)", onClick = onClose)
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Group("Оформление")
                ThemeSelector(s.themeMode, app::setThemeMode)
                SwitchRow("Цвет из обложки", "Акцент интерфейса подстраивается под играющий трек", s.dynamicColors, app::setDynamicColors)
                Group("Воспроизведение")
                SwitchRow(
                    "Всегда перекодировать в MP3",
                    "FLAC, ALAC и другие форматы перекодируются автоматически. Включите для экономии трафика",
                    s.transcode, app::setTranscode,
                )
                SwitchRow("Медиаклавиши клавиатуры", "Play/Pause, Next и Previous работают, даже когда окно свёрнуто", s.mediaKeys, app::setMediaKeys)
                SwitchRow("Сворачивать в трей при закрытии", "Музыка продолжит играть, управление — из значка в трее", s.closeToTray, app::setCloseToTray)
                Group("Музыка из интернета")
                OnlineSettings(app)
                Group("Данные")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("История прослушиваний", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            plural(app.stats.size, "трек", "трека", "треков"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PillButton("Очистить", Icons.Rounded.DeleteSweep, primary = false, onClick = app::clearHistory)
                }
                Group("Горячие клавиши")
                Hotkeys()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sonora для Windows 1.1 · музыка с Synology Audio Station, Internet Archive и Jamendo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnlineSettings(app: AppState) {
    var key by remember { mutableStateOf(app.settings.jamendoClientId) }
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Client ID для Jamendo", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Бесплатно: зарегистрируйтесь на devportal.jamendo.com и создайте приложение. Internet Archive работает без ключа",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(Glass.copy(alpha = 0.07f))
                    .border(1.dp, Glass.copy(alpha = 0.1f), shape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (key.isEmpty()) Text("например, 1a2b3c4d", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    key,
                    { key = it.trim(); app.setJamendoClientId(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth().trackTextFocus(),
                )
            }
            Spacer(Modifier.width(10.dp))
            PillButton("Получить", Icons.AutoMirrored.Rounded.OpenInNew, primary = false) {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://devportal.jamendo.com/")) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Папка загрузок", style = MaterialTheme.typography.bodyLarge)
                Text(
                    app.downloadDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            PillButton("Изменить", Icons.Rounded.Folder, primary = false) {
                val chooser = JFileChooser(app.downloadDir).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Папка для скачанной музыки"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    app.setDownloadDir(chooser.selectedFile.absolutePath)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Hotkeys() {
    val keys = listOf(
        "Пробел" to "Играть / пауза",
        "Ctrl + → / ←" to "Следующий / предыдущий трек",
        "→ / ←" to "Перемотка на 5 секунд",
        "↑ / ↓" to "Громкость",
        "M" to "Выключить звук",
        "L" to "В избранное",
        "S / R" to "Перемешивание / повтор",
        "Q" to "Очередь",
        "F11" to "Сейчас играет",
        "Ctrl + F" to "Поиск",
        "Ctrl + T" to "Сменить тему",
        "Alt + ← / Esc" to "Назад",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { (k, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    k,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .width(150.dp)
                        .padding(end = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(v, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Сегментированный переключатель темы со скользящим индикатором. */
@Composable
fun ThemeSelector(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(ThemeMode.System to "Как в системе", ThemeMode.Light to "Светлая", ThemeMode.Dark to "Тёмная")
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.copy(alpha = 0.06f))
            .padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * options.indexOfFirst { it.first == mode }, spring(0.8f, Spring.StiffnessMediumLow), label = "themeX")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .clip(RoundedCornerShape(11.dp))
                .background(accent),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (m, label) ->
                val selected = m == mode
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(250), label = "themeLabel",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .handClick { onChange(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
