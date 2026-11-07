@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.sonora.player.MainViewModel
import com.sonora.player.SongAction
import com.sonora.player.data.Artist
import com.sonora.player.data.Song
import com.sonora.player.player.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class PlayerSheet { Queue, Sleep, Speed }

@Composable
fun PlayerScreen(vm: MainViewModel, onCollapse: () -> Unit) {
    val p by vm.player.state.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val song = p.current ?: return
    // Цвет обложки меняется плавно только здесь, а не во всей теме.
    val accent by animateColorAsState(MaterialTheme.colorScheme.primary, tween(800, easing = Motion.Standard), label = "playerAccent")
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val cornerPx = with(LocalDensity.current) { 28.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            // Смещение и скругление читаются в graphicsLayer — свайп не вызывает перекомпозицию.
            .graphicsLayer {
                translationY = dragY
                val progress = (dragY / 200f).coerceIn(0f, 1f)
                shape = RoundedCornerShape(cornerPx * progress)
                clip = dragY > 0f
                val s = 1f - 0.04f * (dragY / size.height).coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
            }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { d -> dragY = (dragY + d).coerceAtLeast(0f) },
                onDragStopped = { velocity ->
                    if (dragY > 280f || velocity > 1800f) onCollapse()
                    else scope.launch {
                        animate(dragY, 0f, initialVelocity = velocity, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragY = v }
                    }
                },
            )
            .background(Bg),
    ) {
        AuroraBackground(
            listOf(accent, accent.copy(alpha = 0.7f).compositeOver(Violet), accent.copy(alpha = 0.5f).compositeOver(Teal)),
            Modifier.fillMaxSize(),
            durationMs = 24000,
            intensity = 0.5f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0.1f), Bg.copy(alpha = 0.55f), Bg.copy(alpha = 0.92f)))),
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Свернуть", Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EqualizerBars(p.isPlaying, accent, Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("СЕЙЧАС ИГРАЕТ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        song.album.ifBlank { song.artist },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { sheet = PlayerSheet.Queue }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь") }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Artwork(song, p.index, vm.cover(song), p.isPlaying, onNext = { vm.player.next() }, onPrevious = { vm.player.previous() })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    song,
                    contentKey = { it.id },
                    transitionSpec = {
                        (fadeIn(tween(400, 80, Motion.Standard)) + slideInVertically(tween(500, easing = Motion.Decelerate)) { it / 4 }) togetherWith
                            fadeOut(tween(150, easing = Motion.Accelerate))
                    },
                    label = "title",
                    modifier = Modifier.weight(1f),
                ) { s ->
                    Column {
                        Text(
                            s.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            s.artist.ifBlank { "Неизвестный исполнитель" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                onCollapse()
                                vm.openArtist(Artist(s.artist))
                            },
                        )
                    }
                }
                HeartButton(song.id in favIds) { vm.toggleFavorite(song) }
            }

            Spacer(Modifier.height(16.dp))
            ProgressSection(vm, p, accent) { vm.player.seekTo(it) }
            Spacer(Modifier.height(8.dp))
            Controls(vm, p, accent)
            Spacer(Modifier.height(12.dp))
            ExtraRow(p, song, accent, onSheet = { sheet = it })
            Spacer(Modifier.height(12.dp))
        }
    }

    when (sheet) {
        PlayerSheet.Queue -> QueueSheet(vm, p) { sheet = null }
        PlayerSheet.Sleep -> SleepSheet(vm, p) { sheet = null }
        PlayerSheet.Speed -> SpeedSheet(vm, p) { sheet = null }
        null -> Unit
    }
}

/** Обложка: «дышит» при воспроизведении, листается свайпом, меняется с анимацией. */
@Composable
private fun Artwork(song: Song, index: Int, cover: String?, playing: Boolean, onNext: () -> Unit, onPrevious: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        if (playing) 1f else 0.86f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    var dx by remember { mutableFloatStateOf(0f) }
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)

    AnimatedContent(
        targetState = index to song,
        contentKey = { it.first.toString() + it.second.id },
        transitionSpec = {
            val forward = targetState.first >= initialState.first
            (slideInHorizontally(tween(550, easing = Motion.Emphasized)) { if (forward) it / 2 else -it / 2 } +
                fadeIn(tween(400, 100, Motion.Standard)) +
                scaleIn(tween(550, easing = Motion.Emphasized), initialScale = 0.9f)) togetherWith
                (slideOutHorizontally(tween(450, easing = Motion.Emphasized)) { if (forward) -it / 2 else it / 2 } +
                    fadeOut(tween(250, easing = Motion.Accelerate)) +
                    scaleOut(tween(450, easing = Motion.Emphasized), targetScale = 0.9f))
        },
        label = "artwork",
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f),
    ) { (_, s) ->
        CoverImage(
            if (s.id == song.id) cover else null,
            s.album + s.artist,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dx
                    rotationZ = dx / 50f
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dx < -180f -> next()
                                dx > 180f -> previous()
                            }
                            scope.launch {
                                animate(dx, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dx = v }
                            }
                        },
                        onDragCancel = { dx = 0f },
                    ) { change, amount ->
                        change.consume()
                        dx += amount
                    }
                }
                .shadow(36.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent),
            RoundedCornerShape(28.dp),
            96.dp,
        )
    }
}

