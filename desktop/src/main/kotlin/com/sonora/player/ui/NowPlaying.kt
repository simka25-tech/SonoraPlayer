package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.sonora.player.AppState
import com.sonora.player.SongAction
import com.sonora.player.data.Album
import com.sonora.player.data.Artist

/** Полноэкранный режим «Сейчас играет»: большая обложка, живой фон в цвет трека, очередь справа. */
@Composable
fun NowPlayingScreen(app: AppState, accent: Color) {
    val state by app.player.state.collectAsState()
    val song = state.current
    val tint by animateColorAsState(accent, tween(900), label = "npTint")
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .clickableNoRipple { },
    ) {
        AuroraBackground(listOf(tint, tint.copy(alpha = 0.7f), Violet), Modifier.fillMaxSize(), durationMs = 30000, intensity = 0.5f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showQueue = maxWidth > 1100.dp
            // Обложка занимает всё, что остаётся по высоте после подписей и кнопок.
            val coverSize = min((maxHeight - 400.dp).coerceIn(140.dp, 460.dp), (if (showQueue) maxWidth - 420.dp else maxWidth) * 0.6f)
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SmallIconButton(Icons.Rounded.KeyboardArrowDown, "Свернуть (Esc)", tint = MaterialTheme.colorScheme.onSurface, size = 40.dp, iconSize = 28.dp) {
                            app.nowPlayingOpen = false
                        }
                        Spacer(Modifier.weight(1f))
                        Text("СЕЙЧАС ИГРАЕТ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        SpeedMenu(app, state)
                        Spacer(Modifier.width(6.dp))
                        VolumeControl(app, state, tint, width = 140.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (song != null) {
                        BreathingCover(app.cover(song), song.album + song.artist, song.id, state.isPlaying, coverSize)
                        Spacer(Modifier.height(28.dp))
                        AnimatedContent(
                            song,
                            transitionSpec = { fadeIn(tween(400, 100)) togetherWith fadeOut(tween(200)) },
                            contentKey = { it.id },
                            label = "npTitle",
                        ) { s ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
                                Text(
                                    s.title,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        s.artist,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.handClick { if (s.artist.isNotBlank()) app.onSongAction(s, SongAction.GoToArtist) },
                                    )
                                    if (s.album.isNotBlank()) {
                                        Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            s.album,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.handClick {
                                                app.onSongAction(s, SongAction.GoToAlbum)
                                            },
                                        )
                                    }
                                }
                                val meta = listOfNotNull(
                                    s.format.uppercase().ifBlank { null },
                                    s.bitrate.takeIf { it > 0 }?.let { "$it кбит/с" },
                                    s.year.takeIf { it > 0 }?.toString(),
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        meta,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Glass.copy(alpha = 0.08f))
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        PlaybackTimeline(app, state, tint, Modifier.widthIn(max = 640.dp).fillMaxWidth(), large = true)
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val fav = song.id in app.favoriteIds
                            AnimatedContent(fav, transitionSpec = { scaleIn(spring(0.45f)) togetherWith scaleOut(tween(120)) }, label = "npFav") { f ->
                                SmallIconButton(
                                    if (f) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное",
                                    tint = if (f) tint else MaterialTheme.colorScheme.onSurfaceVariant, size = 44.dp, iconSize = 24.dp,
                                ) { app.onSongAction(song, SongAction.ToggleFavorite) }
                            }
                            Spacer(Modifier.width(24.dp))
                            TransportControls(app, state, tint, large = true)
                            Spacer(Modifier.width(24.dp))
                            SleepMenu(app, state)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
                if (showQueue) {
                    Box(
                        Modifier
                            .padding(24.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Glass.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
                    ) {
                        QueuePanel(app, Modifier.graphicsLayer { alpha = 0.97f })
                    }
                }
            }
        }
    }
}

@Composable
private fun BreathingCover(url: String?, seed: String, key: String, playing: Boolean, size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "breathe")
    val breathe by transition.animateFloat(
        1f, 1.025f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breatheScale",
    )
    val active by animateFloatAsState(if (playing) 1f else 0f, tween(600, easing = Motion.Standard), label = "coverActive")
    val (src, _) = rememberHover()
    AnimatedContent(
        key,
        transitionSpec = {
            (scaleIn(tween(500, easing = Motion.Decelerate), initialScale = 0.85f) + fadeIn(tween(400))) togetherWith
                (scaleOut(tween(300), targetScale = 1.05f) + fadeOut(tween(250)))
        },
        label = "npCover",
    ) { _ ->
        val shape = RoundedCornerShape(28.dp)
        CoverImage(
            url, seed,
            Modifier
                .size(size)
                .graphicsLayer {
                    val s = 0.92f + 0.08f * active + (breathe - 1f) * active
                    scaleX = s
                    scaleY = s
                }
                .shadowSoft(shape)
                .hoverable(src),
            shape, 96.dp, Icons.Rounded.Album,
        )
    }
}
