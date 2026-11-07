@file:OptIn(ExperimentalFoundationApi::class)

package com.sonora.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.collectAsState
import com.sonora.player.player.AudioPlayer
import kotlinx.coroutines.isActive
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sonora.player.SongAction
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.Song

/** Обложка с цветной заглушкой, пока картинка грузится или отсутствует. */
@Composable
fun CoverImage(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 24.dp,
    icon: ImageVector = Icons.Rounded.MusicNote,
) {
    val colors = remember(seed) { seedGradient(seed) }
    Box(modifier.clip(shape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        if (iconSize > 0.dp) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(iconSize))
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun seedGradient(seed: String): List<Color> {
    val h = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return listOf(Color.hsl(h, 0.55f, 0.38f), Color.hsl((h + 55f) % 360f, 0.6f, 0.2f))
}

/** Анимированный «эквалайзер» — индикатор играющего трека. */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(18.dp), bars: Int = 4) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until bars).map { i ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 340 + (i * 137) % 300, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    val activity by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "eqActivity")
    Canvas(modifier) {
        val slot = size.width / bars
        val barW = slot * 0.62f
        heights.forEachIndexed { i, h ->
            val frac = 0.18f + (h.value - 0.18f) * activity
            val barH = size.height * frac
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}

/** Пружинящее нажатие: элемент слегка сжимается под пальцем. */
fun Modifier.bouncyClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.combinedClickable(
        interactionSource = interaction,
        indication = null,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
fun SongRow(
    song: Song,
    cover: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onAction: (SongAction) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        if (isCurrent) accent.copy(alpha = 0.13f) else Color.Transparent, tween(400), label = "rowBg",
    )
    val titleColor by animateColorAsState(
        if (isCurrent) accent else MaterialTheme.colorScheme.onSurface, tween(400), label = "rowTitle",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .combinedClickable(onLongClick = { menu = true }, onClick = onClick)
            .padding(start = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box(Modifier.size(50.dp)) {
            CoverImage(cover, song.album + song.artist, Modifier.fillMaxSize(), RoundedCornerShape(12.dp), 20.dp)
            val overlay by animateFloatAsState(if (isCurrent) 1f else 0f, tween(350, easing = Motion.Standard), label = "rowOverlay")
            if (overlay > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = overlay
                            scaleX = 0.7f + 0.3f * overlay
                            scaleY = 0.7f + 0.3f * overlay
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(isPlaying, Color.White)
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle ?: listOf(song.artist, formatTime(song.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(isFavorite, enter = scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Favorite, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, "Ещё", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SongMenu(menu, isFavorite, onDismiss = { menu = false }) {
                menu = false
                onAction(it)
            }
        }
    }
}

@Composable
fun SongMenu(expanded: Boolean, isFavorite: Boolean, onDismiss: () -> Unit, onAction: (SongAction) -> Unit) {
    DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(16.dp), containerColor = Surface2) {
        MenuItem(Icons.Rounded.QueuePlayNext, "Воспроизвести далее") { onAction(SongAction.PlayNext) }
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Добавить в очередь") { onAction(SongAction.AddToQueue) }
        MenuItem(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "Убрать из избранного" else "В избранное",
        ) { onAction(SongAction.ToggleFavorite) }
        MenuItem(Icons.Rounded.Album, "Перейти к альбому") { onAction(SongAction.GoToAlbum) }
        MenuItem(Icons.Rounded.Person, "Перейти к исполнителю") { onAction(SongAction.GoToArtist) }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

@Composable
fun AlbumCard(album: Album, cover: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.bouncyClick(onClick = onClick)) {
        CoverImage(
            cover, album.name + album.artist,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            RoundedCornerShape(20.dp), 40.dp, Icons.Rounded.Album,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            album.name.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            album.artist.ifBlank { "Неизвестный исполнитель" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistItem(artist: Artist, cover: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.bouncyClick(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverImage(
            cover, artist.name,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            CircleShape, 36.dp, Icons.Rounded.Person,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SongCard(song: Song, cover: String?, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .bouncyClick(onClick = onClick),
    ) {
        Box {
            CoverImage(
                cover, song.album + song.artist,
                Modifier
                    .size(140.dp),
                RoundedCornerShape(20.dp), 36.dp,
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(isPlaying, MaterialTheme.colorScheme.onPrimary, Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun PlayShuffleHeader(title: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
        }
        Button(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Слушать")
        }
    }
}

/** Пустое состояние с «дышащей» иконкой. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val breathe by transition.animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(600), label = "emptyAlpha")
    Column(
        modifier
            .fillMaxWidth()
            .padding(40.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = breathe
                    scaleY = breathe
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Мерцающие заглушки строк, пока библиотека загружается. */
@Composable
fun ShimmerList(rows: Int = 8, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        listOf(Surface1, Surface2.copy(alpha = 1f), Surface1),
        start = Offset(x * 800f, 0f),
        end = Offset(x * 800f + 400f, 0f),
    )
    Column(modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
        repeat(rows) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(brush),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(brush),
                    )
                }
            }
        }
    }
}

/**
 * Позиция трека, обновляемая на каждом кадре. Читайте её внутри draw/graphicsLayer-лямбд —
 * тогда перерисовывается только полоса прогресса, а не весь экран.
 */
@Composable
fun rememberPlaybackPosition(player: AudioPlayer): State<Long> {
    val snapshot by player.position.collectAsState()
    val position = remember { mutableLongStateOf(snapshot.now()) }
    LaunchedEffect(snapshot) {
        position.longValue = snapshot.now()
        while (snapshot.playing && isActive) {
            withFrameMillis { position.longValue = snapshot.now() }
        }
    }
    return position
}

fun formatTime(ms: Long, zero: Boolean = false): String {
    if (ms <= 0) return if (zero) "0:00" else ""
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
