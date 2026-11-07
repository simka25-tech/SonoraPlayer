package com.sonora.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonora.player.AppState
import com.sonora.player.Screen
import com.sonora.player.Section
import com.sonora.player.data.ThemeMode

private val sectionIcons = mapOf(
    Section.Home to Icons.Rounded.Home,
    Section.Search to Icons.Rounded.Search,
    Section.Songs to Icons.Rounded.MusicNote,
    Section.Albums to Icons.Rounded.Album,
    Section.Artists to Icons.Rounded.Person,
    Section.Favorites to Icons.Rounded.Favorite,
    Section.History to Icons.Rounded.History,
    Section.Online to Icons.Rounded.Public,
    Section.Downloads to Icons.Rounded.DownloadForOffline,
)

/** Главный макет: боковая панель, контент, очередь справа, плеер снизу и полноэкранный режим поверх. */
@Composable
fun MainShell(app: AppState) {
    val holder = rememberSaveableStateHolder()
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Sidebar(app)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val screen = app.stack.last()
                    AnimatedContent(
                        screen,
                        transitionSpec = {
                            val dir = if (app.navForward) 1 else -1
                            (fadeIn(tween(320, 60, Motion.Standard)) +
                                slideInHorizontally(tween(420, easing = Motion.Decelerate)) { dir * it / 14 }) togetherWith
                                (fadeOut(tween(160, easing = Motion.Accelerate)) +
                                    slideOutHorizontally(tween(260, easing = Motion.Accelerate)) { -dir * it / 20 })
                        },
                        label = "screen",
                    ) { s ->
                        holder.SaveableStateProvider(s.toString()) {
                            ScreenContent(app, s)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        app.canGoBack,
                        enter = fadeIn() + scaleIn(initialScale = 0.6f),
                        exit = fadeOut() + scaleOut(targetScale = 0.6f),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Box(Modifier.clip(CircleShape).background(Bg.copy(alpha = 0.6f))) {
                            SmallIconButton(
                                Icons.AutoMirrored.Rounded.ArrowBack, "Назад (Alt+←)",
                                tint = MaterialTheme.colorScheme.onSurface, size = 40.dp, iconSize = 22.dp,
                            ) { app.back() }
                        }
                    }
                }
                AnimatedVisibility(
                    app.queueOpen && !app.nowPlayingOpen,
                    enter = slideInHorizontally(tween(380, easing = Motion.Decelerate)) { it } + fadeIn(tween(250)),
                    exit = slideOutHorizontally(tween(260, easing = Motion.Accelerate)) { it } + fadeOut(tween(200)),
                ) {
                    Row {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Glass.copy(alpha = 0.08f)))
                        QueuePanel(app)
                    }
                }
            }
            AnimatedVisibility(!app.nowPlayingOpen, enter = fadeIn(), exit = fadeOut()) {
                PlayerBar(app, accent)
            }
        }
        AnimatedVisibility(
            app.nowPlayingOpen,
            enter = slideInVertically(tween(520, easing = Motion.Decelerate)) { it / 3 } + fadeIn(tween(350)),
            exit = slideOutVertically(tween(320, easing = Motion.Accelerate)) { it / 3 } + fadeOut(tween(250)),
        ) {
            NowPlayingScreen(app, accent)
        }
        Toast(app.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = if (app.nowPlayingOpen) 32.dp else 112.dp))
        if (app.settingsOpen) {
            SettingsDialog(app) { app.settingsOpen = false }
        }
    }
}

@Composable
private fun Sidebar(app: AppState) {
    val accent = MaterialTheme.colorScheme.primary
    val selected = app.section
    val itemHeight = 40.dp
    val indicatorY by animateDpAsState(
        (itemHeight + 2.dp) * selected.ordinal,
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "navIndicator",
    )
    Column(
        Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(Surface1)
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SonoraLogo(Modifier.size(36.dp), playing = true)
            Spacer(Modifier.width(12.dp))
            Text("Sonora", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black))
        }
        // На невысоких экранах список пунктов прокручивается, а не налезает на карточку снизу.
        Box(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // Подсветка выбранного пункта плавно «переезжает» между пунктами.
            Box(
                Modifier
                    .offset(y = indicatorY)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(20.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Section.entries.forEach { section ->
                    NavItem(sectionIcons.getValue(section), section.title, section == selected, itemHeight, badge = navBadge(app, section)) {
                        app.select(section)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Glass.copy(alpha = 0.05f))
                .padding(12.dp),
        ) {
            Text(
                app.source?.title.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val dark = IsDark
                AnimatedContent(dark, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }, label = "themeIcon") { d ->
                    SmallIconButton(if (d) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "Сменить тему (Ctrl+T)", size = 36.dp, iconSize = 20.dp) {
                        app.setThemeMode(if (d) ThemeMode.Light else ThemeMode.Dark)
                    }
                }
                SmallIconButton(Icons.Rounded.Refresh, "Обновить библиотеку (F5)", size = 36.dp, iconSize = 20.dp) { app.loadLibrary() }
                SmallIconButton(Icons.Rounded.Settings, "Настройки (Ctrl+,)", size = 36.dp, iconSize = 20.dp) { app.settingsOpen = true }
                Spacer(Modifier.weight(1f))
                SmallIconButton(Icons.AutoMirrored.Rounded.Logout, "Выйти", size = 36.dp, iconSize = 20.dp) { app.logout() }
            }
        }
    }
}

private fun navBadge(app: AppState, section: Section): String? = when (section) {
    Section.Songs -> app.songsTotal.takeIf { it > 0 }?.toString()
    Section.Favorites -> app.favorites.size.takeIf { it > 0 }?.toString()
    Section.Downloads -> app.downloadProgress.size.takeIf { it > 0 }?.let { "↓$it" }
        ?: app.downloads.size.takeIf { it > 0 }?.toString()
    else -> null
}

@Composable
private fun NavItem(icon: ImageVector, title: String, selected: Boolean, height: androidx.compose.ui.unit.Dp, badge: String?, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val (src, hovered) = rememberHover()
    val bg by animateColorAsState(if (hovered && !selected) Glass.copy(alpha = 0.06f) else Color.Transparent, tween(180), label = "navBg")
    val fg by animateColorAsState(
        when {
            selected -> accent
            hovered -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tween(220), label = "navFg",
    )
    val shift by animateDpAsState(if (hovered || selected) 4.dp else 0.dp, spring(0.7f), label = "navShift")
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .hoverable(src)
            .handClick(onClick)
            .padding(start = 14.dp + shift, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (badge != null) {
            Text(badge, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier) {
    AnimatedVisibility(
        message != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(tween(220)) { it / 2 } + fadeOut(tween(200)),
    ) {
        var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        if (message != null) last = message
        val shape = CircleShape
        Text(
            last,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .shadowSoft(shape)
                .clip(shape)
                .background(Surface2)
                .border(1.dp, Glass.copy(alpha = 0.1f), shape)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}
