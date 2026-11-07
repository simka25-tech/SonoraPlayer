@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package com.sonora.player.ui

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sonora.player.SongAction
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.Song

/** Наведение мышью как State. */
@Composable
fun rememberHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

/** Курсор-рука. */
fun Modifier.hand(): Modifier = pointerHoverIcon(PointerIcon.Hand)

fun Modifier.hoverableHand(source: MutableInteractionSource): Modifier = hoverable(source).hand()

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun Modifier.handClick(onClick: () -> Unit): Modifier = hand().clickableNoRipple(onClick)

fun Modifier.shadowSoft(shape: Shape): Modifier =
    shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))

/** Есть ли фокус в текстовом поле — тогда пробел и стрелки не управляют плеером. */
object TextFocus {
    var focused by mutableStateOf(false)
}

fun Modifier.trackTextFocus(): Modifier = onFocusChanged { TextFocus.focused = it.isFocused }

/** Строка трека в таблице: номер / обложка, название, альбом, длительность. */
@Composable
fun TrackRow(
    number: Int,
    song: Song,
    cover: String?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showAlbum: Boolean,
    onClick: () -> Unit,
    onAction: (SongAction) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val (hoverSource, hovered) = rememberHover()
    var menu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            isCurrent -> accent.copy(alpha = 0.14f)
            hovered || menu -> Glass.copy(alpha = 0.07f)
            else -> Color.Transparent
        },
        tween(220, easing = Motion.Standard), label = "rowBg",
    )
    val titleColor by animateColorAsState(
        if (isCurrent) accent else MaterialTheme.colorScheme.onSurface, tween(300), label = "rowTitle",
    )
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .hoverable(hoverSource)
                .onPointerEvent(PointerEventType.Press) { e ->
                    if (e.buttons.isSecondaryPressed) {
                        val p = e.changes.first().position
                        menuOffset = with(density) { DpOffset(p.x.toDp(), p.y.toDp() - 60.dp) }
                        menu = true
                    }
                }
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                when {
                    isCurrent -> EqualizerBars(isPlaying, accent, Modifier.size(16.dp))
                    hovered -> Icon(Icons.Rounded.PlayArrow, "Играть", Modifier.size(22.dp))
                    else -> Text("$number", style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
            CoverImage(cover, song.album + song.artist, Modifier.size(42.dp), RoundedCornerShape(8.dp), 18.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
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
                    song.artist.ifBlank { "Неизвестный исполнитель" },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showAlbum) {
                Text(
                    song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(end = 12.dp),
                )
            }
            trailing?.invoke()
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    isFavorite || hovered,
                    enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    SmallIconButton(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFavorite) "Убрать из избранного" else "В избранное",
                        tint = if (isFavorite) accent else muted,
                    ) { onAction(SongAction.ToggleFavorite) }
                }
            }
            Text(
                formatTime(song.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                val a by animateFloatAsState(if (hovered || menu) 1f else 0f, tween(180), label = "moreAlpha")
                SmallIconButton(Icons.Rounded.MoreHoriz, "Ещё", Modifier.graphicsLayer { alpha = a }) {
                    menuOffset = DpOffset.Zero
                    menu = true
                }
            }
        }
        TrackMenu(menu, isFavorite, menuOffset, onDismiss = { menu = false }) {
            menu = false
            onAction(it)
        }
    }
}

@Composable
fun TrackMenu(expanded: Boolean, isFavorite: Boolean, offset: DpOffset, onDismiss: () -> Unit, onAction: (SongAction) -> Unit) {
    DropdownMenu(expanded, onDismiss, offset = offset, shape = RoundedCornerShape(14.dp), containerColor = Surface2) {
        MenuEntry(Icons.Rounded.QueuePlayNext, "Воспроизвести далее") { onAction(SongAction.PlayNext) }
        MenuEntry(Icons.AutoMirrored.Rounded.PlaylistAdd, "Добавить в очередь") { onAction(SongAction.AddToQueue) }
        MenuEntry(
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            if (isFavorite) "Убрать из избранного" else "В избранное",
        ) { onAction(SongAction.ToggleFavorite) }
        MenuEntry(Icons.Rounded.Album, "Перейти к альбому") { onAction(SongAction.GoToAlbum) }
        MenuEntry(Icons.Rounded.Person, "Перейти к исполнителю") { onAction(SongAction.GoToArtist) }
    }
}

@Composable
fun MenuEntry(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        onClick = onClick,
        modifier = Modifier.hand(),
    )
}

