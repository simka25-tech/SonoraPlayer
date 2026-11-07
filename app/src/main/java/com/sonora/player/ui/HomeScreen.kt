@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.ui.graphics.lerp
import com.sonora.player.data.ThemeMode
import kotlin.math.abs
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonora.player.MainViewModel
import com.sonora.player.data.SynologySource
import kotlinx.coroutines.launch
import java.util.Calendar

private val tabs = listOf("Главная", "Песни", "Альбомы", "Исполнители", "Избранное")

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

@Composable
fun HomeScreen(vm: MainViewModel, bottomPad: Dp) {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                Text(
                    vm.source?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemeToggle(vm)
            IconButton(onClick = vm::openSearch) { Icon(Icons.Rounded.Search, "Поиск") }
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Настройки") }
        }
        PillTabs(tabs, pager) {
            scope.launch { pager.animateScrollToPage(it, animationSpec = tween(450, easing = Motion.Emphasized)) }
        }
        AnimatedVisibility(vm.libraryError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            ErrorBanner(vm.libraryError.orEmpty(), vm::loadLibrary)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> OverviewPage(vm, bottomPad)
                1 -> SongsPage(vm, bottomPad)
                2 -> AlbumsPage(vm, bottomPad)
                3 -> ArtistsPage(vm, bottomPad)
                else -> FavoritesPage(vm, bottomPad)
            }
        }
    }
    if (showSettings) SettingsSheet(vm) { showSettings = false }
}

/** Вкладки, цвет которых непрерывно следует за положением пейджера при свайпе. */
@Composable
private fun PillTabs(titles: List<String>, pager: PagerState, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(pager.targetPage) {
        listState.animateScrollToItem((pager.targetPage - 1).coerceAtLeast(0))
    }
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.surfaceVariant
    val onIdle = MaterialTheme.colorScheme.onSurfaceVariant
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(titles) { i, title ->
            val fraction by remember(pager, i) {
                derivedStateOf {
                    (1f - abs(pager.currentPage + pager.currentPageOffsetFraction - i)).coerceIn(0f, 1f)
                }
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .drawBehind { drawRect(lerp(idle, accent, fraction)) }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    title,
                    color = lerp(onIdle, onAccent, fraction),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ThemeToggle(vm: MainViewModel) {
    val dark = IsDark
    IconButton(onClick = { vm.setThemeMode(if (dark) ThemeMode.Light else ThemeMode.Dark) }) {
        AnimatedContent(
            dark,
            transitionSpec = {
                (fadeIn(tween(300, easing = Motion.Standard)) + scaleIn(tween(400, easing = Motion.Decelerate), initialScale = 0.5f) +
                    slideInVertically(tween(400, easing = Motion.Decelerate)) { it / 2 }) togetherWith
                    (fadeOut(tween(200)) + slideOutVertically(tween(250, easing = Motion.Accelerate)) { -it / 2 })
            },
            label = "themeIcon",
        ) { isDark ->
            Icon(if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, "Тема")
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.error)
        Text(message, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Повторить")
        }
    }
}

@Composable
private fun OverviewPage(vm: MainViewModel, bottomPad: Dp) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val p by vm.player.state.collectAsStateWithLifecycle()
    val recent = remember(stats) { stats.sortedByDescending { it.lastPlayed }.take(15).map { it.song } }
    val top = remember(stats) { stats.filter { it.count > 1 }.sortedByDescending { it.count }.take(5) }
    val picks = remember(vm.albums) { vm.albums.shuffled().take(12) }
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { LuckyCard(enabled = vm.songs.isNotEmpty(), onClick = vm::shuffleAll) }

        if (vm.libraryLoading && vm.songs.isEmpty()) {
            item { ShimmerList(rows = 4) }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader("Недавно прослушанные") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(recent, key = { _, s -> s.id }) { i, s ->
                        SongCard(s, vm.cover(s), p.current?.id == s.id, p.isPlaying) { vm.play(recent, i) }
                    }
                }
            }
        }

        if (top.isNotEmpty()) {
            item { SectionHeader("Чаще всего") }
            itemsIndexed(top, key = { _, st -> "top-" + st.song.id }) { i, st ->
                SongRow(
                    song = st.song,
                    cover = vm.cover(st.song),
                    isCurrent = p.current?.id == st.song.id,
                    isPlaying = p.isPlaying,
                    isFavorite = st.song.id in favIds,
                    onClick = { vm.play(top.map { it.song }, i) },
                    onAction = { vm.onSongAction(st.song, it) },
                    subtitle = "${st.song.artist} · ${plural(st.count, "прослушивание", "прослушивания", "прослушиваний")}",
                    leading = {
                        Text(
                            "${i + 1}",
                            Modifier.width(30.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    },
                )
            }
        }

        if (picks.isNotEmpty()) {
            item { SectionHeader("Альбомы для вас") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(picks) { album ->
                        AlbumCard(album, vm.albumCover(album), Modifier.width(150.dp)) { vm.openAlbum(album) }
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item { SectionHeader("Избранное") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(favorites.take(20), key = { _, s -> "fav-" + s.id }) { i, s ->
                        SongCard(s, vm.cover(s), p.current?.id == s.id, p.isPlaying) { vm.play(favorites, i) }
                    }
                }
            }
        }

        if (!vm.libraryLoading && recent.isEmpty() && vm.songs.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, "Библиотека пуста", "Добавьте музыку в Audio Station на вашем NAS")
            }
        } else if (recent.isEmpty() && !vm.libraryLoading) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, "Здесь появится история", "Включите любой трек — Sonora запомнит, что вы слушаете")
            }
        }
    }
}

