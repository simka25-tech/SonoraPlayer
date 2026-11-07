@file:OptIn(ExperimentalFoundationApi::class)

package com.sonora.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import com.sonora.player.Screen
import com.sonora.player.Section
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.Song
import java.time.LocalTime

internal val PagePadding = 32.dp

@Composable
fun ScreenContent(app: AppState, screen: Screen) {
    when (screen) {
        is Screen.Root -> when (screen.section) {
            Section.Home -> HomeScreen(app)
            Section.Search -> SearchScreen(app)
            Section.Songs -> SongsScreen(app)
            Section.Albums -> AlbumsScreen(app)
            Section.Artists -> ArtistsScreen(app)
            Section.Favorites -> FavoritesScreen(app)
            Section.History -> HistoryScreen(app)
            Section.Online -> OnlineScreen(app)
            Section.Downloads -> DownloadsScreen(app)
        }
        is Screen.AlbumDetail -> AlbumScreen(app, screen)
        is Screen.ArtistDetail -> ArtistScreen(app, screen)
        is Screen.OnlineRelease -> ReleaseScreen(app, screen)
    }
}

// ---------- Общие куски ----------

@Composable
internal fun ScrollingList(
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = PagePadding - 12.dp, end = PagePadding - 4.dp, top = 8.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
        VerticalScrollbar(
            rememberScrollbarAdapter(state),
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            style = sonoraScrollbar(),
        )
    }
}

@Composable
fun sonoraScrollbar() = ScrollbarStyle(
    minimalHeight = 48.dp,
    thickness = 6.dp,
    shape = CircleShape,
    hoverDurationMillis = 250,
    unhoverColor = Glass.copy(alpha = 0.14f),
    hoverColor = Glass.copy(alpha = 0.32f),
)

@Composable
internal fun PageTitle(title: String, subtitle: String?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun PlayActions(onPlay: () -> Unit, onShuffle: () -> Unit) {
    PillButton("Перемешать", Icons.Rounded.Shuffle, primary = false, onClick = onShuffle)
    PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true, onClick = onPlay)
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            val (src, hovered) = rememberHover()
            val color by animateColorAsState(
                if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(180), label = "sectionAction",
            )
            Text(
                action,
                color = color,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .hoverableHand(src)
                    .clickableNoRipple(onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

internal fun LazyListScope.tracks(
    app: AppState,
    list: List<Song>,
    showAlbum: Boolean,
    numberOf: (Int, Song) -> Int = { i, _ -> i + 1 },
    trailing: (@Composable (Song) -> Unit)? = null,
) {
    itemsIndexed(list, key = { i, s -> "${s.id}#$i" }) { i, song ->
        val state by app.player.state.collectAsState()
        val isCurrent = state.current?.id == song.id
        TrackRow(
            number = numberOf(i, song),
            song = song,
            cover = app.cover(song),
            isCurrent = isCurrent,
            isPlaying = isCurrent && state.isPlaying,
            isFavorite = song.id in app.favoriteIds,
            showAlbum = showAlbum,
            onClick = { app.play(list, i) },
            onAction = { app.onSongAction(song, it) },
            modifier = Modifier.animateItem(),
            trailing = trailing?.let { t -> { t(song) } },
        )
    }
}

@Composable
private fun LoadingOrError(app: AppState, empty: Boolean, content: @Composable () -> Unit) {
    when {
        app.libraryLoading && empty -> Column(Modifier.padding(horizontal = PagePadding, vertical = 24.dp)) { ShimmerList(10) }
        app.libraryError != null && empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Rounded.CloudOff, "Не удалось загрузить библиотеку", app.libraryError.orEmpty())
                PillButton("Повторить", Icons.Rounded.Refresh, primary = true, onClick = app::loadLibrary)
            }
        }
        else -> content()
    }
}

// ---------- Главная ----------

