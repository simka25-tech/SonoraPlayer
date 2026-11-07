@file:OptIn(ExperimentalComposeUiApi::class)

package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import com.sonora.player.SongAction
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.player.PlayerUiState
import com.sonora.player.player.RepeatMode
import kotlinx.coroutines.delay

/** Полоса перемотки: щелчок или перетаскивание; при наведении становится толще и показывает «бегунок». */
@Composable
fun SeekBar(
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    track: Color = Glass.copy(alpha = 0.16f),
    onPreview: ((Float?) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val (src, hovered) = rememberHover()
    val active by animateFloatAsState(if (hovered || dragging != null) 1f else 0f, tween(200), label = "seekActive")
    val seek by rememberUpdatedState(onSeek)
    val preview by rememberUpdatedState(onPreview)
    Box(
        modifier
            .height(20.dp)
            .hoverable(src)
            .hand()
            .pointerInput(Unit) {
                detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = (it.x / size.width).coerceIn(0f, 1f)
                        preview?.invoke(dragging)
                    },
                    onDragEnd = {
                        dragging?.let(seek)
                        dragging = null
                        preview?.invoke(null)
                    },
                    onDragCancel = {
                        dragging = null
                        preview?.invoke(null)
                    },
                ) { change, _ ->
                    change.consume()
                    dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                    preview?.invoke(dragging)
                }
            }
            .drawBehind {
                val p = (dragging ?: progress()).coerceIn(0f, 1f)
                val h = (4f + 2f * active).dp.toPx()
                val cy = size.height / 2
                val radius = CornerRadius(h / 2)
                drawRoundRect(track, Offset(0f, cy - h / 2), Size(size.width, h), radius)
                drawRoundRect(accent, Offset(0f, cy - h / 2), Size(size.width * p, h), radius)
                if (active > 0f) {
                    drawCircle(Color.Black.copy(alpha = 0.25f * active), 8.dp.toPx() * active, Offset(size.width * p, cy + 1.dp.toPx()))
                    drawCircle(Color.White, 7.dp.toPx() * active, Offset(size.width * p, cy))
                }
            },
    )
}

/** Позиция с точностью до секунды — для подписей времени (не перерисовываются каждый кадр). */
@Composable
fun rememberSecond(position: State<Long>): State<Long> = remember(position) { derivedStateOf { position.value / 1000 } }

@Composable
fun PlaybackTimeline(app: AppState, state: PlayerUiState, accent: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    val position = rememberPlaybackPosition(app.player)
    var preview by remember { mutableStateOf<Float?>(null) }
    val second by rememberSecond(position)
    val duration = state.duration
    val shown = preview?.let { (it * duration / 1000).toLong() } ?: second
    val style = if (large) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(shown * 1000, zero = true), style = style, color = muted, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        SeekBar(
            progress = { if (duration > 0) position.value.toFloat() / duration else 0f },
            onSeek = { f -> if (duration > 0) app.player.seekTo((f * duration).toLong()) },
            accent = accent,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            onPreview = { preview = it },
        )
        Text(formatTime(duration, zero = true), style = style, color = muted, modifier = Modifier.width(48.dp))
    }
}

/** Кнопки управления: перемешать, назад, играть/пауза, вперёд, повтор. */
@Composable
fun TransportControls(app: AppState, state: PlayerUiState, accent: Color, large: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = if (large) 44.dp else 34.dp
    val smallIcon = if (large) 24.dp else 20.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 18.dp else 10.dp)) {
        SmallIconButton(Icons.Rounded.Shuffle, "Перемешать (S)", tint = muted, size = small, iconSize = smallIcon, active = state.shuffle) {
            app.player.toggleShuffle()
        }
        SmallIconButton(Icons.Rounded.SkipPrevious, "Назад (Ctrl+←)", tint = MaterialTheme.colorScheme.onSurface, size = small + 6.dp, iconSize = smallIcon + 6.dp) {
            app.player.previous()
        }
        PlayPauseButton(state, accent, if (large) 72.dp else 42.dp) { app.player.toggle() }
        SmallIconButton(Icons.Rounded.SkipNext, "Вперёд (Ctrl+→)", tint = MaterialTheme.colorScheme.onSurface, size = small + 6.dp, iconSize = smallIcon + 6.dp) {
            app.player.next()
        }
        SmallIconButton(
            if (state.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Повтор (R)", tint = muted, size = small, iconSize = smallIcon, active = state.repeat != RepeatMode.Off,
        ) { app.player.cycleRepeat() }
    }
}

