package com.sonora.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.DemoSource
import com.sonora.player.data.MusicSource
import com.sonora.player.data.Net
import com.sonora.player.data.PlayStat
import com.sonora.player.data.SearchResult
import com.sonora.player.data.Session
import com.sonora.player.data.Settings
import com.sonora.player.data.Song
import com.sonora.player.data.SynologyApi
import com.sonora.player.data.SynologyException
import com.sonora.player.data.SynologySource
import com.sonora.player.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class AlbumDetail(val album: Album) : Screen
    data class ArtistDetail(val artist: Artist) : Screen
}

enum class SongAction { PlayNext, AddToQueue, ToggleFavorite, GoToAlbum, GoToArtist }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val sonora = app as SonoraApp
    val prefs = sonora.prefs
    val player = sonora.player

    var auth by mutableStateOf<AuthState>(AuthState.Checking)
        private set
    var source by mutableStateOf<MusicSource?>(null)
        private set

    // --- Форма входа ---
    var loginServer by mutableStateOf("")
    var loginAccount by mutableStateOf("")
    var loginHttps by mutableStateOf(false)
    var loginTrustAll by mutableStateOf(false)
    var needOtp by mutableStateOf(false)
        private set
    var loginLoading by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var loginErrorTick by mutableIntStateOf(0)
        private set

    // --- Библиотека ---
    var songs by mutableStateOf(emptyList<Song>())
        private set
    var songsTotal by mutableIntStateOf(0)
        private set
    var songsLoading by mutableStateOf(false)
        private set
    var albums by mutableStateOf(emptyList<Album>())
        private set
    var artists by mutableStateOf(emptyList<Artist>())
        private set
    var libraryLoading by mutableStateOf(false)
        private set
    var libraryError by mutableStateOf<String?>(null)
        private set

    val settings: StateFlow<Settings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val sourceFlow = snapshotFlow { source }

    val favorites: StateFlow<List<Song>> = combine(prefs.favorites, sourceFlow) { list, src ->
        if (src == null) emptyList<Song>() else list.filter(src::owns)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteIds: StateFlow<Set<String>> = favorites.map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val stats: StateFlow<List<PlayStat>> = combine(prefs.stats, sourceFlow) { list, src ->
        if (src == null) emptyList<PlayStat>() else list.filter { src.owns(it.song) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Навигация ---
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var navForward by mutableStateOf(true)
        private set
    val detailSongs = mutableStateMapOf<Screen, List<Song>>()
    var playerExpanded by mutableStateOf(false)

    // --- Поиск ---
    var query by mutableStateOf("")
        private set
    var searchResult by mutableStateOf<SearchResult?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    // --- Всплывающие сообщения ---
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    init {
        viewModelScope.launch { settings.collect { s -> source?.transcode = s.transcode } }
        viewModelScope.launch { restore() }
    }

    private suspend fun restore() {
        val s = prefs.session.first()
        when {
            s == null -> auth = AuthState.LoggedOut
            s.mode == Session.DEMO -> activate(DemoSource())
            else -> {
                loginServer = s.server
                loginAccount = s.account
                loginHttps = s.https
                loginTrustAll = s.trustAll
                Net.trustAllCerts = s.trustAll
                try {
                    val api = SynologyApi(s.baseUrl)
                    api.queryInfo()
                    api.sid = s.sid
                    api.ping()
                    activate(SynologySource(api, s.account))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SynologyException) {
                    loginError = e.message
                    auth = AuthState.LoggedOut
                } catch (e: Exception) {
                    loginError = "Не удалось подключиться к ${s.baseUrl}"
                    auth = AuthState.LoggedOut
                }
            }
        }
    }

    fun login(password: String, otp: String) {
        if (loginLoading) return
        viewModelScope.launch {
            loginLoading = true
            loginError = null
            try {
                Net.trustAllCerts = loginTrustAll
                val base = SynologyApi.normalizeBase(loginServer, loginHttps)
                val api = SynologyApi(base)
                api.queryInfo()
                api.login(loginAccount.trim(), password, otp.takeIf { needOtp })
                prefs.saveSession(
                    Session(
                        mode = Session.NAS, baseUrl = base, server = loginServer.trim(), https = loginHttps,
                        account = loginAccount.trim(), sid = api.sid.orEmpty(), trustAll = loginTrustAll,
                    ),
                )
                needOtp = false
                activate(SynologySource(api, loginAccount.trim()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SynologyException) {
                if (e.code == 403 || e.code == 404) needOtp = true
                showLoginError(e.message)
            } catch (e: IllegalArgumentException) {
                showLoginError(e.message)
            } catch (e: javax.net.ssl.SSLException) {
                showLoginError("Ошибка HTTPS-сертификата. Включите доверие самоподписанным сертификатам")
            } catch (e: Exception) {
                showLoginError("Не удалось подключиться: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                loginLoading = false
            }
        }
    }

    private fun showLoginError(message: String?) {
        loginError = message ?: "Ошибка входа"
        loginErrorTick++
    }

    fun startDemo() {
        viewModelScope.launch {
            prefs.saveSession(Session(mode = Session.DEMO))
            loginError = null
            activate(DemoSource())
        }
    }

    private fun activate(src: MusicSource) {
        src.transcode = settings.value.transcode
        source = src
        stack.clear()
        stack.add(Screen.Home)
        detailSongs.clear()
        auth = AuthState.LoggedIn
        loadLibrary()
    }

    fun logout() {
        viewModelScope.launch {
            val src = source
            player.stopAndClear()
            playerExpanded = false
            prefs.saveSession(null)
            source = null
            songs = emptyList()
            albums = emptyList()
            artists = emptyList()
            songsTotal = 0
            searchResult = null
            query = ""
            auth = AuthState.LoggedOut
            runCatching { src?.logout() }
        }
    }

    fun loadLibrary() {
        val src = source ?: return
        viewModelScope.launch {
            libraryLoading = true
            libraryError = null
            try {
                coroutineScope {
                    val page = async { src.songs(0, PAGE) }
                    val al = async { src.albums() }
                    val ar = async { src.artists() }
                    page.await().let {
                        songs = it.items
                        songsTotal = it.total
                    }
                    albums = al.await()
                    artists = ar.await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            } finally {
                libraryLoading = false
            }
        }
    }

    fun loadMoreSongs() {
        val src = source ?: return
        if (songsLoading || libraryLoading || songs.size >= songsTotal) return
        viewModelScope.launch {
            songsLoading = true
            try {
                val page = src.songs(songs.size, PAGE)
                val known = songs.map { it.id }.toHashSet()
                songs = songs + page.items.filter { it.id !in known }
                songsTotal = if (page.items.isEmpty()) songs.size else page.total
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            } finally {
                songsLoading = false
            }
        }
    }

    private fun handleError(e: Exception) {
        if (e is SynologyException && e.isSessionError) {
            loginError = e.message
            logout()
        } else {
            libraryError = e.localizedMessage ?: "Ошибка загрузки"
        }
    }

    // --- Навигация ---

    private fun push(screen: Screen) {
        navForward = true
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) {
            navForward = false
            stack.removeAt(stack.lastIndex)
        }
    }

    fun openSearch() = push(Screen.Search)

    fun openAlbum(album: Album) {
        val screen = Screen.AlbumDetail(album)
        push(screen)
        loadDetail(screen) { it.albumSongs(album) }
    }

    fun openArtist(artist: Artist) {
        val screen = Screen.ArtistDetail(artist)
        push(screen)
        loadDetail(screen) { it.artistSongs(artist) }
    }

    private fun loadDetail(screen: Screen, load: suspend (MusicSource) -> List<Song>) {
        val src = source ?: return
        if (detailSongs.containsKey(screen)) return
        viewModelScope.launch {
            try {
                detailSongs[screen] = load(src)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailSongs[screen] = emptyList()
                notify(e.localizedMessage ?: "Ошибка загрузки")
            }
        }
    }

    // --- Поиск ---

    fun onQuery(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            searchResult = null
            searching = false
            return
        }
        val src = source ?: return
        searchJob = viewModelScope.launch {
            delay(350)
            searching = true
            try {
                searchResult = src.search(q.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.localizedMessage ?: "Ошибка поиска")
            } finally {
                searching = false
            }
        }
    }

    // --- Воспроизведение ---

    fun play(list: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        val src = source ?: return
        player.play(list, index, src, shuffle)
    }

    fun shuffleAll() {
        if (songs.isEmpty()) return
        play(songs, shuffle = true)
        playerExpanded = true
    }

    fun onSongAction(song: Song, action: SongAction) {
        val src = source ?: return
        when (action) {
            SongAction.PlayNext -> {
                player.playNext(song, src)
                notify("«${song.title}» будет следующей")
            }
            SongAction.AddToQueue -> {
                player.addToQueue(song, src)
                notify("Добавлено в очередь")
            }
            SongAction.ToggleFavorite -> toggleFavorite(song)
            SongAction.GoToAlbum -> {
                playerExpanded = false
                openAlbum(Album(song.album, song.albumArtist.ifBlank { song.artist }, song.year))
            }
            SongAction.GoToArtist -> {
                playerExpanded = false
                openArtist(Artist(song.artist))
            }
        }
    }

    fun toggleFavorite(song: Song) {
        val wasFavorite = song.id in favoriteIds.value
        viewModelScope.launch { prefs.toggleFavorite(song) }
        notify(if (wasFavorite) "Убрано из избранного" else "Добавлено в избранное")
    }

    fun cover(song: Song) = source?.songCover(song)
    fun albumCover(album: Album) = source?.albumCover(album)
    fun artistCover(artist: Artist) = source?.artistCover(artist)

    // --- Настройки ---

    fun setDynamicColors(value: Boolean) {
        viewModelScope.launch { prefs.setDynamicColors(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setTranscode(value: Boolean) {
        viewModelScope.launch { prefs.setTranscode(value) }
    }

    fun clearHistory() {
        viewModelScope.launch { prefs.clearStats() }
        notify("История очищена")
    }

    fun notify(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2200)
            toast = null
        }
    }

    private companion object {
        const val PAGE = 300
    }
}