/** Заголовок колонок таблицы треков. */
@Composable
fun TrackHeader(showAlbum: Boolean, modifier: Modifier = Modifier, trailingWidth: Dp = 0.dp) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", style = style, color = muted, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp))
            Spacer(Modifier.width(42.dp))
            Text("Название", style = style, color = muted, modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp))
            if (showAlbum) {
                Text("Альбом", style = style, color = muted, modifier = Modifier
                    .weight(0.7f)
                    .padding(end = 12.dp))
            }
            Spacer(Modifier.width(36.dp + trailingWidth))
            Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.Schedule, "Длительность", Modifier.size(16.dp), tint = muted)
            }
            Spacer(Modifier.width(36.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Glass.copy(alpha = 0.08f)),
        )
    }
}

/** Маленькая круглая кнопка-иконка с подсветкой при наведении. */
@Composable
fun SmallIconButton(
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val (src, hovered) = rememberHover()
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(
        when {
            active -> accent.copy(alpha = 0.16f)
            hovered -> Glass.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        tween(180), label = "iconBg",
    )
    val scale by animateFloatAsState(if (hovered) 1.08f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "iconScale")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .hoverable(src)
            .hand()
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(iconSize), tint = if (active) accent else tint)
    }
}

/** Плитка с обложкой: при наведении приподнимается и показывает круглую кнопку «Играть». */
@Composable
fun HoverTile(
    cover: String?,
    seed: String,
    title: String,
    subtitle: String?,
    shape: Shape,
    placeholderIcon: ImageVector,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val (src, hovered) = rememberHover()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(0.7f, Spring.StiffnessMediumLow), label = "lift")
    val bg by animateColorAsState(if (hovered) Glass.copy(alpha = 0.07f) else Color.Transparent, tween(220), label = "tileBg")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .hoverable(src)
            .hand()
            .combinedClickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box {
            CoverImage(
                cover, seed,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationY = -6f * lift
                        scaleX = 1f + 0.02f * lift
                        scaleY = 1f + 0.02f * lift
                    }
                    .shadow((14 * lift).dp, shape, clip = false),
                shape, 40.dp, placeholderIcon,
            )
            if (onPlay != null) {
                PlayBubble(lift, onPlay)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.PlayBubble(progress: Float, onPlay: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val (src, hovered) = rememberHover()
    val press by animateFloatAsState(if (hovered) 1.08f else 1f, spring(0.6f), label = "bubble")
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp)
            .graphicsLayer {
                alpha = progress
                translationY = 14f * (1f - progress)
                scaleX = press
                scaleY = press
            }
            .size(46.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(accent)
            .hoverable(src)
            .combinedClickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, "Играть", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
    }
}

@Composable
fun AlbumTile(album: Album, cover: String?, onClick: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    HoverTile(
        cover, album.name + album.artist,
        album.name.ifBlank { "Без названия" },
        listOf(album.artist.ifBlank { "Неизвестный исполнитель" }, if (album.year > 0) "${album.year}" else "")
            .filter { it.isNotBlank() }.joinToString(" · "),
        RoundedCornerShape(14.dp), Icons.Rounded.Album, onClick, onPlay, modifier,
    )
}

@Composable
fun ArtistTile(artist: Artist, cover: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HoverTile(
        cover, artist.name, artist.name, "Исполнитель", CircleShape, Icons.Rounded.Person,
        onClick, null, modifier, centered = true,
    )
}

/** Карточка трека для горизонтальных лент на главной. */
@Composable
fun TrackTile(song: Song, cover: String?, isCurrent: Boolean, isPlaying: Boolean, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier) {
        HoverTile(
            cover, song.album + song.artist, song.title, song.artist,
            RoundedCornerShape(14.dp), Icons.Rounded.Album, onPlay, onPlay,
        )
        if (isCurrent) {
            Box(
                Modifier
                    .padding(22.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerBars(isPlaying, MaterialTheme.colorScheme.onPrimary, Modifier.size(14.dp))
            }
        }
    }
}

/** Пилюля-кнопка: основная (акцентная) или вторичная (стекло). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val (src, hovered) = rememberHover()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "pill")
    val bg = if (primary) accent else Glass.copy(alpha = if (hovered) 0.14f else 0.08f)
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(44.dp)
            .clip(CircleShape)
            .background(bg)
            .hoverable(src)
            .hand()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Пустой экран на всю область. */
@Composable
fun CenteredEmpty(icon: ImageVector, title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(icon, title, text)
    }
}