@Composable
private fun HeartButton(checked: Boolean, onToggle: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var initial by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (initial) {
            initial = false
            return@LaunchedEffect
        }
        scale.animateTo(1.3f, tween(140, easing = Motion.Decelerate))
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    val tint by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else Glass.copy(alpha = 0.75f), tween(300), label = "heartTint",
    )
    IconButton(onClick = onToggle) {
        Icon(
            if (checked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            "В избранное",
            tint = tint,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun ProgressSection(vm: MainViewModel, p: PlayerUiState, accent: Color, onSeek: (Long) -> Unit) {
    val position = rememberPlaybackPosition(vm.player)
    val duration = p.duration.coerceAtLeast(1)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // Текст времени пересчитывается раз в секунду, полоса — в фазе отрисовки на каждом кадре.
    val shownMs by remember(duration) {
        derivedStateOf {
            val ms = dragFraction?.let { (it * duration).toLong() } ?: position.value.coerceIn(0, duration)
            ms / 1000 * 1000
        }
    }

    SeekBar(
        progress = { dragFraction ?: (position.value.toFloat() / duration).coerceIn(0f, 1f) },
        dragging = dragFraction != null,
        color = accent,
        onDrag = { dragFraction = it },
        onSeek = { f ->
            dragFraction = null
            onSeek((f * duration).toLong())
        },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatTime(shownMs, zero = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            if (p.duration > 0) "-" + formatTime(p.duration - shownMs, zero = true) else "--:--",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Своя полоса перемотки: утолщается и увеличивает ползунок при перетаскивании. */
@Composable
private fun SeekBar(progress: () -> Float, dragging: Boolean, color: Color, onDrag: (Float) -> Unit, onSeek: (Float) -> Unit) {
    val thumb by animateDpAsState(if (dragging) 20.dp else 13.dp, tween(250, easing = Motion.Standard), label = "thumb")
    val track by animateDpAsState(if (dragging) 9.dp else 5.dp, tween(250, easing = Motion.Standard), label = "track")
    val trackColor = Glass.copy(alpha = 0.16f)
    val thumbColor = if (IsDark) Color.White else color
    val drag by rememberUpdatedState(onDrag)
    val seek by rememberUpdatedState(onSeek)
    var last by remember { mutableFloatStateOf(0f) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        last = (o.x / size.width).coerceIn(0f, 1f)
                        drag(last)
                    },
                    onDragEnd = { seek(last) },
                    onDragCancel = { seek(last) },
                ) { change, _ ->
                    change.consume()
                    last = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag(last)
                }
            },
    ) {
        val progress = progress()
        val y = size.height / 2
        val h = track.toPx()
        drawRoundRect(trackColor, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color), endX = size.width * progress + 1f),
            Offset(0f, y - h / 2),
            Size(size.width * progress, h),
            CornerRadius(h / 2),
        )
        val center = Offset(size.width * progress, y)
        drawCircle(color.copy(alpha = 0.3f), thumb.toPx(), center)
        drawCircle(thumbColor, thumb.toPx() / 2, center)
    }
}

@Composable
private fun Controls(vm: MainViewModel, p: PlayerUiState, accent: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(Icons.Rounded.Shuffle, "Перемешать", p.shuffle) { vm.player.toggleShuffle() }
        IconButton(onClick = { vm.player.previous() }, Modifier.size(60.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Предыдущий", Modifier.size(40.dp))
        }
        PlayPauseButton(p.isPlaying, p.buffering, accent) { vm.player.toggle() }
        IconButton(onClick = { vm.player.next() }, Modifier.size(60.dp)) {
            Icon(Icons.Rounded.SkipNext, "Следующий", Modifier.size(40.dp))
        }
        ToggleIcon(
            if (p.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Повтор",
            p.repeat != Player.REPEAT_MODE_OFF,
        ) { vm.player.cycleRepeat() }
    }
}

/** Кнопка play/pause: форма перетекает из круга в «скруглённый квадрат». */
@Composable
private fun PlayPauseButton(playing: Boolean, buffering: Boolean, accent: Color, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (playing) 24.dp else 40.dp,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "corner",
    )
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(buffering, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(Modifier.size(80.dp), color = accent, strokeWidth = 2.dp)
        }
        Box(
            Modifier
                .size(70.dp)
                .bouncyClick(onClick = onClick)
                .shadow(20.dp, RoundedCornerShape(corner), ambientColor = accent, spotColor = accent)
                .clip(RoundedCornerShape(corner))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                playing,
                transitionSpec = {
                    (scaleIn(tween(300, easing = Motion.Decelerate), initialScale = 0.5f) + fadeIn(tween(200))) togetherWith
                        (scaleOut(tween(180, easing = Motion.Accelerate), targetScale = 0.5f) + fadeOut(tween(150)))
                },
                label = "playIcon",
            ) { isPlaying ->
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) "Пауза" else "Играть",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Glass.copy(alpha = 0.6f), tween(300), label = "toggleTint",
    )
    val dot by animateFloatAsState(if (active) 1f else 0f, tween(300, easing = Motion.Standard), label = "toggleDot")
    IconButton(onClick = onClick) {
        // Место под точку зарезервировано всегда, чтобы иконка не подпрыгивала.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, description, tint = tint)
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .graphicsLayer {
                        scaleX = dot
                        scaleY = dot
                        alpha = dot
                    }
                    .background(tint, CircleShape),
            )
        }
    }
}

