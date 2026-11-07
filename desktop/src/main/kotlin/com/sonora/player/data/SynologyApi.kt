package com.sonora.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

class SynologyException(val code: Int?) : Exception(messageFor(code)) {
    val isSessionError get() = code in setOf(106, 107, 119)

    companion object {
        fun messageFor(code: Int?) = when (code) {
            400 -> "Неверное имя пользователя или пароль"
            401 -> "Учётная запись отключена"
            402 -> "У пользователя нет доступа к Audio Station"
            403 -> "Введите код двухфакторной аутентификации"
            404 -> "Неверный код двухфакторной аутентификации"
            105 -> "Недостаточно прав"
            106, 107, 119 -> "Сессия истекла, войдите снова"
            null -> "Неизвестная ошибка сервера"
            else -> "Ошибка Synology ($code)"
        }
    }
}

/** Минимальный клиент Synology Web API для Audio Station. */
class SynologyApi(val baseUrl: String) {
    var sid: String? = null

    private data class ApiInfo(val path: String, val version: Int)

    private val apis = mutableMapOf<String, ApiInfo>()
    private val json = Json { ignoreUnknownKeys = true }

    /** Узнаёт пути и версии нужных API у самого NAS. */
    suspend fun queryInfo() {
        val data = request(
            "query.cgi",
            mapOf(
                "api" to "SYNO.API.Info", "version" to "1", "method" to "query",
                "query" to NEEDED.keys.joinToString(","),
            ),
            withSid = false,
        )
        for ((name, preferred) in NEEDED) {
            val o = data.obj(name) ?: continue
            val min = o.int("minVersion").coerceAtLeast(1)
            val max = o.int("maxVersion").coerceAtLeast(min)
            apis[name] = ApiInfo(o.str("path"), preferred.coerceIn(min, max))
        }
        if (AUTH !in apis) throw IOException("По этому адресу не найден Synology DSM")
        if (SONG !in apis) throw IOException("На NAS не установлен пакет Audio Station")
    }

    suspend fun login(account: String, password: String, otp: String?) {
        val params = mutableMapOf(
            "account" to account, "passwd" to password,
            "session" to "AudioStation", "format" to "sid",
        )
        if (!otp.isNullOrBlank()) params["otp_code"] = otp.trim()
        val data = call(AUTH, "login", params, post = true)
        sid = data.str("sid").ifBlank { throw SynologyException(null) }
    }

    suspend fun logout() {
        runCatching { call(AUTH, "logout", mapOf("session" to "AudioStation")) }
        sid = null
    }

    /** Лёгкий запрос, чтобы проверить, что сессия жива. */
    suspend fun ping() {
        call(ARTIST, "list", mapOf("library" to "all", "limit" to "1", "offset" to "0"))
    }

    suspend fun songs(offset: Int, limit: Int, filter: Map<String, String> = emptyMap()): Page<Song> {
        val data = call(
            SONG, "list",
            mapOf(
                "library" to "all", "offset" to "$offset", "limit" to "$limit",
                "additional" to "song_tag,song_audio",
                "sort_by" to "title", "sort_direction" to "ASC",
            ) + filter,
        )
        return Page(data.arr("songs").map(::parseSong), data.int("total"))
    }

    suspend fun albums(): List<Album> {
        val data = call(
            ALBUM, "list",
            mapOf(
                "library" to "all", "offset" to "0", "limit" to "5000",
                "sort_by" to "name", "sort_direction" to "ASC",
            ),
        )
        return data.arr("albums").map(::parseAlbum)
    }

    suspend fun artists(): List<Artist> {
        val data = call(
            ARTIST, "list",
            mapOf(
                "library" to "all", "offset" to "0", "limit" to "5000",
                "sort_by" to "name", "sort_direction" to "ASC",
            ),
        )
        return data.arr("artists").map { Artist(it.str("name")) }.filter { it.name.isNotBlank() }
    }

    suspend fun search(keyword: String): SearchResult {
        val data = call(
            SEARCH, "list",
            mapOf(
                "library" to "all", "keyword" to keyword, "offset" to "0", "limit" to "100",
                "additional" to "song_tag,song_audio",
                "sort_by" to "title", "sort_direction" to "ASC",
            ),
        )
        return SearchResult(
            songs = data.arr("songs").map(::parseSong),
            albums = data.arr("albums").map(::parseAlbum),
            artists = data.arr("artists").map { Artist(it.str("name")) }.filter { it.name.isNotBlank() },
        )
    }

