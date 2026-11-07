package com.sonora.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.sonora.player.data.Net
import com.sonora.player.data.Store
import com.sonora.player.data.ThemeMode
import com.sonora.player.platform.MediaKey
import com.sonora.player.platform.MediaKeys
import com.sonora.player.platform.TitleBar
import com.sonora.player.player.AudioPlayer
import com.sonora.player.ui.Bg
import com.sonora.player.ui.DarkColors
import com.sonora.player.ui.LightColors
import com.sonora.player.ui.LoginScreen
import com.sonora.player.ui.MainShell
import com.sonora.player.ui.SonoraTheme
import com.sonora.player.ui.SplashScreen
import com.sonora.player.ui.TextFocus
import com.sonora.player.ui.rememberArtworkAccent
import javafx.application.Platform
import okio.Path.Companion.toOkioPath

/** Значок приложения, нарисованный кодом: градиентный круг с эквалайзером. */
object AppIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFFF4D8D), Color(0xFF7C4DFF)), Offset.Zero, Offset(s, s)),
            radius = s / 2,
        )
        val heights = listOf(0.28f, 0.6f, 0.42f, 0.32f)
        val bw = s * 0.1f
        val gap = s * 0.05f
        var x = (s - (4 * bw + 3 * gap)) / 2
        heights.forEach { h ->
            val bh = s * h
            drawRoundRect(Color.White, Offset(x, (s - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
            x += bw + gap
        }
    }
}

fun main() {
    System.setProperty("prism.order", "sw")
    AudioPlayer.initPlatform()
    val store = Store()
    val player = AudioPlayer(store.data.value.settings.volume)
    val app = AppState(store, player)

    application {
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }
        var trayHintShown by remember { mutableStateOf(false) }
        val state by player.state.collectAsState()

        fun quit() {
            MediaKeys.stop()
            player.shutdown()
            store.flush()
            runCatching { Platform.exit() }
            exitApplication()
        }

        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { Net.callFactory })) }
                .diskCache {
                    DiskCache.Builder()
                        .directory(store.cacheDir.toOkioPath().resolve("images"))
                        .maxSizeBytes(300L * 1024 * 1024)
                        .build()
                }
                .crossfade(250)
                .build()
        }

        // Медиаклавиши клавиатуры.
        LaunchedEffect(app.settings.mediaKeys) {
            if (app.settings.mediaKeys) {
                MediaKeys.start { key ->
                    when (key) {
                        MediaKey.PlayPause -> player.toggle()
                        MediaKey.Next -> player.next()
                        MediaKey.Previous -> player.previous()
                        MediaKey.Stop -> if (player.state.value.isPlaying) player.toggle()
                    }
                }
            } else {
                MediaKeys.stop()
            }
        }

        val song = state.current
        Tray(
            icon = AppIcon,
            state = trayState,
            tooltip = song?.let { "${it.title} — ${it.artist}" } ?: "Sonora",
            onAction = { visible = true },
            menu = {
                if (song != null) {
                    Item(if (state.isPlaying) "Пауза" else "Играть", onClick = { player.toggle() })
                    Item("Следующий трек", onClick = { player.next() })
                    Item("Предыдущий трек", onClick = { player.previous() })
                    Separator()
                }
                Item("Открыть Sonora", onClick = { visible = true })
                Item("Выход", onClick = { quit() })
            },
        )

        // Окно занимает до 90% рабочей области, чтобы заголовок не уходил за край экрана при масштабе 125–150%.
        val screen = remember { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
        val windowState = rememberWindowState(
            size = DpSize(minOf(1320, (screen.width * 0.9).toInt()).dp, minOf(860, (screen.height * 0.9).toInt()).dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = {
                if (app.settings.closeToTray) {
                    visible = false
                    if (!trayHintShown) {
                        trayHintShown = true
                        trayState.sendNotification(Notification("Sonora работает в фоне", "Значок в трее — управление и выход"))
                    }
                } else {
                    quit()
                }
            },
            visible = visible,
            state = windowState,
            title = song?.let { "${it.title} — ${it.artist} · Sonora" } ?: "Sonora",
            icon = AppIcon,
            onPreviewKeyEvent = { handleKey(app, it) },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(minOf(1000, screen.width - 40), minOf(640, screen.height - 40))
            }
            SonoraRoot(app) { dark ->
                val palette = if (dark) DarkColors else LightColors
                TitleBar.apply(window, dark, palette.surface1.toArgb(), palette.onBg.toArgb())
            }
        }
    }
}

@Composable
private fun SonoraRoot(app: AppState, onTheme: (Boolean) -> Unit) {
    val s = app.settings
    val dark = when (s.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    LaunchedEffect(dark) { onTheme(dark) }
    val state by app.player.state.collectAsState()
    val coverUrl = if (s.dynamicColors) state.current?.let(app::cover) else null
    val accent by rememberArtworkAccent(coverUrl, dark)
    SonoraTheme(dark, accent) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            AnimatedContent(
                app.auth,
                transitionSpec = { fadeIn(tween(500, 100)) togetherWith fadeOut(tween(250)) },
                label = "auth",
            ) { auth ->
                when (auth) {
                    AuthState.Checking -> SplashScreen()
                    AuthState.LoggedOut -> LoginScreen(app)
                    AuthState.LoggedIn -> MainShell(app)
                }
            }
        }
    }
}

/** Горячие клавиши окна. */
private fun handleKey(app: AppState, e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val p = app.player
    val loggedIn = app.auth == AuthState.LoggedIn
    when {
        e.key == Key.Escape -> when {
            app.settingsOpen -> app.settingsOpen = false
            app.nowPlayingOpen -> app.nowPlayingOpen = false
            app.canGoBack -> app.back()
            else -> return false
        }
        !loggedIn -> return false
        e.isCtrlPressed && e.key == Key.F -> app.select(Section.Search)
        e.isCtrlPressed && e.key == Key.T -> app.setThemeMode(if (app.settings.themeMode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark)
        e.isCtrlPressed && e.key == Key.Comma -> app.settingsOpen = true
        e.key == Key.F5 -> app.loadLibrary()
        e.key == Key.F11 -> app.nowPlayingOpen = !app.nowPlayingOpen && p.state.value.current != null
        e.isAltPressed && e.key == Key.DirectionLeft -> app.back()
        e.key == Key.MediaPlayPause -> p.toggle()
        TextFocus.focused -> return false
        e.isCtrlPressed && e.key == Key.DirectionRight -> p.next()
        e.isCtrlPressed && e.key == Key.DirectionLeft -> p.previous()
        e.key == Key.Spacebar -> p.toggle()
        e.key == Key.DirectionRight -> p.seekBy(5000)
        e.key == Key.DirectionLeft -> p.seekBy(-5000)
        e.key == Key.DirectionUp -> p.setVolume(p.state.value.volume + 0.05f)
        e.key == Key.DirectionDown -> p.setVolume(p.state.value.volume - 0.05f)
        e.key == Key.M -> p.toggleMute()
        e.key == Key.S -> p.toggleShuffle()
        e.key == Key.R -> p.cycleRepeat()
        e.key == Key.Q -> app.queueOpen = !app.queueOpen
        e.key == Key.L -> p.state.value.current?.let { app.toggleFavorite(it) } ?: return false
        else -> return false
    }
    return true
}
