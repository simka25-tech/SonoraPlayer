package com.sonora.player.data

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val durationMs: Long = 0,
    val year: Int = 0,
    val genre: String = "",
    val track: Int = 0,
    val bitrate: Int = 0,
    val format: String = "",
)

data class Album(val name: String, val artist: String, val year: Int = 0)

data class Artist(val name: String)

data class Page<T>(val items: List<T>, val total: Int)

data class SearchResult(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/** Источник музыки: Synology Audio Station или демо-библиотека. */
interface MusicSource {
    val title: String
    var transcode: Boolean

    suspend fun songs(offset: Int, limit: Int): Page<Song>
    suspend fun albums(): List<Album>
    suspend fun artists(): List<Artist>
    suspend fun albumSongs(album: Album): List<Song>
    suspend fun artistSongs(artist: Artist): List<Song>
    suspend fun search(query: String): SearchResult

    fun streamUrl(song: Song): String
    fun songCover(song: Song): String?
    fun albumCover(album: Album): String?
    fun artistCover(artist: Artist): String?

    /** Принадлежит ли сохранённая песня (избранное, история) этому источнику. */
    fun owns(song: Song): Boolean

    suspend fun logout() {}
}
