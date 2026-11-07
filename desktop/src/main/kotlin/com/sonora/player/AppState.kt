package com.sonora.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sonora.player.data.Album
import com.sonora.player.data.Artist
import com.sonora.player.data.Catalog
import com.sonora.player.data.DemoSource
import com.sonora.player.data.Log
import com.sonora.player.ui.plural
import com.sonora.player.data.Online
import com.sonora.player.data.OnlineAlbum
import com.sonora.player.data.RoutedSource
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.awt.Desktop
import java.io.File
import com.sonora.player.data.MusicSource
import com.sonora.player.data.Net
import com.sonora.player.data.PlayStat
import com.sonora.player.data.SearchResult
import com.sonora.player.data.Session
import com.sonora.player.data.Settings
import com.sonora.player.data.Song
import com.sonora.player.data.Store
import com.sonora.player.data.SynologyApi
import com.sonora.player.data.SynologyException
import com.sonora.player.data.SynologySource
import com.sonora.player.data.ThemeMode
import com.sonora.player.player.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthState {
    data object Checking : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

/** Разделы боковой панели. */
enum class Section(val title: String) {
    Home("Главная"),
    Search("Поиск"),
    Songs("Песни"),
    Albums("Альбомы"),
    Artists("Исполнители"),
    Favorites("Избранное"),
    History("История"),
    Online("Интернет"),
    Downloads("Загрузки"),
}

sealed interface Screen {
    data class Root(val section: Section) : Screen
    data class AlbumDetail(val album: Album) : Screen
    data class ArtistDetail(val artist: Artist) : Screen
    data class OnlineRelease(val release: OnlineAlbum) : Screen
}

enum class SongAction { PlayNext, AddToQueue, ToggleFavorite, GoToAlbum, GoToArtist }

@Stable
class AppState(val store: Store, val player: AudioPlayer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

    // --- Сохранённые данные ---
    var settings by mutableStateOf(store.data.value.settings)
        private set
    var favorites by mutableStateOf(emptyList<Song>())
        private set
    var favoriteIds by mutableStateOf(emptySet<String>())
        private set
    var stats by mutableStateOf(emptyList<PlayStat>())
        private set

    // --- Навигация ---
    val stack = mutableStateListOf<Screen>(Screen.Root(Section.Home))
    var navForward by mutableStateOf(true)
        private set
    val detailSongs = mutableStateMapOf<Screen, List<Song>>()
    var nowPlayingOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    val section: Section get() = stack.filterIsInstance<Screen.Root>().lastOrNull()?.section ?: Section.Home

    // --- Поиск ---
    var query by mutableStateOf("")
        private set
    var searchResult by mutableStateOf<SearchResult?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchFocusTick by mutableIntStateOf(0)
        private set
    private var searchJob: Job? = null

    // --- Всплывающие сообщения ---
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    /** Источник для плеера и обложек: библиотека плюс треки из интернета и скачанные файлы. */
    private var routed: MusicSource? = null

    // --- Музыка из интернета ---
    var catalog by mutableStateOf(if (settings.jamendoClientId.isNotBlank()) Catalog.Jamendo else Catalog.Archive)
        private set
    var onlineQuery by mutableStateOf("")
    var onlineSongs by mutableStateOf(emptyList<Song>())
        private set
    var onlineReleases by mutableStateOf(emptyList<OnlineAlbum>())
        private set
    var onlineLoading by mutableStateOf(false)
        private set
    var onlineError by mutableStateOf<String?>(null)
        private set
    /** Что сейчас показано: запрос и каталог, по которым получены результаты. */
    var onlineShown by mutableStateOf<Pair<Catalog, String>?>(null)
        private set
    private var onlineJob: Job? = null

    // --- Загрузки ---
    private var downloadsRaw: List<Song>? = null
    var downloads by mutableStateOf(emptyList<Song>())
        private set
    var downloadedIds by mutableStateOf(emptySet<String>())
        private set
    /** Прогресс активных загрузок по id исходного трека: 0..1, или -1, если размер неизвестен. */
    val downloadProgress = mutableStateMapOf<String, Float>()
    private val downloadJobs = HashMap<String, Job>()
    private val downloadSlots = Semaphore(3)

    init {
        scope.launch {
            store.data.map { it.settings }.distinctUntilChanged().collect { s ->
                settings = s
                source?.transcode = s.transcode
            }
        }
        scope.launch {
            store.data.collect { refreshSaved() }
        }
        player.onSongStarted = { song -> store.recordPlay(song) }
        player.onError = { message -> scope.launch { notify(message) } }
        player.onVolumeChanged = { v -> store.updateSettings { it.copy(volume = v) } }
        scope.launch { restore() }
    }

    private fun refreshSaved() {
        val d = store.data.value
        val src = routed
        favorites = if (src == null) emptyList() else d.favorites.filter(src::owns)
        favoriteIds = favorites.map { it.id }.toSet()
        stats = if (src == null) emptyList() else d.stats.filter { src.owns(it.song) }
        if (d.downloads != downloadsRaw) {
            downloadsRaw = d.downloads
            downloads = d.downloads.filter { File(it.url).isFile }
            downloadedIds = downloads.map { it.id.removePrefix(Online.PREFIX_LOCAL) }.toSet()
        }
    }

    private suspend fun restore() {
        val s = store.data.value.session
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
        scope.launch {
            loginLoading = true
            loginError = null
            try {
                Net.trustAllCerts = loginTrustAll
                val base = SynologyApi.normalizeBase(loginServer, loginHttps)
                val api = SynologyApi(base)
                api.queryInfo()
                api.login(loginAccount.trim(), password, otp.takeIf { needOtp })
                store.saveSession(
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
        store.saveSession(Session(mode = Session.DEMO))
        loginError = null
        activate(DemoSource())
    }

    private fun activate(src: MusicSource) {
        src.transcode = settings.transcode
        source = src
        routed = RoutedSource(src)
        stack.clear()
        stack.add(Screen.Root(Section.Home))
        detailSongs.clear()
        refreshSaved()
        auth = AuthState.LoggedIn
        loadLibrary()
    }

    fun logout() {
        val src = source
        player.stopAndClear()
        nowPlayingOpen = false
        queueOpen = false
        settingsOpen = false
        store.saveSession(null)
        source = null
        routed = null
        songs = emptyList()
        albums = emptyList()
        artists = emptyList()
        songsTotal = 0
        searchResult = null
        query = ""
        refreshSaved()
        auth = AuthState.LoggedOut
        scope.launch { runCatching { src?.logout() } }
    }

    fun loadLibrary() {
        val src = source ?: return
        scope.launch {
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
        scope.launch {
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

    fun select(section: Section) {
        nowPlayingOpen = false
        if (stack.size == 1 && section == this.section) {
            if (section == Section.Search) searchFocusTick++
            return
        }
        navForward = section.ordinal >= this.section.ordinal
        stack.clear()
        stack.add(Screen.Root(section))
        if (section == Section.Search) searchFocusTick++
    }

    private fun push(screen: Screen) {
        nowPlayingOpen = false
        navForward = true
        stack.add(screen)
    }

    val canGoBack: Boolean get() = stack.size > 1

    fun back() {
        if (nowPlayingOpen) {
            nowPlayingOpen = false
            return
        }
        if (stack.size > 1) {
            navForward = false
            stack.removeAt(stack.lastIndex)
        }
    }

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
        scope.launch {
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
        searchJob = scope.launch {
            delay(300)
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
        val src = routed ?: return
        player.play(list, index, src, shuffle)
    }

    fun shuffleAll() {
        if (songs.isEmpty()) return
        play(songs, shuffle = true)
    }

    fun onSongAction(song: Song, action: SongAction) {
        val src = routed ?: return
        if ((song.isOnline || song.isLocal) && (action == SongAction.GoToAlbum || action == SongAction.GoToArtist)) {
            val archiveId = song.id.removePrefix(Online.PREFIX_LOCAL).takeIf { it.startsWith(Online.PREFIX_ARCHIVE) }
                ?.removePrefix(Online.PREFIX_ARCHIVE)?.substringBefore('/')
            if (action == SongAction.GoToAlbum && archiveId != null) {
                openRelease(OnlineAlbum(archiveId, song.album, song.albumArtist.ifBlank { song.artist }, song.year, song.license))
            } else {
                onlineQuery = if (action == SongAction.GoToArtist) song.artist else "${song.artist} ${song.album}".trim()
                val bare = song.id.removePrefix(Online.PREFIX_LOCAL)
                catalog = if (bare.startsWith(Online.PREFIX_JAMENDO)) Catalog.Jamendo else Catalog.Archive
                select(Section.Online)
                searchOnline()
            }
            return
        }
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
            SongAction.GoToAlbum -> openAlbum(Album(song.album, song.albumArtist.ifBlank { song.artist }, song.year))
            SongAction.GoToArtist -> openArtist(Artist(song.artist))
        }
    }

    fun toggleFavorite(song: Song) {
        val wasFavorite = song.id in favoriteIds
        store.toggleFavorite(song)
        notify(if (wasFavorite) "Убрано из избранного" else "Добавлено в избранное")
    }

    fun cover(song: Song) = routed?.songCover(song)

    // --- Музыка из интернета ---

    fun selectCatalog(c: Catalog) {
        if (c == catalog) return
        catalog = c
        searchOnline()
    }

    /** Поиск в выбранном каталоге; пустой запрос показывает популярное. */
    fun searchOnline() {
        val c = catalog
        val q = onlineQuery.trim()
        onlineJob?.cancel()
        onlineJob = scope.launch {
            onlineLoading = true
            onlineError = null
            try {
                when (c) {
                    Catalog.Archive -> {
                        onlineReleases = Online.searchArchive(q)
                        onlineSongs = emptyList()
                    }
                    Catalog.Jamendo -> {
                        onlineSongs = Online.searchJamendo(q, settings.jamendoClientId)
                        onlineReleases = emptyList()
                    }
                }
                onlineShown = c to q
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineSongs = emptyList()
                onlineReleases = emptyList()
                onlineShown = c to q
                onlineError = when (e) {
                    is java.net.UnknownHostException -> "Нет подключения к интернету"
                    is java.net.SocketTimeoutException -> "Каталог не отвечает, попробуйте ещё раз"
                    else -> e.localizedMessage ?: "Ошибка поиска"
                }
            } finally {
                onlineLoading = false
            }
        }
    }

    /** Открыть раздел «Интернет» и, если там ещё пусто, сразу показать популярное. */
    fun ensureOnlineLoaded() {
        if (onlineShown == null && !onlineLoading) searchOnline()
    }

    fun openRelease(release: OnlineAlbum) {
        val screen = Screen.OnlineRelease(release)
        push(screen)
        if (detailSongs.containsKey(screen)) return
        scope.launch {
            try {
                detailSongs[screen] = Online.archiveTracks(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailSongs[screen] = emptyList()
                notify(e.localizedMessage ?: "Не удалось открыть релиз")
            }
        }
    }

    fun playRelease(release: OnlineAlbum) {
        val screen = Screen.OnlineRelease(release)
        detailSongs[screen]?.let { if (it.isNotEmpty()) return play(it) }
        scope.launch {
            try {
                val tracks = Online.archiveTracks(release)
                detailSongs[screen] = tracks
                if (tracks.isEmpty()) notify("В релизе нет MP3-файлов") else play(tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.localizedMessage ?: "Не удалось открыть релиз")
            }
        }
    }

    // --- Загрузки ---

    val downloadDir: File get() = settings.downloadDir.ifBlank { null }?.let(::File) ?: Online.defaultDir()

    fun isDownloaded(song: Song) = song.isLocal || song.id in downloadedIds

    fun download(song: Song) {
        if (!song.isOnline) return
        if (song.id in downloadedIds || downloadJobs.containsKey(song.id)) return
        if (!Online.canDownload(song)) {
            notify("Исполнитель не разрешил скачивать этот трек — его можно только слушать")
            return
        }
        downloadProgress[song.id] = 0f
        val job = scope.launch {
            try {
                downloadSlots.withPermit {
                    val saved = Online.download(song, downloadDir) { p ->
                        scope.launch { if (downloadJobs.containsKey(song.id)) downloadProgress[song.id] = p }
                    }
                    store.addDownload(saved)
                    notify("Скачано: ${song.title}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("download failed: ${song.url}", e)
                notify("Не удалось скачать «${song.title}»: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                downloadJobs.remove(song.id)
                downloadProgress.remove(song.id)
            }
        }
        downloadJobs[song.id] = job
    }

    fun downloadAll(list: List<Song>) {
        val todo = list.filter { it.isOnline && Online.canDownload(it) && it.id !in downloadedIds && !downloadJobs.containsKey(it.id) }
        if (todo.isEmpty()) {
            notify("Всё уже скачано")
            return
        }
        todo.forEach(::download)
        notify("Скачивается ${plural(todo.size, "трек", "трека", "треков")}")
    }

    fun cancelDownload(song: Song) {
        downloadJobs.remove(song.id)?.cancel()
        downloadProgress.remove(song.id)
    }

    /** Удалить скачанный файл с диска и из списка загрузок. */
    fun deleteDownload(song: Song) {
        val local = if (song.isLocal) song else downloads.firstOrNull { it.id == Online.PREFIX_LOCAL + song.id } ?: return
        // Открытый в плеере файл Windows удалить не даст — сначала освобождаем его.
        if (player.state.value.current?.id == local.id) player.stopAndClear()
        store.removeDownload(local.id)
        val file = File(local.url)
        scope.launch {
            val deleted = withContext(Dispatchers.IO) {
                var ok = false
                for (attempt in 0 until 10) {
                    if (!file.exists() || file.delete()) {
                        ok = true
                        break
                    }
                    delay(300)
                }
                ok
            }
            notify(if (deleted) "Файл удалён" else "Не удалось удалить файл — он занят другой программой")
        }
    }

    fun openDownloadsFolder() {
        runCatching {
            downloadDir.mkdirs()
            Desktop.getDesktop().open(downloadDir)
        }.onFailure { notify("Не удалось открыть папку") }
    }

    fun setJamendoClientId(id: String) = store.updateSettings { it.copy(jamendoClientId = id.trim()) }
    fun setDownloadDir(path: String) = store.updateSettings { it.copy(downloadDir = path) }
    fun albumCover(album: Album) = source?.albumCover(album)
    fun artistCover(artist: Artist) = source?.artistCover(artist)

    // --- Настройки ---

    fun setThemeMode(mode: ThemeMode) = store.updateSettings { it.copy(themeMode = mode) }
    fun setDynamicColors(value: Boolean) = store.updateSettings { it.copy(dynamicColors = value) }
    fun setTranscode(value: Boolean) = store.updateSettings { it.copy(transcode = value) }
    fun setMediaKeys(value: Boolean) = store.updateSettings { it.copy(mediaKeys = value) }
    fun setCloseToTray(value: Boolean) = store.updateSettings { it.copy(closeToTray = value) }
    fun updateSettings(s: (Settings) -> Settings) = store.updateSettings(s)

    fun clearHistory() {
        store.clearStats()
        notify("История очищена")
    }

    fun notify(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2600)
            toast = null
        }
    }

    private companion object {
        const val PAGE = 300
    }
}