@Composable
private fun ExtraRow(p: PlayerUiState, song: Song, accent: Color, onSheet: (PlayerSheet) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.sleepEndsAt) {
        now = System.currentTimeMillis()
        while (p.sleepEndsAt != null) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val sleepLabel = when {
        p.sleepEndOfTrack -> "После трека"
        p.sleepEndsAt != null -> formatTime((p.sleepEndsAt - now).coerceAtLeast(0), zero = true)
        else -> "Сон"
    }
    val info = listOfNotNull(
        song.format.uppercase().takeIf { it.isNotBlank() },
        song.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip(Icons.Rounded.Bedtime, sleepLabel, p.sleepEndsAt != null || p.sleepEndOfTrack, accent) { onSheet(PlayerSheet.Sleep) }
        Chip(Icons.Rounded.Speed, "${"%.2f".format(p.speed).trimEnd('0').trimEnd('.', ',')}×", p.speed != 1f, accent) { onSheet(PlayerSheet.Speed) }
        Spacer(Modifier.weight(1f))
        if (info.isNotEmpty()) {
            Text(
                info,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Glass.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) accent.copy(alpha = 0.25f) else Glass.copy(alpha = 0.08f), tween(300), label = "chipBg")
    val fg by animateColorAsState(if (active) accent else Glass.copy(alpha = 0.8f), tween(300), label = "chipFg")
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            label,
            transitionSpec = {
                (fadeIn(tween(250, easing = Motion.Standard)) + slideInVertically(tween(300, easing = Motion.Decelerate)) { it / 2 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(200, easing = Motion.Accelerate)) { -it / 2 }) using
                    SizeTransform(clip = false) { _, _ -> tween(300, easing = Motion.Standard) }
            },
            label = "chipLabel",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

@Composable
private fun QueueSheet(vm: MainViewModel, p: PlayerUiState, onDismiss: () -> Unit) {
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = Surface1) {
        Text("Очередь", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            plural(p.queue.size, "трек", "трека", "треков") + if (p.shuffle) " · перемешано" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (p.index - 1).coerceAtLeast(0))
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            state = listState,
        ) {
            itemsIndexed(p.queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                SongRow(
                    song = s,
                    cover = vm.cover(s),
                    isCurrent = i == p.index,
                    isPlaying = p.isPlaying,
                    isFavorite = s.id in favIds,
                    onClick = { vm.player.skipTo(i) },
                    onAction = { action ->
                        if (action == SongAction.GoToAlbum || action == SongAction.GoToArtist) onDismiss()
                        vm.onSongAction(s, action)
                    },
                    leading = {
                        IconButton(onClick = { vm.player.removeAt(i) }, enabled = i != p.index) {
                            Icon(
                                Icons.Rounded.Close, "Убрать",
                                tint = if (i != p.index) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepSheet(vm: MainViewModel, p: PlayerUiState, onDismiss: () -> Unit) {
    val options = listOf(5, 15, 30, 45, 60, 90)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Таймер сна", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Музыка плавно затихнет и остановится",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            options.forEach { min ->
                SheetOption(plural(min, "минута", "минуты", "минут"), false) {
                    vm.player.setSleepTimer(min)
                    vm.notify("Таймер сна: ${plural(min, "минута", "минуты", "минут")}")
                    onDismiss()
                }
            }
            SheetOption("До конца трека", p.sleepEndOfTrack) {
                vm.player.sleepAtEndOfTrack()
                onDismiss()
            }
            if (p.sleepEndsAt != null || p.sleepEndOfTrack) {
                SheetOption("Выключить таймер", false, MaterialTheme.colorScheme.error) {
                    vm.player.cancelSleepTimer()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SpeedSheet(vm: MainViewModel, p: PlayerUiState, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface1) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text("Скорость воспроизведения", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            speeds.forEach { s ->
                val label = "%.2f".format(s).trimEnd('0').trimEnd('.', ',') + "×" + if (s == 1f) " (обычная)" else ""
                SheetOption(label, abs(p.speed - s) < 0.01f) {
                    vm.player.setSpeed(s)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun SheetOption(text: String, selected: Boolean, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else color,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Мини-плеер: тап или свайп вверх — развернуть, свайп влево/вправо — переключить трек. */
@Composable
fun MiniPlayer(vm: MainViewModel, p: PlayerUiState, onExpand: () -> Unit) {
    val song = p.current ?: return
    val accent by animateColorAsState(MaterialTheme.colorScheme.primary, tween(800, easing = Motion.Standard), label = "miniAccent")
    val expand by rememberUpdatedState(onExpand)
    val position = rememberPlaybackPosition(vm.player)
    val duration = p.duration
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(22.dp)
    val surface = Surface2
    val trackColor = Glass.copy(alpha = 0.1f)

    Box(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(70.dp)
            .graphicsLayer {
                translationX = dragX * 0.6f
                alpha = 1f - (abs(dragX) / 900f).coerceIn(0f, 0.4f)
            }
            .shadow(18.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.35f).compositeOver(surface), surface)))
                // Полоса прогресса рисуется на каждом кадре без перекомпозиции.
                val fraction = if (duration > 0) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
                val inset = 16.dp.toPx()
                val h = 2.dp.toPx()
                val y = size.height - h
                drawRoundRect(trackColor, Offset(inset, y), Size(size.width - inset * 2, h), CornerRadius(h))
                drawRoundRect(accent, Offset(inset, y), Size((size.width - inset * 2) * fraction, h), CornerRadius(h))
            }
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        when {
                            abs(total.y) > abs(total.x) && total.y < -40f -> expand()
                            total.x < -110f -> vm.player.next()
                            total.x > 110f -> vm.player.previous()
                        }
                        scope.launch {
                            animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragX = v }
                        }
                    },
                    onDragCancel = { dragX = 0f },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    if (abs(total.x) > abs(total.y)) dragX = total.x
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                song,
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(tween(400, easing = Motion.Standard)) + scaleIn(tween(450, easing = Motion.Decelerate), initialScale = 0.85f)) togetherWith
                        fadeOut(tween(250, easing = Motion.Accelerate))
                },
                label = "miniCover",
            ) { s ->
                CoverImage(vm.cover(s), s.album + s.artist, Modifier.size(52.dp), RoundedCornerShape(14.dp), 22.dp)
            }
            AnimatedContent(
                song,
                contentKey = { it.id },
                transitionSpec = {
                    (slideInVertically(tween(450, easing = Motion.Emphasized)) { it / 2 } + fadeIn(tween(350, 80, Motion.Standard))) togetherWith
                        (slideOutVertically(tween(350, easing = Motion.Emphasized)) { -it / 2 } + fadeOut(tween(200, easing = Motion.Accelerate)))
                },
                label = "miniTitle",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) { s ->
                Column {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { vm.player.toggle() }) {
                AnimatedContent(
                    p.isPlaying,
                    transitionSpec = {
                        (scaleIn(tween(250, easing = Motion.Decelerate), initialScale = 0.5f) + fadeIn(tween(200))) togetherWith
                            (scaleOut(tween(150), targetScale = 0.5f) + fadeOut(tween(120)))
                    },
                    label = "miniPlay",
                ) { playing ->
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(30.dp))
                }
            }
            IconButton(onClick = { vm.player.next() }) { Icon(Icons.Rounded.SkipNext, "Следующий") }
        }
    }
}
