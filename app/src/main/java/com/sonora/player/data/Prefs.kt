package com.sonora.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora")

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

enum class ThemeMode { System, Light, Dark }

data class Settings(
    val dynamicColors: Boolean = true,
    val transcode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
)

class Prefs(context: Context) {
    private val store = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object K {
        val session = stringPreferencesKey("session")
        val favorites = stringPreferencesKey("favorites")
        val stats = stringPreferencesKey("stats")
        val dynamic = booleanPreferencesKey("dynamic_colors")
        val transcode = booleanPreferencesKey("transcode")
        val theme = stringPreferencesKey("theme")
    }

    val session: Flow<Session?> = store.data.map { p ->
        p[K.session]?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }
    }

    suspend fun saveSession(s: Session?) {
        store.edit { if (s == null) it.remove(K.session) else it[K.session] = json.encodeToString(s) }
    }

    val favorites: Flow<List<Song>> = store.data.map { decodeSongs(it[K.favorites]) }

    suspend fun toggleFavorite(song: Song) {
        store.edit { p ->
            val list = decodeSongs(p[K.favorites]).toMutableList()
            if (!list.removeAll { it.id == song.id }) list.add(0, song)
            p[K.favorites] = json.encodeToString(list)
        }
    }

    /** История и статистика прослушиваний. */
    val stats: Flow<List<PlayStat>> = store.data.map { decodeStats(it[K.stats]) }

    suspend fun recordPlay(song: Song) {
        store.edit { p ->
            val now = System.currentTimeMillis()
            val list = decodeStats(p[K.stats]).toMutableList()
            val i = list.indexOfFirst { it.song.id == song.id }
            if (i >= 0) list[i] = list[i].copy(song = song, count = list[i].count + 1, lastPlayed = now)
            else list.add(PlayStat(song, 1, now))
            val trimmed = list.sortedByDescending { it.lastPlayed }.take(300)
            p[K.stats] = json.encodeToString(trimmed)
        }
    }

    suspend fun clearStats() {
        store.edit { it.remove(K.stats) }
    }

    val settings: Flow<Settings> = store.data.map {
        Settings(
            dynamicColors = it[K.dynamic] ?: true,
            transcode = it[K.transcode] ?: false,
            themeMode = ThemeMode.entries.firstOrNull { m -> m.name == it[K.theme] } ?: ThemeMode.System,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[K.theme] = mode.name }
    }

    suspend fun setDynamicColors(value: Boolean) {
        store.edit { it[K.dynamic] = value }
    }

    suspend fun setTranscode(value: Boolean) {
        store.edit { it[K.transcode] = value }
    }

    private fun decodeSongs(raw: String?): List<Song> =
        raw?.let { runCatching { json.decodeFromString<List<Song>>(it) }.getOrNull() }.orEmpty()

    private fun decodeStats(raw: String?): List<PlayStat> =
        raw?.let { runCatching { json.decodeFromString<List<PlayStat>>(it) }.getOrNull() }.orEmpty()
}