@Composable
fun PlayPauseButton(state: PlayerUiState, accent: Color, size: Dp, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    val scale by animateFloatAsState(if (hovered) 1.07f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "ppScale")
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(accent)
            .hoverable(src)
            .handClick(onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            state.isPlaying,
            transitionSpec = {
                (scaleIn(tween(220, easing = Motion.Decelerate), initialScale = 0.5f) + fadeIn(tween(180))) togetherWith
                    (scaleOut(tween(160), targetScale = 0.5f) + fadeOut(tween(120)))
            },
            label = "playPause",
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Пауза (Пробел)" else "Играть (Пробел)",
                tint = onAccent,
                modifier = Modifier.size(size * 0.55f),
            )
        }
        if (state.buffering) {
            CircularProgressIndicator(Modifier.fillMaxSize(), color = onAccent.copy(alpha = 0.6f), strokeWidth = 2.dp)
        }
    }
}

/** Громкость: ползунок, колесо мыши и щелчок по иконке для выключения звука. */
@Composable
fun VolumeControl(app: AppState, state: PlayerUiState, accent: Color, width: Dp = 110.dp) {
    val icon = when {
        state.muted || state.volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
        state.volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    val shown = if (state.muted) 0f else state.volume
    val animated by animateFloatAsState(shown, tween(120), label = "volume")
    val current by rememberUpdatedState(state.volume)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.onPointerEvent(PointerEventType.Scroll) { e ->
            val dy = e.changes.first().scrollDelta.y
            app.player.setVolume(current - dy * 0.05f)
        },
    ) {
        SmallIconButton(icon, "Звук (M)", size = 34.dp, iconSize = 20.dp) { app.player.toggleMute() }
        SeekBar(
            progress = { animated },
            onSeek = { app.player.setVolume(it) },
            accent = accent,
            modifier = Modifier.width(width),
            onPreview = { it?.let(app.player::setVolume) },
        )
    }
}

@Composable
fun PlayerBar(app: AppState, target: Color) {
    val state by app.player.state.collectAsState()
    val accent by animateColorAsState(target, tween(700), label = "barAccent")
    val song = state.current
    AnimatedVisibility(
        song != null,
        enter = slideInVertically(tween(450, easing = Motion.Decelerate)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300, easing = Motion.Accelerate)) { it } + fadeOut(tween(200)),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Glass.copy(alpha = 0.08f)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(Surface1)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingInfo(app, state, accent, Modifier.weight(0.3f))
                Column(
                    Modifier
                        .weight(0.4f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TransportControls(app, state, accent)
                    Spacer(Modifier.height(4.dp))
                    PlaybackTimeline(app, state, accent, Modifier.fillMaxWidth())
                }
                Row(
                    Modifier.weight(0.3f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpeedMenu(app, state)
                    SleepMenu(app, state)
                    SmallIconButton(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь (Q)", size = 34.dp, iconSize = 20.dp, active = app.queueOpen) {
                        app.queueOpen = !app.queueOpen
                    }
                    Spacer(Modifier.width(4.dp))
                    VolumeControl(app, state, accent)
                    Spacer(Modifier.width(6.dp))
                    SmallIconButton(Icons.Rounded.OpenInFull, "Сейчас играет (F11)", size = 34.dp, iconSize = 18.dp) {
                        app.nowPlayingOpen = true
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingInfo(app: AppState, state: PlayerUiState, accent: Color, modifier: Modifier) {
    val song = state.current ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            song,
            transitionSpec = {
                (slideInVertically(tween(380, easing = Motion.Decelerate)) { it / 2 } + fadeIn(tween(300))) togetherWith
                    (slideOutVertically(tween(260, easing = Motion.Accelerate)) { -it / 2 } + fadeOut(tween(200)))
            },
            contentKey = { it.id },
            label = "barSong",
        ) { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (src, hovered) = rememberHover()
                val lift by animateFloatAsState(if (hovered) 1.06f else 1f, spring(0.6f), label = "barCover")
                CoverImage(
                    app.cover(s), s.album + s.artist,
                    Modifier
                        .size(58.dp)
                        .graphicsLayer {
                            scaleX = lift
                            scaleY = lift
                        }
                        .hoverable(src)
                        .handClick { app.nowPlayingOpen = true },
                    RoundedCornerShape(10.dp), 22.dp,
                )
                Column(
                    Modifier
                        .padding(start = 14.dp)
                        .width(200.dp),
                ) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.handClick {
                            if (s.album.isNotBlank()) app.onSongAction(s, SongAction.GoToAlbum)
                        },
                    )
                    Text(
                        s.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.handClick { if (s.artist.isNotBlank()) app.onSongAction(s, SongAction.GoToArtist) },
                    )
                }
            }
        }
        val fav = song.id in app.favoriteIds
        AnimatedContent(fav, transitionSpec = { scaleIn(spring(0.45f, Spring.StiffnessMedium)) togetherWith scaleOut(tween(120)) }, label = "barFav") { f ->
            SmallIconButton(
                if (f) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Избранное (L)", tint = if (f) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            ) { app.onSongAction(song, SongAction.ToggleFavorite) }
        }
    }
}

@Composable
fun SpeedMenu(app: AppState, state: PlayerUiState) {
    var open by remember { mutableStateOf(false) }
    Box {
        val label = if (state.speed == 1f) null else "×" + formatSpeed(state.speed)
        if (label == null) {
            SmallIconButton(Icons.Rounded.Speed, "Скорость", size = 34.dp, iconSize = 20.dp) { open = true }
        } else {
            Chip(label, MaterialTheme.colorScheme.primary) { open = true }
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(14.dp), containerColor = Surface2) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                DropdownMenuItem(
                    text = { Text("×" + formatSpeed(s), fontWeight = if (s == state.speed) FontWeight.Bold else null) },
                    onClick = {
                        app.player.setSpeed(s)
                        open = false
                    },
                    modifier = Modifier.hand(),
                )
            }
        }
    }
}

