package com.sonora.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Открытые каталоги, где музыку можно легально слушать и скачивать. */
enum class Catalog(val title: String, val note: String) {
    Archive("Internet Archive", "Netlabels, живые концерты и музыка под Creative Commons или в общественном достоянии"),
    Jamendo("Jamendo", "Независимые исполнители, треки под лицензиями Creative Commons"),
}

/** Релиз в Internet Archive: внутри него лежат файлы-треки. */
data class OnlineAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val license: String,
) {
    val cover get() = "https://archive.org/services/img/$id"
    val page get() = "https://archive.org/details/$id"
}

class OnlineException(message: String) : Exception(message)

object Online {
    const val PREFIX_ARCHIVE = "ia-"
    const val PREFIX_JAMENDO = "jm-"
    const val PREFIX_LOCAL = "dl-"

    private val json = Json { ignoreUnknownKeys = true }

    /** Только то, что разрешено распространять: netlabels, etree и всё с лицензией CC или Public Domain. */
    private const val LEGAL =
        "mediatype:audio AND -collection:librivoxaudio AND -collection:audio_bookspoetry AND " +
            "(collection:netlabels OR collection:etree OR licenseurl:*creativecommons* OR licenseurl:*publicdomain*)"

    private suspend fun get(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "Sonora/1.0 (desktop music player)").build()
        Net.client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw OnlineException("Каталог ответил ошибкой ${r.code}")
            json.parseToJsonElement(r.body!!.string())
        }
    }

    // ---------- Internet Archive ----------

    suspend fun searchArchive(query: String): List<OnlineAlbum> {
        val q = query.trim()
        val full = if (q.isEmpty()) "$LEGAL AND collection:netlabels" else "($q) AND $LEGAL"
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", full)
            .apply { listOf("identifier", "title", "creator", "year", "licenseurl").forEach { addQueryParameter("fl[]", it) } }
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("rows", "60")
            .addQueryParameter("output", "json")
            .build()
        val docs = get(url).obj("response")?.arr("docs") ?: return emptyList()
        return docs.mapNotNull { el ->
            val d = el as? JsonObject ?: return@mapNotNull null
            val id = d.str("identifier") ?: return@mapNotNull null
            OnlineAlbum(
                id = id,
                title = d.str("title") ?: id,
                artist = d.str("creator").orEmpty(),
                year = d.str("year")?.take(4)?.toIntOrNull() ?: 0,
                license = licenseName(d.str("licenseurl")),
            )
        }
    }

    suspend fun archiveTracks(album: OnlineAlbum): List<Song> {
        val root = get("https://archive.org/metadata/${album.id}".toHttpUrl()).jsonObject
        val meta = root.obj("metadata")
        val creator = meta?.str("creator") ?: album.artist
        val license = licenseName(meta?.str("licenseurl")).ifBlank { album.license }
        val files = root.arr("files")?.mapNotNull { it as? JsonObject }.orEmpty()
        val mp3 = files.filter { it.str("format")?.contains("MP3", ignoreCase = true) == true && it.str("name") != null }
        // У одного трека бывает несколько копий (оригинал, _vbr, _64kb) — оставляем лучшую:
        // оригинал, а если его нет — самый большой файл.
        fun key(f: JsonObject): String {
            val base = f.str("name")!!.substringBeforeLast('.').lowercase()
                .replace(Regex("_(vbr|\\d+kb(ps)?)$"), "")
            return base
        }
        return mp3.groupBy(::key).values
            .map { group ->
                group.maxWith(
                    compareBy<JsonObject>({ if (it.str("source") == "original") 1 else 0 }, { it.str("size")?.toLongOrNull() ?: 0L }),
                )
            }
            .mapNotNull { f ->
                val name = f.str("name") ?: return@mapNotNull null
                val url = HttpUrl.Builder().scheme("https").host("archive.org")
                    .addPathSegment("download").addPathSegment(album.id)
                    .apply { name.split('/').forEach { addPathSegment(it) } }
                    .build().toString()
                Song(
                    id = "$PREFIX_ARCHIVE${album.id}/$name",
                    title = f.str("title")?.ifBlank { null } ?: name.substringAfterLast('/').substringBeforeLast('.'),
                    artist = f.str("artist")?.ifBlank { null } ?: creator,
                    album = album.title,
                    albumArtist = creator,
                    durationMs = parseLength(f.str("length")),
                    year = album.year,
                    track = f.str("track")?.substringBefore('/')?.toIntOrNull() ?: 0,
                    format = "mp3",
                    url = url,
                    coverUrl = album.cover,
                    license = license,
                    pageUrl = album.page,
                )
            }
            .sortedWith(compareBy<Song>({ if (it.track > 0) it.track else Int.MAX_VALUE }, { it.id.lowercase() }))
    }

    // ---------- Jamendo ----------

    suspend fun searchJamendo(query: String, clientId: String): List<Song> {
        if (clientId.isBlank()) throw OnlineException("Для Jamendo нужен Client ID — укажите его в настройках")
        val b = "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId.trim())
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "60")
            .addQueryParameter("audioformat", "mp32")
            .addQueryParameter("imagesize", "300")
        if (query.isBlank()) b.addQueryParameter("order", "popularity_week")
        else b.addQueryParameter("search", query.trim()).addQueryParameter("order", "relevance")
        val root = get(b.build()).jsonObject
        val headers = root.obj("headers")
        if (headers?.str("status") == "failed") {
            val msg = headers.str("error_message").orEmpty()
            throw OnlineException(if (msg.contains("Client Id", true)) "Jamendo не принял Client ID — проверьте его в настройках" else "Jamendo: $msg")
        }
        return root.arr("results").orEmpty().mapNotNull { el ->
            val t = el as? JsonObject ?: return@mapNotNull null
            val id = t.str("id") ?: return@mapNotNull null
            val audio = t.str("audio")?.ifBlank { null } ?: return@mapNotNull null
            val canDownload = t.str("audiodownload_allowed") != "false"
            Song(
                id = "$PREFIX_JAMENDO$id",
                title = t.str("name").orEmpty(),
                artist = t.str("artist_name").orEmpty(),
                album = t.str("album_name").orEmpty(),
                albumArtist = t.str("artist_name").orEmpty(),
                durationMs = (t.str("duration")?.toDoubleOrNull() ?: 0.0).times(1000).toLong(),
                year = t.str("releasedate")?.take(4)?.toIntOrNull() ?: 0,
                format = "mp3",
                // Скачиваем файл для загрузки, если исполнитель это разрешил, иначе — только прослушивание.
                url = audio,
                coverUrl = t.str("image").orEmpty(),
                license = licenseName(t.str("license_ccurl")),
                pageUrl = t.str("shareurl").orEmpty() + if (canDownload) "" else "#nodownload",
            )
        }
    }

    // ---------- Скачивание ----------

    fun canDownload(song: Song) = song.isOnline && !song.pageUrl.endsWith("#nodownload")

    fun defaultDir(): File = File(System.getProperty("user.home"), "Music${File.separator}Sonora")

    /** Скачивает трек в [dir]; [progress] получает долю от 0 до 1 (или -1, если размер неизвестен). */
    suspend fun download(song: Song, dir: File, progress: (Float) -> Unit): Song = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val base = sanitize(listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "track" })
        var target = File(dir, "$base.mp3")
        var n = 2
        while (target.exists()) target = File(dir, "$base ($n).mp3").also { n++ }
        val part = File(dir, target.name + ".part")
        val request = Request.Builder().url(song.url).header("User-Agent", "Sonora/1.0 (desktop music player)").build()
        try {
            Net.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw OnlineException("Не удалось скачать: ошибка ${r.code}")
                val body = r.body ?: throw OnlineException("Пустой ответ сервера")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            done += read
                            if (done - last > 128 * 1024) {
                                last = done
                                progress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить файл ${target.name}")
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
        progress(1f)
        song.copy(id = PREFIX_LOCAL + song.id, url = target.absolutePath)
    }

    private fun sanitize(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(120).ifBlank { "track" }

    // ---------- Разбор ----------

    private fun parseLength(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        s.toDoubleOrNull()?.let { return (it * 1000).toLong() }
        val parts = s.split(':').mapNotNull { it.toDoubleOrNull() }
        return (parts.fold(0.0) { acc, v -> acc * 60 + v } * 1000).toLong()
    }

    fun licenseName(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val u = url.lowercase()
        return when {
            "publicdomain/zero" in u -> "CC0"
            "publicdomain" in u -> "Public Domain"
            "creativecommons.org/licenses/" in u -> {
                val parts = u.substringAfter("/licenses/").trim('/').split('/')
                "CC " + parts[0].uppercase() + (parts.getOrNull(1)?.let { " $it" } ?: "")
            }
            else -> ""
        }
    }

    private fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { null }
        else -> null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = (this[key] as? JsonArray)?.jsonArray
    private fun JsonElement.obj(key: String) = (this as? JsonObject)?.obj(key)
}

/**
 * Источник для плеера: библиотечные треки идут через NAS/демо, а треки из интернета
 * и скачанные файлы — по собственным ссылкам.
 */
class RoutedSource(private val base: MusicSource) : MusicSource by base {
    override fun streamUrl(song: Song): String = when {
        song.isLocal -> File(song.url).toPath().toUri().toASCIIString()
        song.isOnline -> song.url
        else -> base.streamUrl(song)
    }

    override fun songCover(song: Song): String? =
        if (song.isLocal || song.isOnline) song.coverUrl.ifBlank { null } else base.songCover(song)

    override fun owns(song: Song) = song.isLocal || song.isOnline || base.owns(song)
}