@Composable
private fun HomeScreen(app: AppState) {
    val state by app.player.state.collectAsState()
    val recent = remember(app.stats) { app.stats.sortedByDescending { it.lastPlayed }.take(20).map { it.song } }
    val top = remember(app.stats) { app.stats.sortedByDescending { it.count }.take(20).map { it.song } }
    ScrollingList {
        item { HomeHero(app) }
        if (recent.isNotEmpty()) {
            item { SectionTitle("Недавно прослушанные", "История") { app.select(Section.History) } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(recent, key = { _, s -> s.id }) { i, song ->
                        TrackTile(
                            song, app.cover(song), state.current?.id == song.id, state.isPlaying,
                            onPlay = { app.play(recent, i) },
                            modifier = Modifier.width(184.dp),
                        )
                    }
                }
            }
        }
        if (top.size >= 3) {
            item { SectionTitle("Чаще всего") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(top, key = { _, s -> s.id }) { i, song ->
                        TrackTile(
                            song, app.cover(song), state.current?.id == song.id, state.isPlaying,
                            onPlay = { app.play(top, i) },
                            modifier = Modifier.width(184.dp),
                        )
                    }
                }
            }
        }
        if (app.albums.isNotEmpty()) {
            item { SectionTitle("Альбомы", "Все альбомы") { app.select(Section.Albums) } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(app.albums.take(24), key = { _, a -> a.name + a.artist }) { _, album ->
                        AlbumTile(album, app.albumCover(album), { app.openAlbum(album) }, { playAlbum(app, album) }, Modifier.width(184.dp))
                    }
                }
            }
        }
        if (app.artists.isNotEmpty()) {
            item { SectionTitle("Исполнители", "Все исполнители") { app.select(Section.Artists) } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(app.artists.take(24), key = { _, a -> a.name }) { _, artist ->
                        ArtistTile(artist, app.artistCover(artist), { app.openArtist(artist) }, Modifier.width(164.dp))
                    }
                }
            }
        }
        if (app.libraryLoading && app.songs.isEmpty()) {
            item { ShimmerList(6) }
        }
    }
}

@Composable
private fun HomeHero(app: AppState) {
    val accent = MaterialTheme.colorScheme.primary
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 24.dp)
            .fillMaxWidth()
            .height(220.dp)
            .clip(shape)
            .border(1.dp, Glass.copy(alpha = 0.08f), shape),
    ) {
        AuroraBackground(listOf(accent, Violet, Teal), Modifier.fillMaxSize(), durationMs = 26000, intensity = 0.6f)
        Row(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    app.source?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Перемешать всё", Icons.Rounded.Shuffle, primary = true, onClick = app::shuffleAll)
                    PillButton("Избранное", Icons.Rounded.Favorite, primary = false) { app.select(Section.Favorites) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatBubble("${app.songsTotal}", "песен", Icons.Rounded.MusicNote)
                StatBubble("${app.albums.size}", "альбомов", Icons.Rounded.Album)
                StatBubble("${app.artists.size}", "исполнителей", Icons.Rounded.Person)
            }
        }
    }
}

@Composable
private fun StatBubble(value: String, label: String, icon: ImageVector) {
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg.copy(alpha = 0.45f))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun playAlbum(app: AppState, album: Album) {
    val cached = app.detailSongs[Screen.AlbumDetail(album)]
    if (cached != null && cached.isNotEmpty()) app.play(cached) else {
        app.openAlbum(album)
        app.notify("Открываю альбом — нажмите «Слушать»")
    }
}

// ---------- Песни ----------

@Composable
private fun SongsScreen(app: AppState) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 20
        }
    }
    LaunchedEffect(nearEnd, app.songs.size) { if (nearEnd) app.loadMoreSongs() }
    LoadingOrError(app, app.songs.isEmpty()) {
        BoxWithConstraints {
            val showAlbum = maxWidth > 720.dp
            ScrollingList(listState) {
                item {
                    PageTitle("Песни", plural(app.songsTotal, "песня", "песни", "песен")) {
                        PlayActions({ app.play(app.songs) }, app::shuffleAll)
                    }
                }
                stickyHeader { TrackHeader(showAlbum, Modifier.background(Bg)) }
                tracks(app, app.songs, showAlbum)
                if (app.songsLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Альбомы и исполнители ----------

@Composable
private fun AlbumsScreen(app: AppState) {
    LoadingOrError(app, app.albums.isEmpty()) {
        if (app.albums.isEmpty()) {
            CenteredEmpty(Icons.Rounded.Album, "Альбомов нет", "Добавьте музыку в Audio Station")
            return@LoadingOrError
        }
        val state = rememberLazyGridState()
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(190.dp),
                state = state,
                contentPadding = PaddingValues(start = PagePadding - 12.dp, end = PagePadding - 4.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PageTitle("Альбомы", plural(app.albums.size, "альбом", "альбома", "альбомов"))
                }
                itemsIndexed(app.albums, key = { _, a -> a.name + "\u0000" + a.artist }) { _, album ->
                    AlbumTile(album, app.albumCover(album), { app.openAlbum(album) }, { playAlbum(app, album) })
                }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(state),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
                style = sonoraScrollbar(),
            )
        }
    }
}

@Composable
private fun ArtistsScreen(app: AppState) {
    LoadingOrError(app, app.artists.isEmpty()) {
        if (app.artists.isEmpty()) {
            CenteredEmpty(Icons.Rounded.Person, "Исполнителей нет", "Добавьте музыку в Audio Station")
            return@LoadingOrError
        }
        val state = rememberLazyGridState()
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(170.dp),
                state = state,
                contentPadding = PaddingValues(start = PagePadding - 12.dp, end = PagePadding - 4.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PageTitle("Исполнители", plural(app.artists.size, "исполнитель", "исполнителя", "исполнителей"))
                }
                itemsIndexed(app.artists, key = { _, a -> a.name }) { _, artist ->
                    ArtistTile(artist, app.artistCover(artist), { app.openArtist(artist) })
                }
            }
            VerticalScrollbar(
                rememberScrollbarAdapter(state),
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
                style = sonoraScrollbar(),
            )
        }
    }
}