private fun formatSpeed(s: Float) = if (s % 1f == 0f) "${s.toInt()}" else "$s".trimEnd('0')

@Composable
fun SleepMenu(app: AppState, state: PlayerUiState) {
    var open by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.sleepEndsAt) {
        val end = state.sleepEndsAt ?: return@LaunchedEffect
        while (true) {
            left = (end - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
    }
    Box {
        when {
            state.sleepEndsAt != null -> Chip(formatTime(left, zero = true), MaterialTheme.colorScheme.primary, Icons.Rounded.Bedtime) { open = true }
            state.sleepEndOfTrack -> Chip("Конец трека", MaterialTheme.colorScheme.primary, Icons.Rounded.Bedtime) { open = true }
            else -> SmallIconButton(Icons.Rounded.Bedtime, "Таймер сна", size = 34.dp, iconSize = 20.dp) { open = true }
        }
        DropdownMenu(open, { open = false }, shape = RoundedCornerShape(14.dp), containerColor = Surface2) {
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("Через $m мин") }, onClick = {
                    app.player.setSleepTimer(m)
                    app.notify("Музыка остановится через $m мин")
                    open = false
                }, modifier = Modifier.hand())
            }
            DropdownMenuItem(text = { Text("В конце трека") }, onClick = {
                app.player.sleepAtEndOfTrack()
                open = false
            }, modifier = Modifier.hand())
            if (state.sleepEndsAt != null || state.sleepEndOfTrack) {
                DropdownMenuItem(text = { Text("Отключить таймер") }, leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = {
                    app.player.cancelSleepTimer()
                    open = false
                }, modifier = Modifier.hand())
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val (src, hovered) = rememberHover()
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (hovered) 0.24f else 0.16f))
            .hoverable(src)
            .handClick(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Боковая панель очереди воспроизведения. */
@Composable
fun QueuePanel(app: AppState, modifier: Modifier = Modifier) {
    val state by app.player.state.collectAsState()
    val queue = state.queue
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (state.index > 1) listState.scrollToItem(state.index - 1) }
    val remainingMs = queue.drop(state.index + 1).sumOf { it.durationMs }
    Column(
        modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(Surface1),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Очередь", style = MaterialTheme.typography.titleLarge)
                Text(
                    plural(queue.size, "трек", "трека", "треков") + if (remainingMs > 0) " · осталось ${formatDuration(remainingMs)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SmallIconButton(Icons.Rounded.Close, "Закрыть") { app.queueOpen = false }
        }
        if (queue.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь пуста", "Включите любой трек")
            return@Column
        }
        LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { i, s -> "${s.id}#$i" }) { i, song ->
                QueueItem(app, i, song, i == state.index, state.isPlaying, i < state.index, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun QueueItem(
    app: AppState,
    index: Int,
    song: com.sonora.player.data.Song,
    current: Boolean,
    playing: Boolean,
    past: Boolean,
    modifier: Modifier,
) {
    val (src, hovered) = rememberHover()
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            current -> accent.copy(alpha = 0.14f)
            hovered -> Glass.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        tween(200), label = "queueBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .hoverable(src)
            .handClick { app.player.skipTo(index) }
            .padding(8.dp)
            .graphicsLayer { alpha = if (past && !hovered) 0.5f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp)) {
            CoverImage(app.cover(song), song.album + song.artist, Modifier.fillMaxSize(), RoundedCornerShape(8.dp), 18.dp)
            if (current) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(playing, Color.White, Modifier.size(16.dp)) }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AnimatedVisibility(hovered && !current, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            SmallIconButton(Icons.Rounded.Close, "Убрать из очереди", size = 28.dp, iconSize = 16.dp) { app.player.removeAt(index) }
        }
        if (!hovered || current) {
            Text(formatTime(song.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
