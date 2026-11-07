package com.sonora.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sonora.player.data.Net
import com.sonora.player.data.Prefs
import com.sonora.player.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SonoraApp : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var player: PlayerController
        private set

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        player = PlayerController(this).apply {
            onSongStarted = { song -> appScope.launch { prefs.recordPlay(song) } }
            connect()
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .callFactory { Net.callFactory }
        .crossfade(true)
        .build()
}