// ---------- Избранное и история ----------

@Composable
private fun FavoritesScreen(app: AppState) {
    val list = app.favorites
    if (list.isEmpty()) {
        CenteredEmpty(Icons.Rounded.Favorite, "Здесь пока пусто", "Наведите на трек и нажмите ♥, чтобы добавить его в избранное")
        return
    }
    BoxWithConstraints {
        val showAlbum = maxWidth > 720.dp
        ScrollingList {
            item {
                PageTitle("Избранное", plural(list.size, "песня", "песни", "песен")) {
                    PlayActions({ app.play(list) }, { app.play(list, shuffle = true) })
                }
            }
            stickyHeader { TrackHeader(showAlbum, Modifier.background(Bg)) }
            tracks(app, list, showAlbum)
        }
    }
}

@Composable
private fun HistoryScreen(app: AppState) {
    val stats = remember(app.stats) { app.stats.sortedByDescending { it.lastPlayed } }
    if (stats.isEmpty()) {
        CenteredEmpty(Icons.Rounded.History, "История пуста", "Здесь появятся треки, которые вы слушали")
        return
    }
    val list = remember(stats) { stats.map { it.song } }
    val counts = remember(stats) { stats.associate { it.song.id to it.count } }
    val total = remember(stats) { stats.sumOf { it.count } }
    BoxWithConstraints {
        val showAlbum = maxWidth > 820.dp
        ScrollingList {
            item {
                PageTitle("История", "${plural(list.size, "трек", "трека", "треков")} · ${plural(total, "прослушивание", "прослушивания", "прослушиваний")}") {
                    PillButton("Очистить", Icons.Rounded.DeleteSweep, primary = false, onClick = app::clearHistory)
                    PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true) { app.play(list) }
                }
            }
            stickyHeader { TrackHeader(showAlbum, Modifier.background(Bg)) }
            tracks(app, list, showAlbum, trailing = { song ->
                Text(
                    "×${counts[song.id] ?: 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            })
        }
    }
}

// ---------- Поиск ----------

@Composable
private fun SearchScreen(app: AppState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(app.searchFocusTick) { runCatching { focus.requestFocus() } }
    val result = app.searchResult
    Column(Modifier.fillMaxSize()) {
        SearchField(app.query, app::onQuery, focus, app.searching)
        Box(Modifier.weight(1f)) {
            when {
                app.query.isBlank() -> CenteredEmpty(Icons.Rounded.Search, "Что послушаем?", "Ищите песни, альбомы и исполнителей. Ctrl+F — быстрый поиск")
                result == null -> Unit
                result.isEmpty -> CenteredEmpty(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос")
                else -> BoxWithConstraints {
                    val showAlbum = maxWidth > 720.dp
                    ScrollingList {
                        if (result.artists.isNotEmpty()) {
                            item { SectionTitle("Исполнители") }
                            item {
                                LazyRow {
                                    itemsIndexed(result.artists, key = { _, a -> a.name }) { _, artist ->
                                        ArtistTile(artist, app.artistCover(artist), { app.openArtist(artist) }, Modifier.width(150.dp))
                                    }
                                }
                            }
                        }
                        if (result.albums.isNotEmpty()) {
                            item { SectionTitle("Альбомы") }
                            item {
                                LazyRow {
                                    itemsIndexed(result.albums, key = { _, a -> a.name + a.artist }) { _, album ->
                                        AlbumTile(album, app.albumCover(album), { app.openAlbum(album) }, { playAlbum(app, album) }, Modifier.width(170.dp))
                                    }
                                }
                            }
                        }
                        if (result.songs.isNotEmpty()) {
                            item { SectionTitle("Песни") }
                            tracks(app, result.songs, showAlbum)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    focus: FocusRequester,
    searching: Boolean,
    placeholder: String = "Песни, альбомы, исполнители",
    onSubmit: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = CircleShape
    Row(
        Modifier
            .padding(start = PagePadding, end = PagePadding, top = 28.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(Glass.copy(alpha = 0.07f))
            .border(1.dp, Glass.copy(alpha = 0.1f), shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = accent)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                query, onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .trackTextFocus()
                    .onPreviewKeyEvent { e ->
                        if (onSubmit != null && e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        AnimatedVisibility(searching, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        AnimatedVisibility(query.isNotEmpty() && !searching, enter = fadeIn(), exit = fadeOut()) {
            SmallIconButton(Icons.Rounded.Close, "Очистить") { onQuery("") }
        }
    }
}

// ---------- Альбом и исполнитель ----------

@Composable
private fun AlbumScreen(app: AppState, screen: Screen.AlbumDetail) {
    val album = screen.album
    val songs = app.detailSongs[screen]
    val cover = app.albumCover(album)
    val totalMs = songs?.sumOf { it.durationMs } ?: 0
    DetailPage(
        app = app,
        cover = cover,
        seed = album.name + album.artist,
        circle = false,
        label = "АЛЬБОМ",
        title = album.name.ifBlank { "Без названия" },
        meta = listOfNotNull(
            album.artist.ifBlank { null },
            album.year.takeIf { it > 0 }?.toString(),
            songs?.let { plural(it.size, "песня", "песни", "песен") },
            totalMs.takeIf { it > 0 }?.let { formatDuration(it) },
        ).joinToString(" · "),
        songs = songs,
        showAlbum = false,
        numberOf = { i, s -> if (s.track > 0) s.track else i + 1 },
        onMetaClick = album.artist.takeIf { it.isNotBlank() }?.let { { app.openArtist(Artist(it)) } },
    )
}

@Composable
private fun ArtistScreen(app: AppState, screen: Screen.ArtistDetail) {
    val artist = screen.artist
    val songs = app.detailSongs[screen]
    val albums = remember(songs) {
        songs.orEmpty().filter { it.album.isNotBlank() }
            .distinctBy { it.album }
            .map { Album(it.album, it.albumArtist.ifBlank { it.artist }, it.year) }
    }
    DetailPage(
        app = app,
        cover = app.artistCover(artist),
        seed = artist.name,
        circle = true,
        label = "ИСПОЛНИТЕЛЬ",
        title = artist.name,
        meta = listOfNotNull(
            songs?.let { plural(it.size, "песня", "песни", "песен") },
            albums.size.takeIf { it > 0 }?.let { plural(it, "альбом", "альбома", "альбомов") },
        ).joinToString(" · "),
        songs = songs,
        showAlbum = true,
        extra = if (albums.isEmpty()) null else {
            {
                SectionTitle("Альбомы")
                LazyRow {
                    itemsIndexed(albums, key = { _, a -> a.name }) { _, album ->
                        AlbumTile(album, app.albumCover(album), { app.openAlbum(album) }, { playAlbum(app, album) }, Modifier.width(170.dp))
                    }
                }
                SectionTitle("Песни")
            }
        },
    )
}

@Composable
private fun DetailPage(
    app: AppState,
    cover: String?,
    seed: String,
    circle: Boolean,
    label: String,
    title: String,
    meta: String,
    songs: List<Song>?,
    showAlbum: Boolean,
    numberOf: (Int, Song) -> Int = { i, _ -> i + 1 },
    onMetaClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val accent by rememberArtworkAccent(cover, IsDark)
    val tint by animateColorAsState(accent, tween(700), label = "detailTint")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 720.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.38f), Color.Transparent))),
        )
        ScrollingList {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 68.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val shape = if (circle) CircleShape else RoundedCornerShape(18.dp)
                    CoverImage(
                        cover, seed,
                        Modifier
                            .size(if (wide) 220.dp else 160.dp)
                            .shadowSoft(shape),
                        shape, 64.dp, if (circle) Icons.Rounded.Person else Icons.Rounded.Album,
                    )
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            title,
                            style = if (wide) MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black)
                            else MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (onMetaClick != null) Modifier.handClick(onMetaClick) else Modifier,
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true) { songs?.let { app.play(it) } }
                            PillButton("Перемешать", Icons.Rounded.Shuffle, primary = false) { songs?.let { app.play(it, shuffle = true) } }
                        }
                    }
                }
            }
            if (songs == null) {
                item { ShimmerList(8) }
            } else if (songs.isEmpty()) {
                item { EmptyState(Icons.Rounded.LibraryMusic, "Песен не найдено", "Возможно, библиотека ещё индексируется") }
            } else {
                if (extra != null) item { extra() }
                stickyHeader { TrackHeader(showAlbum && wide, Modifier.background(Bg)) }
                tracks(app, songs, showAlbum && wide, numberOf)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val min = ms / 60000
    return if (min >= 60) "${min / 60} ч ${min % 60} мин" else "$min мин"
}