/** Карточка «Мне повезёт» с переливающимся градиентом. */
@Composable
private fun LuckyCard(enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "lucky")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4500), RepeatMode.Reverse), label = "luckyShift")
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(128.dp)
            .bouncyClick(onClick = { if (enabled) onClick() })
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(accent, Violet, Teal),
                        start = Offset(size.width * shift - size.width, 0f),
                        end = Offset(size.width * shift + size.width, size.height),
                    ),
                )
            }
            .padding(22.dp),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("Мне повезёт", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Случайный микс из вашей библиотеки",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Icon(
            Icons.Rounded.Shuffle, null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(52.dp)
                .graphicsLayer { rotationZ = (shift - 0.5f) * 20f },
        )
    }
}

@Composable
private fun SongsPage(vm: MainViewModel, bottomPad: Dp) {
    val p by vm.player.state.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    val songs = vm.songs
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 15
        }
    }
    LaunchedEffect(nearEnd, songs.size) { if (nearEnd) vm.loadMoreSongs() }

    if (songs.isEmpty()) {
        if (vm.libraryLoading) ShimmerList() else EmptyState(Icons.Rounded.LibraryMusic, "Нет песен", "В библиотеке пока ничего нет")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            PlayShuffleHeader(
                plural(vm.songsTotal, "песня", "песни", "песен"),
                onPlay = { vm.play(songs, 0) },
                onShuffle = { vm.play(songs, shuffle = true) },
            )
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                cover = vm.cover(s),
                isCurrent = p.current?.id == s.id,
                isPlaying = p.isPlaying,
                isFavorite = s.id in favIds,
                onClick = { vm.play(songs, i) },
                onAction = { vm.onSongAction(s, it) },
                modifier = Modifier.animateItem(),
            )
        }
        if (vm.songsLoading) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun AlbumsPage(vm: MainViewModel, bottomPad: Dp) {
    if (vm.albums.isEmpty()) {
        if (vm.libraryLoading) ShimmerList() else EmptyState(Icons.Rounded.LibraryMusic, "Нет альбомов", "Альбомы появятся, когда в библиотеке будет музыка")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(vm.albums) { album ->
            AlbumCard(album, vm.albumCover(album)) { vm.openAlbum(album) }
        }
    }
}

@Composable
private fun ArtistsPage(vm: MainViewModel, bottomPad: Dp) {
    if (vm.artists.isEmpty()) {
        if (vm.libraryLoading) ShimmerList() else EmptyState(Icons.Rounded.LibraryMusic, "Нет исполнителей", "Исполнители появятся вместе с музыкой")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(vm.artists) { artist ->
            ArtistItem(artist, vm.artistCover(artist)) { vm.openArtist(artist) }
        }
    }
}

@Composable
private fun FavoritesPage(vm: MainViewModel, bottomPad: Dp) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val p by vm.player.state.collectAsStateWithLifecycle()
    if (favorites.isEmpty()) {
        EmptyState(
            Icons.Rounded.FavoriteBorder,
            "Пока нет избранного",
            "Нажмите на сердечко в плеере или в меню песни, чтобы сохранить её здесь",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            PlayShuffleHeader(
                plural(favorites.size, "песня", "песни", "песен"),
                onPlay = { vm.play(favorites, 0) },
                onShuffle = { vm.play(favorites, shuffle = true) },
            )
        }
        itemsIndexed(favorites, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                cover = vm.cover(s),
                isCurrent = p.current?.id == s.id,
                isPlaying = p.isPlaying,
                isFavorite = true,
                onClick = { vm.play(favorites, i) },
                onAction = { vm.onSongAction(s, it) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface1,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
            SettingRow(Icons.Rounded.Cloud, "Источник", vm.source?.title.orEmpty())
            ThemeSelector(settings.themeMode, vm::setThemeMode)
            SettingRow(Icons.Rounded.Palette, "Цвета из обложки", "Интерфейс подстраивается под текущий трек") {
                Switch(settings.dynamicColors, vm::setDynamicColors)
            }
            if (vm.source is SynologySource) {
                SettingRow(Icons.Rounded.Transform, "Перекодировать в MP3", "Меньше трафика, играет любые форматы. Для новых треков в очереди") {
                    Switch(settings.transcode, vm::setTranscode)
                }
            }
            SettingRow(Icons.Rounded.DeleteSweep, "Очистить историю", "Недавние и «Чаще всего»", onClick = vm::clearHistory)
            SettingRow(
                Icons.AutoMirrored.Rounded.Logout, "Выйти", "Вернуться к экрану входа",
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onDismiss()
                    vm.logout()
                },
            )
            Text(
                "Sonora 1.0",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Сегментированный выбор темы с плавно скользящим индикатором. */
@Composable
private fun ThemeSelector(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.System, "Системная", Icons.Rounded.BrightnessAuto),
        Triple(ThemeMode.Light, "Светлая", Icons.Rounded.LightMode),
        Triple(ThemeMode.Dark, "Тёмная", Icons.Rounded.DarkMode),
    )
    val selected = options.indexOfFirst { it.first == mode }.coerceAtLeast(0)
    val position by animateFloatAsState(selected.toFloat(), tween(450, easing = Motion.Emphasized), label = "themeIndicator")
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Text(
            "Тема",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .drawBehind {
                    val w = size.width / options.size
                    val inset = 4.dp.toPx()
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(w * position + inset, inset),
                        size = androidx.compose.ui.geometry.Size(w - inset * 2, size.height - inset * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                    )
                },
        ) {
            options.forEachIndexed { i, (m, label, icon) ->
                val fraction = (1f - abs(position - i)).coerceIn(0f, 1f)
                val fg = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onPrimary, fraction)
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(m) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(Surface2)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
