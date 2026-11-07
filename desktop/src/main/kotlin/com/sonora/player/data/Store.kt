package com.sonora.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class Session(
    val mode: String,
    val baseUrl: String = "",
    val server: String = "",
    val https: Boolean = false,
    val account: String = "",
    val sid: String = "",
    val trustAll: Boolean = false,
) {
    companion object {
        const val DEMO = "demo"
        const val NAS = "nas"
    }
}

@Serializable
data class PlayStat(val song: Song, val count: Int, val lastPlayed: Long)

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class Settings(
    val dynamicColors: Boolean = true,
    val transcode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val mediaKeys: Boolean = true,
    val closeToTray: Boolean = false,
    val volume: Float = 0.8f,
    val jamendoClientId: String = "",
    val downloadDir: String = "",
)

@Serializable
data class StoreData(
    val session: Session? = null,
    val favorites: List<Song> = emptyList(),
    val stats: List<PlayStat> = emptyList(),
    val settings: Settings = Settings(),
    val downloads: List<Song> = emptyList(),
)

/** Настройки, избранное и история в JSON-файле %APPDATA%\Sonora\sonora.json. */
@OptIn(FlowPreview::class)
class Store {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Sonora").apply { mkdirs() }
    private val file = File(dir, "sonora.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _data = MutableStateFlow(load())
    val data: StateFlow<StoreData> = _data

    val cacheDir: File get() = File(dir, "cache").apply { mkdirs() }

    init {
        // Пишем на диск не чаще раза в полсекунды — громкость меняется очень часто.
        scope.launch { _data.drop(1).debounce(500).collect(::write) }
    }

    private fun load(): StoreData = runCatching {
        if (file.exists()) json.decodeFromString<StoreData>(file.readText()) else StoreData()
    }.getOrElse { StoreData() }

    private fun write(d: StoreData) {
        runCatching {
            val tmp = File(dir, "sonora.json.tmp")
            tmp.writeText(json.encodeToString(StoreData.serializer(), d))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /** Синхронно сохранить при выходе. */
    fun flush() = write(_data.value)

    fun saveSession(s: Session?) = _data.update { it.copy(session = s) }

    fun toggleFavorite(song: Song) = _data.update { d ->
        val list = d.favorites.toMutableList()
        if (!list.removeAll { it.id == song.id }) list.add(0, song)
        d.copy(favorites = list)
    }

    fun recordPlay(song: Song) = _data.update { d ->
        val now = System.currentTimeMillis()
        val list = d.stats.toMutableList()
        val i = list.indexOfFirst { it.song.id == song.id }
        if (i >= 0) list[i] = list[i].copy(song = song, count = list[i].count + 1, lastPlayed = now)
        else list.add(PlayStat(song, 1, now))
        d.copy(stats = list.sortedByDescending { it.lastPlayed }.take(300))
    }

    fun clearStats() = _data.update { it.copy(stats = emptyList()) }

    fun addDownload(song: Song) = _data.update { d ->
        d.copy(downloads = listOf(song) + d.downloads.filterNot { it.id == song.id })
    }

    fun removeDownload(id: String) = _data.update { d -> d.copy(downloads = d.downloads.filterNot { it.id == id }) }

    fun updateSettings(block: (Settings) -> Settings) = _data.update { it.copy(settings = block(it.settings)) }
}
