@file:OptIn(ExperimentalFoundationApi::class)

package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import com.sonora.player.Screen
import com.sonora.player.data.Catalog
import com.sonora.player.data.Online
import com.sonora.player.data.Song
import java.awt.Desktop
import java.net.URI

// ---------- Раздел «Интернет» ----------

@Composable
fun OnlineScreen(app: AppState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        app.ensureOnlineLoaded()
        runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(
            app.onlineQuery, { app.onlineQuery = it }, focus, app.onlineLoading,
            placeholder = "Найти музыку в ${app.catalog.title} — нажмите Enter",
            onSubmit = app::searchOnline,
        )
        Row(
            Modifier.padding(start = PagePadding, end = PagePadding, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CatalogSelector(app.catalog, app::selectCatalog)
            Spacer(Modifier.width(16.dp))
            Text(
                app.catalog.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.weight(1f)) {
            val shown = app.onlineShown
            AnimatedContent(
                Triple(app.onlineLoading && (shown == null || shown.first != app.catalog || app.onlineError != null), app.onlineError, shown),
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                contentKey = { (loading, err, s) -> "$loading|$err|${s?.first}|${s?.second}" },
                label = "online",
            ) { (loading, error, s) ->
                when {
                    loading || s == null -> Column(Modifier.padding(horizontal = PagePadding, vertical = 24.dp)) { ShimmerList(8) }
                    error != null -> OnlineError(app, error)
                    s.first == Catalog.Archive -> ReleasesGrid(app, s.second)
                    else -> OnlineTracks(app, s.second)
                }
            }
        }
    }
}

@Composable
private fun OnlineError(app: AppState, error: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(Icons.Rounded.CloudOff, "Не получилось", error)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (app.catalog == Catalog.Jamendo) {
                    PillButton("Настройки", Icons.Rounded.Settings, primary = false) { app.settingsOpen = true }
                }
                PillButton("Повторить", Icons.Rounded.Refresh, primary = true, onClick = app::searchOnline)
            }
        }
    }
}

