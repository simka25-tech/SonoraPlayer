package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonora.player.MainViewModel
import com.sonora.player.Screen
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.Song

@Composable
fun AlbumScreen(vm: MainViewModel, album: Album, bottomPad: Dp) {
    val songs = vm.detailSongs[Screen.AlbumDetail(album)]
    val total = songs?.sumOf { it.durationMs } ?: 0
    val meta = listOfNotNull(
        album.year.takeIf { it > 0 }?.toString(),
        songs?.let { plural(it.size, "трек", "трека", "треков") },
        formatTime(total).takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
    DetailList(
        vm = vm,
        songs = songs,
        bottomPad = bottomPad,
        numbered = true,
        header = { state ->
            DetailHeader(
                state = state,
                cover = vm.albumCover(album),
                seed = album.name + album.artist,
                title = album.name.ifBlank { "Без названия" },
                subtitle = album.artist,
                meta = meta,
                circle = false,
                onBack = vm::back,
                onSubtitle = { vm.openArtist(Artist(album.artist)) },
                onPlay = { songs?.let { vm.play(it) } },
                onShuffle = { songs?.let { vm.play(it, shuffle = true) } },
            )
        },
    )
}

@Composable
fun ArtistScreen(vm: MainViewModel, artist: Artist, bottomPad: Dp) {
    val songs = vm.detailSongs[Screen.ArtistDetail(artist)]
    val artistAlbums = remember(vm.albums, artist) { vm.albums.filter { it.artist.equals(artist.name, ignoreCase = true) } }
    DetailList(
        vm = vm,
        songs = songs,
        bottomPad = bottomPad,
        numbered = false,
        header = { state ->
            DetailHeader(
                state = state,
                cover = vm.artistCover(artist),
                seed = artist.name,
                title = artist.name,
                subtitle = null,
                meta = listOfNotNull(
                    songs?.let { plural(it.size, "песня", "песни", "песен") },
                    artistAlbums.size.takeIf { it > 0 }?.let { plural(it, "альбом", "альбома", "альбомов") },
                ).joinToString(" · "),
                circle = true,
                onBack = vm::back,
                onSubtitle = null,
                onPlay = { songs?.let { vm.play(it) } },
                onShuffle = { songs?.let { vm.play(it, shuffle = true) } },
            )
        },
        extra = {
            if (artistAlbums.isNotEmpty()) {
                Column {
                    SectionHeader("Альбомы")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(artistAlbums) { a -> AlbumCard(a, vm.albumCover(a), Modifier.width(140.dp)) { vm.openAlbum(a) } }
                    }
                    SectionHeader("Песни")
                }
            }
        },
    )
}

@Composable
private fun DetailList(
    vm: MainViewModel,
    songs: List<Song>?,
    bottomPad: Dp,
    numbered: Boolean,
    header: @Composable (LazyListState) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val p by vm.player.state.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomPad)) {
        item { header(state) }
        if (extra != null) item { extra() }
        when {
            songs == null -> item { ShimmerList(rows = 5) }
            songs.isEmpty() -> item { EmptyState(Icons.Rounded.SearchOff, "Нет треков", "Здесь пока ничего нет") }
            else -> itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    song = s,
                    cover = vm.cover(s),
                    isCurrent = p.current?.id == s.id,
                    isPlaying = p.isPlaying,
                    isFavorite = s.id in favIds,
                    onClick = { vm.play(songs, i) },
                    onAction = { vm.onSongAction(s, it) },
                    modifier = Modifier.animateItem(),
                    leading = if (numbered) {
                        {
                            Text(
                                "${if (s.track > 0) s.track else i + 1}",
                                Modifier.width(28.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

/** Шапка с параллаксом: размытый фон и обложка, которая уменьшается при прокрутке. */
@Composable
private fun DetailHeader(
    state: LazyListState,
    cover: String?,
    seed: String,
    title: String,
    subtitle: String?,
    meta: String,
    circle: Boolean,
    onBack: () -> Unit,
    onSubtitle: (() -> Unit)?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(24.dp)
    val icon: ImageVector = if (circle) Icons.Rounded.Person else Icons.Rounded.Album
    val offset = { if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else 1000f }
    Box(Modifier.fillMaxWidth()) {
        CoverImage(
            cover, seed,
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationY = offset() * 0.5f
                    alpha = 0.45f
                }
                .blur(40.dp),
            RectangleShape, 0.dp,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Bg.copy(alpha = 0.2f), Bg))),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverImage(
                cover, seed,
                Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        val s = (1f - offset() / 1400f).coerceIn(0.75f, 1f)
                        scaleX = s
                        scaleY = s
                        translationY = offset() * 0.35f
                        alpha = (1f - offset() / 900f).coerceIn(0f, 1f)
                    }
                    .shadow(28.dp, shape),
                shape, 72.dp, icon,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier),
                )
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onShuffle, modifier = Modifier.height(50.dp)) {
                    Icon(Icons.Rounded.Shuffle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Перемешать")
                }
                Button(onClick = onPlay, modifier = Modifier.height(50.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Слушать")
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Bg.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
        }
    }
}

@Composable
fun SearchScreen(vm: MainViewModel, bottomPad: Dp) {
    val focus = remember { FocusRequester() }
    val p by vm.player.state.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            TextField(
                value = vm.query,
                onValueChange = vm::onQuery,
                placeholder = { Text("Песни, альбомы, исполнители") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQuery("") }) { Icon(Icons.Rounded.Close, "Очистить") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Surface2,
                    unfocusedContainerColor = Surface2,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .padding(horizontal = 24.dp),
        ) {
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val result = vm.searchResult
        val mode = when {
            vm.query.isBlank() -> 0
            result == null -> 1
            result.isEmpty -> 2
            else -> 3
        }
        AnimatedContent(
            mode,
            transitionSpec = { fadeIn(tween(300, 60, Motion.Standard)) togetherWith fadeOut(tween(150, easing = Motion.Accelerate)) },
            label = "searchMode",
        ) { m ->
            when (m) {
                0 -> EmptyState(Icons.Rounded.Search, "Найдите что угодно", "Начните вводить название песни, альбома или имя исполнителя")
                1 -> ShimmerList(rows = 5)
                2 -> EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос")
                else -> {
                    val r = vm.searchResult ?: return@AnimatedContent
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
                        if (r.artists.isNotEmpty()) {
                            item { SectionHeader("Исполнители") }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(r.artists) { a -> ArtistItem(a, vm.artistCover(a), Modifier.width(100.dp)) { vm.openArtist(a) } }
                                }
                            }
                        }
                        if (r.albums.isNotEmpty()) {
                            item { SectionHeader("Альбомы") }
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(r.albums) { a -> AlbumCard(a, vm.albumCover(a), Modifier.width(140.dp)) { vm.openAlbum(a) } }
                                }
                            }
                        }
                        if (r.songs.isNotEmpty()) {
                            item { SectionHeader("Песни") }
                            itemsIndexed(r.songs, key = { _, s -> s.id }) { i, s ->
                                SongRow(
                                    song = s,
                                    cover = vm.cover(s),
                                    isCurrent = p.current?.id == s.id,
                                    isPlaying = p.isPlaying,
                                    isFavorite = s.id in favIds,
                                    onClick = { vm.play(r.songs, i) },
                                    onAction = { vm.onSongAction(s, it) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
