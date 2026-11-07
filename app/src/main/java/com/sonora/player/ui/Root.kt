package com.sonora.player.ui

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonora.player.AuthState
import com.sonora.player.MainViewModel
import com.sonora.player.Screen
import com.sonora.player.data.ThemeMode
import com.sonora.player.player.PlayerUiState

@Composable
fun SonoraRoot(vm: MainViewModel) {
    val p by vm.player.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val cover = if (settings.dynamicColors && vm.auth == AuthState.LoggedIn) p.current?.let(vm::cover) else null
    val accent by rememberArtworkAccent(cover, dark)

    // Иконки статус-бара и навигации под цвет темы.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        val transparent = AndroidColor.TRANSPARENT
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        )
    }

    SonoraTheme(dark, accent) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg),
        ) {
            AnimatedContent(
                vm.auth,
                transitionSpec = {
                    (fadeIn(tween(600, 100, Motion.Standard)) + scaleIn(tween(700, easing = Motion.Decelerate), initialScale = 0.96f)) togetherWith
                        fadeOut(tween(250, easing = Motion.Accelerate))
                },
                label = "auth",
            ) { auth ->
                when (auth) {
                    AuthState.Checking -> SplashScreen()
                    AuthState.LoggedOut -> LoginScreen(vm)
                    AuthState.LoggedIn -> MainScreen(vm, p)
                }
            }
            Toast(vm.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = if (p.current != null) 96.dp else 24.dp))
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel, p: PlayerUiState) {
    val hasPlayer = p.current != null
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = (if (hasPlayer) 96.dp else 16.dp) + navBottom
    val holder = rememberSaveableStateHolder()

    BackHandler(enabled = vm.playerExpanded || vm.stack.size > 1) {
        if (vm.playerExpanded) vm.playerExpanded = false else vm.back()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            vm.stack.last(),
            transitionSpec = {
                val forward = vm.navForward
                // Новый экран мягко выезжает и проявляется, старый слегка уходит в сторону и гаснет.
                (slideInHorizontally(tween(500, easing = Motion.Emphasized)) { if (forward) it / 5 else -it / 5 } +
                    fadeIn(tween(350, 100, Motion.Standard))) togetherWith
                    (slideOutHorizontally(tween(450, easing = Motion.Emphasized)) { if (forward) -it / 8 else it / 8 } +
                        fadeOut(tween(200, easing = Motion.Accelerate)))
            },
            label = "nav",
        ) { screen ->
            holder.SaveableStateProvider(screen.toString()) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm, bottomPad)
                    Screen.Search -> SearchScreen(vm, bottomPad)
                    is Screen.AlbumDetail -> AlbumScreen(vm, screen.album, bottomPad)
                    is Screen.ArtistDetail -> ArtistScreen(vm, screen.artist, bottomPad)
                }
            }
        }

        AnimatedVisibility(
            visible = hasPlayer && !vm.playerExpanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(500, easing = Motion.Emphasized)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300, easing = Motion.Accelerate)) { it } + fadeOut(tween(200)),
        ) {
            MiniPlayer(vm, p) { vm.playerExpanded = true }
        }

        AnimatedVisibility(
            visible = hasPlayer && vm.playerExpanded,
            enter = slideInVertically(tween(550, easing = Motion.Emphasized)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(380, easing = Motion.Emphasized)) { it } + fadeOut(tween(300, 80)),
        ) {
            PlayerScreen(vm) { vm.playerExpanded = false }
        }
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier = Modifier) {
    AnimatedContent(
        message,
        modifier = modifier.navigationBarsPadding(),
        transitionSpec = {
            (slideInVertically(tween(450, easing = Motion.Emphasized)) { it } + fadeIn(tween(300)) +
                scaleIn(tween(450, easing = Motion.Decelerate), initialScale = 0.9f)) togetherWith
                (fadeOut(tween(200, easing = Motion.Accelerate)) + scaleOut(tween(200), targetScale = 0.95f))
        },
        label = "toast",
    ) { text ->
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Surface2)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}