@Composable
private fun ReleasesGrid(app: AppState, query: String) {
    val list = app.onlineReleases
    if (list.isEmpty()) {
        CenteredEmpty(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос или другой каталог")
        return
    }
    val state = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(180.dp),
            state = state,
            contentPadding = PaddingValues(start = PagePadding - 12.dp, end = PagePadding - 4.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle(if (query.isBlank()) "Популярное на netlabels" else "Релизы по запросу «$query»")
            }
            items(list, key = { it.id }) { r ->
                HoverTile(
                    r.cover, r.id, r.title,
                    listOf(r.artist, r.license).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                    RoundedCornerShape(14.dp), Icons.Rounded.Album,
                    onClick = { app.openRelease(r) },
                    onPlay = { app.playRelease(r) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(state),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
            style = sonoraScrollbar(),
        )
    }
}

@Composable
private fun OnlineTracks(app: AppState, query: String) {
    val list = app.onlineSongs
    if (list.isEmpty()) {
        CenteredEmpty(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другой запрос или другой каталог")
        return
    }
    BoxWithConstraints {
        val showAlbum = maxWidth > 760.dp
        ScrollingList {
            item {
                PageTitle(if (query.isBlank()) "Популярное за неделю" else "Треки по запросу «$query»", plural(list.size, "трек", "трека", "треков")) {
                    PillButton("Скачать всё", Icons.Rounded.Download, primary = false) { app.downloadAll(list) }
                    PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true) { app.play(list) }
                }
            }
            stickyHeader { TrackHeader(showAlbum, Modifier.background(Bg), trailingWidth = 36.dp) }
            tracks(app, list, showAlbum, trailing = { DownloadButton(app, it) })
        }
    }
}

/** Переключатель каталога со скользящей подсветкой. */
@Composable
private fun CatalogSelector(selected: Catalog, onSelect: (Catalog) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val itemWidth = 150.dp
    val x by animateDpAsState(itemWidth * selected.ordinal, spring(0.8f, Spring.StiffnessMediumLow), label = "catalogX")
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(Glass.copy(alpha = 0.06f))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(accent),
        )
        Row {
            Catalog.entries.forEach { c ->
                val on = c == selected
                val color by animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(250), label = "catalogLabel",
                )
                Box(
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .handClick { onSelect(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(c.title, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Кнопка в строке трека: скачать, прогресс (клик отменяет) или «уже скачано». */
@Composable
fun DownloadButton(app: AppState, song: Song) {
    val accent = MaterialTheme.colorScheme.primary
    val progress = app.downloadProgress[song.id]
    val state = when {
        app.isDownloaded(song) -> 2
        progress != null -> 1
        else -> 0
    }
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(state, transitionSpec = { scaleIn(spring(0.55f)) + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "dl") { s ->
            when (s) {
                2 -> SmallIconButton(Icons.Rounded.DownloadDone, "Скачано", tint = accent) { app.select(com.sonora.player.Section.Downloads) }
                1 -> Box(Modifier.size(32.dp).clip(CircleShape).handClick { app.cancelDownload(song) }, contentAlignment = Alignment.Center) {
                    val p = progress ?: 0f
                    val animated by animateFloatAsState(p.coerceAtLeast(0f), tween(300), label = "dlProgress")
                    if (p < 0f) CircularProgressIndicator(Modifier.size(22.dp), color = accent, strokeWidth = 2.5.dp)
                    else CircularProgressIndicator({ animated }, Modifier.size(22.dp), color = accent, strokeWidth = 2.5.dp, trackColor = Glass.copy(alpha = 0.12f))
                    Icon(Icons.Rounded.Close, "Отменить", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> if (Online.canDownload(song)) {
                    SmallIconButton(Icons.Rounded.Download, "Скачать") { app.download(song) }
                } else {
                    Spacer(Modifier.size(32.dp))
                }
            }
        }
    }
}

// ---------- Релиз из Internet Archive ----------

@Composable
fun ReleaseScreen(app: AppState, screen: Screen.OnlineRelease) {
    val r = screen.release
    val songs = app.detailSongs[screen]
    val accent by rememberArtworkAccent(r.cover, IsDark)
    val tint by animateColorAsState(accent, tween(700), label = "releaseTint")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 760.dp
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
                    val shape = RoundedCornerShape(18.dp)
                    CoverImage(r.cover, r.id, Modifier.size(if (wide) 220.dp else 160.dp).shadowSoft(shape), shape, 64.dp, Icons.Rounded.Album)
                    Spacer(Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("РЕЛИЗ · INTERNET ARCHIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            r.title,
                            style = if (wide) MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(
                                r.artist.ifBlank { null },
                                r.year.takeIf { it > 0 }?.toString(),
                                songs?.let { plural(it.size, "трек", "трека", "треков") },
                                songs?.sumOf { it.durationMs }?.takeIf { it > 0 }?.let { formatDuration(it) },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val license = songs?.firstOrNull()?.license?.ifBlank { null } ?: r.license.ifBlank { null }
                        if (license != null) {
                            Spacer(Modifier.height(8.dp))
                            LicenseChip(license)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true) { songs?.let { app.play(it) } }
                            PillButton("Скачать всё", Icons.Rounded.Download, primary = false) { songs?.let { app.downloadAll(it) } }
                            PillButton("Страница", Icons.AutoMirrored.Rounded.OpenInNew, primary = false) { openLink(r.page) }
                        }
                    }
                }
            }
            when {
                songs == null -> item { ShimmerList(8) }
                songs.isEmpty() -> item { EmptyState(Icons.Rounded.SearchOff, "Нет MP3-файлов", "В этом релизе нет аудио, которое можно воспроизвести") }
                else -> {
                    stickyHeader { TrackHeader(false, Modifier.background(Bg), trailingWidth = 36.dp) }
                    tracks(app, songs, false, numberOf = { i, s -> if (s.track > 0) s.track else i + 1 }, trailing = { DownloadButton(app, it) })
                }
            }
        }
    }
}

@Composable
private fun LicenseChip(license: String) {
    Text(
        license,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

// ---------- Загрузки ----------

@Composable
fun DownloadsScreen(app: AppState) {
    val list = app.downloads
    val active = app.downloadProgress.size
    if (list.isEmpty() && active == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 520.dp)) {
                EmptyState(
                    Icons.Rounded.DownloadForOffline, "Загрузок пока нет",
                    "Найдите музыку в разделе «Интернет» и нажмите ↓ у трека. Файлы сохраняются в ${app.downloadDir.absolutePath}",
                )
                PillButton("Найти музыку", Icons.Rounded.Public, primary = true) { app.select(com.sonora.player.Section.Online) }
            }
        }
        return
    }
    BoxWithConstraints {
        val showAlbum = maxWidth > 760.dp
        ScrollingList {
            item {
                PageTitle(
                    "Загрузки",
                    listOfNotNull(
                        plural(list.size, "трек", "трека", "треков"),
                        active.takeIf { it > 0 }?.let { "ещё $it скачивается" },
                        app.downloadDir.absolutePath,
                    ).joinToString(" · "),
                ) {
                    PillButton("Папка", Icons.Rounded.Folder, primary = false, onClick = app::openDownloadsFolder)
                    PillButton("Перемешать", Icons.Rounded.Shuffle, primary = false) { app.play(list, shuffle = true) }
                    PillButton("Слушать", Icons.Rounded.PlayArrow, primary = true) { app.play(list) }
                }
            }
            stickyHeader { TrackHeader(showAlbum, Modifier.background(Bg), trailingWidth = 36.dp) }
            tracks(app, list, showAlbum, trailing = { song ->
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    SmallIconButton(Icons.Rounded.DeleteOutline, "Удалить файл") { app.deleteDownload(song) }
                }
            })
        }
    }
}

private fun openLink(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
