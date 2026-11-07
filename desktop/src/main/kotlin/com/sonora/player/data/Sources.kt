package com.sonora.player.data

import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl

class SynologySource(private val api: SynologyApi, account: String) : MusicSource {
    override val title = "$account@${api.baseUrl.toHttpUrl().host}"
    override var transcode = false

    override suspend fun songs(offset: Int, limit: Int) = api.songs(offset, limit)
    override suspend fun albums() = api.albums()
    override suspend fun artists() = api.artists()

    override suspend fun albumSongs(album: Album) =
        api.songs(0, 1000, mapOf("album" to album.name, "album_artist" to album.artist)).items
            .sortedWith(compareBy<Song> { it.track }.thenBy { it.title })

    override suspend fun artistSongs(artist: Artist) =
        api.songs(0, 1000, mapOf("artist" to artist.name)).items

    override suspend fun search(query: String) = api.search(query)

    override fun streamUrl(song: Song): String =
        // JavaFX воспроизводит MP3 надёжно; FLAC, ALAC, OGG и прочее NAS перекодирует в MP3 на лету.
        if (transcode || song.format.lowercase() !in DIRECT_FORMATS) {
            api.url(SynologyApi.STREAM, "transcode", mapOf("id" to song.id, "format" to "mp3"), "/0.mp3")
        } else {
            api.url(SynologyApi.STREAM, "stream", mapOf("id" to song.id), "/0.mp3")
        }.orEmpty()

    override fun songCover(song: Song) =
        api.url(SynologyApi.COVER, "getsongcover", mapOf("id" to song.id))

    override fun albumCover(album: Album) = api.url(
        SynologyApi.COVER, "getcover",
        mapOf("album_name" to album.name, "album_artist_name" to album.artist),
    )

    override fun artistCover(artist: Artist) =
        api.url(SynologyApi.COVER, "getcover", mapOf("artist_name" to artist.name))

    override fun owns(song: Song) = !song.id.startsWith(DemoSource.PREFIX)

    override suspend fun logout() = api.logout()

    private companion object {
        val DIRECT_FORMATS = setOf("mp3", "")
    }
}

/** Демо-библиотека: свободные треки SoundHelix, чтобы попробовать приложение без NAS. */
class DemoSource : MusicSource {
    override val title = "Демо-библиотека"
    override var transcode = false

    private val artistNames = listOf("Neon Drift", "Aurora Lane", "Midnight Echo", "Solar Tide")
    private val albumNames = listOf(
        "Night City", "Afterglow", "Northern Lights", "Polaris",
        "Static Dreams", "Hollow Moon", "Low Tide", "Sunrise FM",
    )
    private val titles = listOf(
        "Electric Rain", "Glass Horizon", "Velvet Sky", "Kaleidoscope",
        "Paper Planes", "Slow Motion", "Gravity", "Neon Hearts",
        "Waves", "Echoes", "Ultraviolet", "Sunset Drive",
        "Lost Signal", "Starlight", "Daydream", "Afterhours",
    )

    private val all = titles.mapIndexed { i, t ->
        val a = i / 2
        Song(
            id = "$PREFIX${i + 1}", title = t,
            artist = artistNames[a / 2], album = albumNames[a], albumArtist = artistNames[a / 2],
            year = 2018 + a, genre = "Electronic", track = i % 2 + 1, bitrate = 320, format = "mp3",
        )
    }

    override suspend fun songs(offset: Int, limit: Int): Page<Song> {
        delay(300)
        return Page(all.drop(offset).take(limit), all.size)
    }

    override suspend fun albums() = albumNames.mapIndexed { a, n -> Album(n, artistNames[a / 2], 2018 + a) }
    override suspend fun artists() = artistNames.map(::Artist)
    override suspend fun albumSongs(album: Album) = all.filter { it.album == album.name }
    override suspend fun artistSongs(artist: Artist) = all.filter { it.artist == artist.name }

    override suspend fun search(query: String): SearchResult {
        fun String.has() = contains(query, ignoreCase = true)
        return SearchResult(
            songs = all.filter { it.title.has() || it.artist.has() || it.album.has() },
            albums = albums().filter { it.name.has() || it.artist.has() },
            artists = artists().filter { it.name.has() },
        )
    }

    override fun streamUrl(song: Song) =
        "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-${song.id.removePrefix(PREFIX)}.mp3"

    override fun songCover(song: Song) = albumCover(Album(song.album, song.albumArtist))
    override fun albumCover(album: Album) = "https://picsum.photos/seed/sonora-${album.name.slug()}/600/600"
    override fun artistCover(artist: Artist) = "https://picsum.photos/seed/artist-${artist.name.slug()}/400/400"
    override fun owns(song: Song) = song.id.startsWith(PREFIX)

    private fun String.slug() = lowercase().replace(Regex("[^a-z0-9]+"), "-")

    companion object {
        const val PREFIX = "demo-"
    }
}