    /** Ссылка на ресурс API (поток, обложка) с подставленной сессией. */
    fun url(api: String, method: String, params: Map<String, String>, pathSuffix: String = ""): String? {
        val info = apis[api] ?: return null
        val b = "$baseUrl/webapi/${info.path}$pathSuffix".toHttpUrl().newBuilder()
            .addQueryParameter("api", api)
            .addQueryParameter("version", info.version.toString())
            .addQueryParameter("method", method)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        sid?.let { b.addQueryParameter("_sid", it) }
        return b.build().toString()
    }

    private suspend fun call(
        api: String,
        method: String,
        params: Map<String, String> = emptyMap(),
        post: Boolean = false,
    ): JsonObject {
        val info = apis[api] ?: throw IOException("API $api недоступно на этом NAS")
        return request(
            info.path,
            mapOf("api" to api, "version" to info.version.toString(), "method" to method) + params,
            post = post,
        )
    }

    private suspend fun request(
        path: String,
        params: Map<String, String>,
        withSid: Boolean = true,
        post: Boolean = false,
    ): JsonObject = withContext(Dispatchers.IO) {
        val all = sid?.takeIf { withSid }?.let { params + ("_sid" to it) } ?: params
        val url = "$baseUrl/webapi/$path".toHttpUrl()
        val request = if (post) {
            val form = FormBody.Builder().apply { all.forEach { (k, v) -> add(k, v) } }.build()
            Request.Builder().url(url).post(form).build()
        } else {
            val full = url.newBuilder().apply { all.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            Request.Builder().url(full).build()
        }
        Net.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IOException("Сервер вернул неожиданный ответ") }
            if ((obj["success"] as? JsonPrimitive)?.booleanOrNull != true) {
                throw SynologyException(obj.obj("error")?.int("code")?.takeIf { it != 0 })
            }
            obj.obj("data") ?: JsonObject(emptyMap())
        }
    }

    private fun parseSong(o: JsonObject): Song {
        val add = o.obj("additional")
        val tag = add?.obj("song_tag")
        val audio = add?.obj("song_audio")
        val albumArtist = tag?.str("album_artist").orEmpty()
        return Song(
            id = o.str("id"),
            title = o.str("title").ifBlank { o.str("path").substringAfterLast('/') },
            artist = tag?.str("artist").orEmpty().ifBlank { albumArtist },
            album = tag?.str("album").orEmpty(),
            albumArtist = albumArtist,
            durationMs = ((audio?.dbl("duration") ?: 0.0) * 1000).toLong(),
            year = tag?.int("year") ?: 0,
            genre = tag?.str("genre").orEmpty(),
            track = tag?.int("track") ?: 0,
            bitrate = (audio?.int("bitrate") ?: 0) / 1000,
            format = audio?.str("codec").orEmpty().ifBlank { o.str("type") },
        )
    }

    private fun parseAlbum(o: JsonObject) = Album(
        name = o.str("name"),
        artist = o.str("album_artist").ifBlank { o.str("display_artist") }.ifBlank { o.str("artist") },
        year = o.int("year"),
    )

    companion object {
        const val AUTH = "SYNO.API.Auth"
        const val SONG = "SYNO.AudioStation.Song"
        const val ALBUM = "SYNO.AudioStation.Album"
        const val ARTIST = "SYNO.AudioStation.Artist"
        const val SEARCH = "SYNO.AudioStation.Search"
        const val STREAM = "SYNO.AudioStation.Stream"
        const val COVER = "SYNO.AudioStation.Cover"

        private val NEEDED = linkedMapOf(
            AUTH to 3, SONG to 1, ALBUM to 1, ARTIST to 1, SEARCH to 1, STREAM to 2, COVER to 3,
        )

        /** "192.168.1.10" → "http://192.168.1.10:5000" и т.п. */
        fun normalizeBase(input: String, https: Boolean): String {
            var s = input.trim().trimEnd('/')
            require(s.isNotEmpty()) { "Укажите адрес NAS" }
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                val port = if (Regex(":\\d+$").containsMatchIn(s)) "" else if (https) ":5001" else ":5000"
                s = (if (https) "https://" else "http://") + s + port
            }
            runCatching { s.toHttpUrl() }.getOrElse { throw IllegalArgumentException("Некорректный адрес NAS") }
            return s
        }
    }
}

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.int(k: String) =
    (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: 0
private fun JsonObject.dbl(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
private fun JsonObject.obj(k: String) = this[k] as? JsonObject
private fun JsonObject.arr(k: String) = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
