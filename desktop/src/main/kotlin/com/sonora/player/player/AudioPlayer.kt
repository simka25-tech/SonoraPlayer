package com.sonora.player.player

import com.sonora.player.data.Log
import com.sonora.player.data.MusicSource
import com.sonora.player.data.Song
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaException
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.random.Random

enum class RepeatMode { Off, All, One }

fun nowMs(): Long = System.nanoTime() / 1_000_000

/**
 * Снимок позиции. Хранится отдельно от [PlayerUiState], чтобы ход трека не перерисовывал
 * весь интерфейс: UI досчитывает позицию на каждом кадре сам.
 */
data class PositionState(
    val position: Long = 0,
    val at: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
) {
    fun now(): Long = if (playing) position + ((nowMs() - at) * speed).toLong() else position
}

data class PlayerUiState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val speed: Float = 1f,
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val sleepEndsAt: Long? = null,
    val sleepEndOfTrack: Boolean = false,
)

/**
 * Плеер на JavaFX Media. Всё изменяемое состояние живёт в потоке JavaFX,
 * а наружу отдаётся через StateFlow — интерфейс Compose только читает его.
 */
class AudioPlayer(initialVolume: Float) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "sonora-loader").apply { isDaemon = true } }

    private val _state = MutableStateFlow(PlayerUiState(volume = initialVolume))
    val state: StateFlow<PlayerUiState> = _state

    private val _position = MutableStateFlow(PositionState())
    val position: StateFlow<PositionState> = _position

    var onSongStarted: ((Song) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onVolumeChanged: ((Float) -> Unit)? = null

    // --- Состояние потока JavaFX ---
    private var mp: MediaPlayer? = null
    private var generation = 0
    private var ready = false
    private var ended = false
    private var pendingSeek: Long? = null
    private var queue: List<Song> = emptyList()
    private var original: List<Song> = emptyList()
    private var index = 0
    private var source: MusicSource? = null
    private var shuffle = false
    private var repeat = RepeatMode.Off
    private var speed = 1f
    private var volume = initialVolume
    private var muted = false
    private var fade = 1f
    private var wantPlay = false
    private var failures = 0
    private var sleepEndOfTrack = false
    private var currentUrl = ""
    private var resumes = 0
    private var goodPos = 0L
    private var goodAt = 0L

    private var ticker: Job? = null
    private var sleepJob: Job? = null

    private fun fx(block: () -> Unit) {
        if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
    }

    // --- Команды ---

    fun play(list: List<Song>, start: Int, src: MusicSource, shuffled: Boolean = false) = fx {
        if (list.isEmpty()) return@fx
        source = src
        original = list
        shuffle = shuffled
        if (shuffled) {
            val first = Random.nextInt(list.size)
            queue = listOf(list[first]) + (list.take(first) + list.drop(first + 1)).shuffled()
            index = 0
        } else {
            queue = list
            index = start.coerceIn(0, list.lastIndex)
        }
        load(index, autoplay = true)
    }

    fun playNext(song: Song, src: MusicSource) = enqueue(song, src, next = true)
    fun addToQueue(song: Song, src: MusicSource) = enqueue(song, src, next = false)

    private fun enqueue(song: Song, src: MusicSource, next: Boolean) = fx {
        if (queue.isEmpty()) {
            play(listOf(song), 0, src)
            return@fx
        }
        val at = if (next) index + 1 else queue.size
        queue = queue.toMutableList().apply { add(at, song) }
        original = if (shuffle) original + song else queue
        publish()
    }

    fun toggle() = fx {
        val p = mp
        when {
            p == null -> if (queue.isNotEmpty()) load(index, autoplay = true)
            wantPlay -> {
                wantPlay = false
                p.pause()
            }
            else -> {
                wantPlay = true
                if (ended) {
                    ended = false
                    p.seek(Duration.ZERO)
                }
                if (ready) p.play()
            }
        }
        publish()
    }

    fun next() = fx { advance(auto = false) }

    fun previous() = fx {
        if (currentPosition() > 3000 || index == 0) seekInternal(0) else load(index - 1, autoplay = true)
    }

    fun seekTo(ms: Long) = fx { seekInternal(ms.coerceAtLeast(0)) }

    fun seekBy(deltaMs: Long) = fx {
        val dur = durationMs()
        val target = (currentPosition() + deltaMs).coerceAtLeast(0)
        seekInternal(if (dur > 0) target.coerceAtMost(dur - 500) else target)
    }

    fun skipTo(i: Int) = fx {
        if (i in queue.indices) load(i, autoplay = true)
    }

    fun removeAt(i: Int) = fx {
        if (i !in queue.indices) return@fx
        if (i == index) {
            if (queue.size == 1) {
                stopAndClear()
                return@fx
            }
            val removed = queue[i]
            queue = queue.toMutableList().apply { removeAt(i) }
            original = original - removed
            load(index.coerceAtMost(queue.lastIndex), autoplay = wantPlay)
            return@fx
        }
        val removed = queue[i]
        queue = queue.toMutableList().apply { removeAt(i) }
        original = if (shuffle) original - removed else queue
        if (i < index) index--
        publish()
    }

    /** Переставить трек в очереди (перетаскивание). */
    fun move(from: Int, to: Int) = fx {
        if (from !in queue.indices || to !in queue.indices || from == to) return@fx
        val list = queue.toMutableList()
        list.add(to, list.removeAt(from))
        index = when (index) {
            from -> to
            in (minOf(from, to)..maxOf(from, to)) -> if (from < to) index - 1 else index + 1
            else -> index
        }
        queue = list
        if (!shuffle) original = list
        publish()
    }

    fun toggleShuffle() = fx {
        val cur = queue.getOrNull(index)
        shuffle = !shuffle
        if (cur != null) {
            if (shuffle) {
                original = queue
                queue = listOf(cur) + (queue.take(index) + queue.drop(index + 1)).shuffled()
                index = 0
            } else {
                queue = original
                index = queue.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
            }
        }
        publish()
    }

    fun cycleRepeat() = fx {
        repeat = RepeatMode.entries[(repeat.ordinal + 1) % RepeatMode.entries.size]
        publish()
    }

    fun setSpeed(value: Float) = fx {
        speed = value
        mp?.rate = value.toDouble()
        publish()
    }

    fun setVolume(value: Float) = fx {
        volume = value.coerceIn(0f, 1f)
        if (volume > 0f) muted = false
        applyVolume()
        onVolumeChanged?.invoke(volume)
        publish()
    }

    fun toggleMute() = fx {
        muted = !muted
        applyVolume()
        publish()
    }

    /** Таймер сна с плавным затуханием за последние 10 секунд. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val total = minutes * 60_000L
        fx { sleepEndOfTrack = false }
        _state.update { it.copy(sleepEndsAt = System.currentTimeMillis() + total, sleepEndOfTrack = false) }
        sleepJob = scope.launch {
            delay((total - FADE_MS).coerceAtLeast(0))
            val steps = 40
            for (i in steps downTo 1) {
                val f = i / steps.toFloat()
                fx {
                    fade = f
                    applyVolume()
                }
                delay(FADE_MS / steps)
            }
            fx {
                wantPlay = false
                mp?.pause()
                fade = 1f
                applyVolume()
                publish()
            }
            _state.update { it.copy(sleepEndsAt = null) }
        }
    }

    fun sleepAtEndOfTrack() {
        cancelSleepTimer()
        fx {
            sleepEndOfTrack = true
            publish()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        fx {
            fade = 1f
            sleepEndOfTrack = false
            applyVolume()
        }
        _state.update { it.copy(sleepEndsAt = null, sleepEndOfTrack = false) }
    }

    fun stopAndClear() = fx {
        Log.d("stopAndClear")
        generation++
        disposePlayer()
        queue = emptyList()
        original = emptyList()
        index = 0
        wantPlay = false
        publish()
    }

    fun shutdown() {
        scope.cancel()
        fx { disposePlayer() }
        loader.shutdownNow()
    }

    // --- Внутренняя кухня (поток JavaFX) ---

    private fun load(i: Int, autoplay: Boolean, startAt: Long? = null) {
        disposePlayer()
        val song = queue.getOrNull(i)
        val src = source
        if (song == null || src == null) {
            publish()
            return
        }
        index = i
        wantPlay = autoplay
        ready = false
        ended = false
        pendingSeek = startAt
        goodPos = startAt ?: 0L
        goodAt = 0L
        if (startAt == null) resumes = 0
        val gen = ++generation
        val url = src.streamUrl(song)
        currentUrl = url
        Log.d("load #$i ${song.title}: $url" + (startAt?.let { " @${it / 1000}s" } ?: ""))
        if (startAt == null) onSongStarted?.invoke(song)
        publish()
        // Media и MediaPlayer открывают сетевое соединение — создаём их вне потока JavaFX.
        loader.execute {
            val player = try {
                MediaPlayer(Media(url))
            } catch (e: Throwable) {
                Log.d("Не удалось открыть $url", e)
                Platform.runLater { if (gen == generation) fail(song, e) }
                return@execute
            }
            player.setOnReady { if (gen == generation && mp === player) onReady(player) }
            player.setOnPlaying { if (gen == generation) publish() }
            player.setOnPaused { if (gen == generation) publish() }
            player.setOnStalled { if (gen == generation) publish() }
            player.setOnEndOfMedia { if (gen == generation) onEnded() }
            player.setOnError {
                Log.d("Ошибка воспроизведения ${song.title}", player.error)
                if (gen == generation) fail(song, player.error)
            }
            Platform.runLater {
                if (gen != generation) {
                    player.dispose()
                    return@runLater
                }
                mp = player
                player.rate = speed.toDouble()
                applyVolume()
                if (player.status == MediaPlayer.Status.READY) onReady(player)
            }
        }
    }

    private fun onReady(p: MediaPlayer) {
        if (ready) return
        Log.d("ready, duration=${p.media.duration}")
        ready = true
        failures = 0
        pendingSeek?.let { p.seek(Duration.millis(it.toDouble())) }
        pendingSeek = null
        if (wantPlay) p.play()
        publish()
    }

    private fun onEnded() {
        val p = mp ?: return
        // Сетевой поток оборвался раньше времени — переподключаемся и продолжаем с того же места.
        val played = estimatedPosition()
        val total = durationMs()
        if (currentUrl.startsWith("http") && total > 0 && played < total - 15_000 && resumes < 5) {
            resumes++
            Log.d("stream ended early at ${played / 1000}s of ${total / 1000}s, resuming (#$resumes)")
            load(index, autoplay = wantPlay, startAt = played)
            return
        }
        if (repeat == RepeatMode.One) {
            goodPos = 0L
            goodAt = 0L
            p.seek(Duration.ZERO)
            p.play()
            publish()
            return
        }
        advance(auto = true)
    }

    private fun advance(auto: Boolean) {
        if (queue.isEmpty()) return
        val stopAfter = auto && sleepEndOfTrack
        if (stopAfter) {
            sleepEndOfTrack = false
            _state.update { it.copy(sleepEndOfTrack = false) }
        }
        when {
            index < queue.lastIndex -> load(index + 1, autoplay = !stopAfter)
            repeat == RepeatMode.All || !auto -> load(0, autoplay = !stopAfter)
            else -> {
                // Очередь закончилась — встаём на начало последнего трека.
                wantPlay = false
                ended = true
                mp?.pause()
                publish()
            }
        }
    }

    private fun fail(song: Song, e: Throwable?) {
        failures++
        val unsupported = (e as? MediaException)?.type == MediaException.Type.MEDIA_UNSUPPORTED
        onError?.invoke(
            if (unsupported) "Формат «${song.title}» не поддерживается — включите перекодирование в настройках"
            else "Не удалось воспроизвести «${song.title}»",
        )
        disposePlayer()
        if (failures < 3 && index < queue.lastIndex) {
            load(index + 1, autoplay = wantPlay)
        } else {
            wantPlay = false
            publish()
        }
    }

    private fun seekInternal(ms: Long) {
        val p = mp
        ended = false
        if (p == null || !ready) {
            pendingSeek = ms
        } else {
            p.seek(Duration.millis(ms.toDouble()))
        }
        goodPos = ms
        goodAt = if (p?.status == MediaPlayer.Status.PLAYING && wantPlay) nowMs() else 0L
        publishPosition(ms)
    }

    private fun disposePlayer() {
        mp?.let {
            it.setOnEndOfMedia(null)
            it.setOnError(null)
            runCatching { it.stop() }
            runCatching { it.dispose() }
        }
        mp = null
        ready = false
    }

    private fun applyVolume() {
        mp?.volume = if (muted) 0.0 else (volume * fade).toDouble()
    }

    private fun currentPosition(): Long =
        mp?.takeIf { ready }?.currentTime?.toMillis()?.toLong() ?: (pendingSeek ?: 0L)

    private fun durationMs(): Long {
        val d = mp?.takeIf { ready }?.media?.duration
        val known = d?.takeIf { !it.isUnknown && !it.isIndefinite }?.toMillis()?.toLong()
        return known ?: queue.getOrNull(index)?.durationMs ?: 0L
    }

    private fun publish() {
        val p = mp
        val status = p?.status
        val loading = wantPlay && (p == null || !ready || status == MediaPlayer.Status.STALLED)
        _state.value = PlayerUiState(
            current = queue.getOrNull(index),
            isPlaying = wantPlay,
            buffering = loading && queue.isNotEmpty(),
            duration = durationMs(),
            shuffle = shuffle,
            repeat = repeat,
            queue = queue,
            index = index,
            speed = speed,
            volume = volume,
            muted = muted,
            sleepEndsAt = _state.value.sleepEndsAt,
            sleepEndOfTrack = sleepEndOfTrack,
        )
        publishPosition(null)
        updateTicker(status == MediaPlayer.Status.PLAYING && wantPlay)
    }

    private fun publishPosition(override: Long?) {
        val playing = mp?.status == MediaPlayer.Status.PLAYING && wantPlay
        val pos = override ?: checkedPosition(playing)
        _position.value = PositionState(pos, nowMs(), playing, speed)
    }

    /**
     * Позиция с защитой от сбоев JavaFX: при обрыве сетевого потока currentTime
     * иногда прыгает в конец трека. Такой скачок игнорируем и считаем позицию по часам.
     */
    private fun checkedPosition(playing: Boolean): Long {
        val raw = currentPosition()
        if (!ready) return raw
        val now = nowMs()
        val expected = estimatedPosition()
        val pos = if (raw > expected + 8_000) expected else raw
        goodPos = pos
        goodAt = if (playing) now else 0L
        return pos
    }

    /** Сколько реально проиграно сейчас — по последней достоверной позиции. */
    private fun estimatedPosition(): Long =
        if (goodAt > 0) goodPos + ((nowMs() - goodAt) * speed).toLong() else goodPos

    /** Редкая сверка позиции с плеером — между сверками UI интерполирует сам. */
    private fun updateTicker(playing: Boolean) {
        if (!playing) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(2000)
                fx { if (mp?.status == MediaPlayer.Status.PLAYING) publishPosition(null) }
            }
        }
    }

    companion object {
        private const val FADE_MS = 10_000L

        /** Запуск движка JavaFX (без окон) — нужен для MediaPlayer. */
        fun initPlatform() {
            Platform.setImplicitExit(false)
            runCatching { Platform.startup {} }
        }
    }
}
