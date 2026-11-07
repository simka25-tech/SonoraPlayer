package com.sonora.player.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.sonora.player.data.MusicSource
import com.sonora.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Снимок позиции воспроизведения. Хранится отдельно от [PlayerUiState], чтобы ход трека
 * не вызывал перерисовку всего интерфейса: UI сам досчитывает позицию на каждом кадре.
 */
data class PositionState(
    val position: Long = 0,
    val at: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
) {
    fun now(): Long =
        if (playing) position + ((SystemClock.elapsedRealtime() - at) * speed).toLong() else position
}

data class PlayerUiState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val speed: Float = 1f,
    val sleepEndsAt: Long? = null,
    val sleepEndOfTrack: Boolean = false,
)

/** Обёртка над MediaController: состояние плеера в виде StateFlow и удобные команды. */
class PlayerController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private val songs = mutableMapOf<String, Song>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    private val _position = MutableStateFlow(PositionState())
    val position: StateFlow<PositionState> = _position

    private var ticker: Job? = null
    private var sleepJob: Job? = null

    /** Вызывается, когда начинает играть новый трек (для истории и статистики). */
    var onSongStarted: ((Song) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) updateTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let(songs::get)?.let { onSongStarted?.invoke(it) }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepEndOfTrack) {
                controller?.pause()
                _state.update { it.copy(sleepEndOfTrack = false) }
            }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            pending.forEach { it(c) }
            pending.clear()
            sync(queueChanged = true)
            updateTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else {
            pending += block
            connect()
        }
    }

    fun play(list: List<Song>, index: Int, source: MusicSource, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        withController { c ->
            list.forEach { songs[it.id] = it }
            val start = if (shuffle) Random.nextInt(list.size) else index.coerceIn(0, list.lastIndex)
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(list.map { it.toMediaItem(source) }, start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: Song, source: MusicSource) = enqueue(song, source, next = true)

    fun addToQueue(song: Song, source: MusicSource) = enqueue(song, source, next = false)

    private fun enqueue(song: Song, source: MusicSource, next: Boolean) = withController { c ->
        songs[song.id] = song
        val item = song.toMediaItem(source)
        when {
            c.mediaItemCount == 0 -> {
                c.setMediaItem(item)
                c.prepare()
                c.play()
            }
            next -> c.addMediaItem(c.currentMediaItemIndex + 1, item)
            else -> c.addMediaItem(item)
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms.coerceAtLeast(0))

    fun skipTo(index: Int) = controller?.run {
        seekToDefaultPosition(index)
        play()
    }

    fun removeAt(index: Int) = controller?.removeMediaItem(index)

    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = controller?.setPlaybackSpeed(speed)

    /** Таймер сна с плавным затуханием громкости за последние 10 секунд. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val total = minutes * 60_000L
        _state.update { it.copy(sleepEndsAt = System.currentTimeMillis() + total, sleepEndOfTrack = false) }
        sleepJob = scope.launch {
            delay((total - FADE_MS).coerceAtLeast(0))
            val steps = 20
            for (i in steps downTo 1) {
                controller?.volume = i / steps.toFloat()
                delay(FADE_MS / steps)
            }
            controller?.pause()
            controller?.volume = 1f
            _state.update { it.copy(sleepEndsAt = null) }
        }
    }

    fun sleepAtEndOfTrack() {
        cancelSleepTimer()
        _state.update { it.copy(sleepEndOfTrack = true) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        controller?.volume = 1f
        _state.update { it.copy(sleepEndsAt = null, sleepEndOfTrack = false) }
    }

    fun stopAndClear() {
        cancelSleepTimer()
        controller?.run {
            stop()
            clearMediaItems()
        }
        songs.clear()
        sync(queueChanged = true)
    }

    private fun sync(queueChanged: Boolean) {
        val c = controller ?: return
        val current = c.currentMediaItem?.mediaId?.let(songs::get)
        _state.update { s ->
            s.copy(
                current = current,
                isPlaying = c.isPlaying,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                duration = c.duration.takeIf { it > 0 } ?: (current?.durationMs ?: 0),
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                index = c.currentMediaItemIndex,
                speed = c.playbackParameters.speed,
                queue = if (queueChanged) {
                    (0 until c.mediaItemCount).mapNotNull { songs[c.getMediaItemAt(it).mediaId] }
                } else s.queue,
            )
        }
        syncPosition(c)
    }

    private fun syncPosition(c: MediaController) {
        _position.value = PositionState(
            position = c.currentPosition,
            at = SystemClock.elapsedRealtime(),
            playing = c.isPlaying,
            speed = c.playbackParameters.speed,
        )
    }

    /** Редкая сверка позиции с плеером — между сверками UI интерполирует сам. */
    private fun updateTicker() {
        ticker?.cancel()
        if (controller?.isPlaying != true) return
        ticker = scope.launch {
            while (isActive) {
                delay(5000)
                controller?.let(::syncPosition)
            }
        }
    }

    private fun Song.toMediaItem(source: MusicSource): MediaItem {
        val uri = Uri.parse(source.streamUrl(this))
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(source.songCover(this)?.let(Uri::parse))
                    .build(),
            )
            .build()
    }

    private companion object {
        const val FADE_MS = 10_000L
    }
}
