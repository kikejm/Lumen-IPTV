package app.lumentv.data.xtream

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class XtreamCredentials(val server: String, val username: String, val password: String) {
    /** Servidor normalizado: con esquema y sin barra final. */
    val baseUrl: String = normalizeServer(server)

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            // Algunos proveedores dan la URL completa del panel.
            s = s.removeSuffix("/player_api.php").removeSuffix("/get.php")
            // El puerto 80 nunca es TLS: «https://host:80» falla con WRONG_VERSION_NUMBER.
            if (s.startsWith("https://", true) && Regex(":80(/|$)").containsMatchIn(s.substringAfter("://"))) s = "http://" + s.substringAfter("://")
            return s
        }

        /**
         * Un enlace M3U `…/get.php?username=…&password=…` es en realidad un panel Xtream Codes.
         * Devuelve sus credenciales para darlo de alta como Xtream (fichas, series y EPG), o null si
         * no lo es.
         */
        fun fromM3uUrl(url: String): XtreamCredentials? {
            val u = url.trim().toHttpUrlOrNull() ?: return null
            if (!u.encodedPath.endsWith("/get.php")) return null
            val user = u.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
            val pass = u.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
            val scheme = if (u.isHttps && u.port == 80) "http" else u.scheme
            val port = if (u.port == HttpUrl.defaultPort(scheme)) "" else ":${u.port}"
            return XtreamCredentials("$scheme://${u.host}$port${u.encodedPath.removeSuffix("/get.php")}", user, pass)
        }
    }
}

data class XtreamAccount(
    val authenticated: Boolean,
    val status: String,
    val expiresAt: Long,
    val maxConnections: Int,
    val activeConnections: Int,
    val serverTimezone: String,
    val message: String,
)

data class XtreamEpgEntry(
    val title: String,
    val description: String,
    val startAt: Long,
    val stopAt: Long,
    val hasArchive: Boolean,
)

data class VodInfo(
    val name: String,
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val releaseDate: String,
    val durationSecs: Int,
    val rating: String,
    val poster: String,
    val backdrop: String,
    val trailer: String,
    val containerExt: String,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String,
    val image: String,
    val durationSecs: Int,
    val containerExt: String,
)

data class SeriesInfo(
    val name: String,
    val plot: String,
    val cast: String,
    val genre: String,
    val releaseDate: String,
    val rating: String,
    val cover: String,
    val backdrop: String,
    val seasons: Map<Int, List<Episode>>,
)

/**
 * Cliente de la API Xtream Codes (`player_api.php`).
 *
 * Las listas grandes (canales, VOD, series) se leen en streaming con [JsonReader]
 * para no cargar decenas de MB en memoria en un Fire Stick. Los valores se tratan
 * siempre como String porque los paneles mezclan números y cadenas sin criterio.
 */
class XtreamClient(private val http: OkHttpClient, private val json: Json, private val cacheDir: File) {

    suspend fun authenticate(c: XtreamCredentials): XtreamAccount = withContext(Dispatchers.IO) {
        val root = getJson(apiUrl(c)) as? JsonObject ?: throw IOException("Respuesta no válida del servidor")
        val user = root["user_info"] as? JsonObject ?: throw IOException("El servidor no devolvió user_info")
        val server = root["server_info"] as? JsonObject
        XtreamAccount(
            authenticated = user.str("auth") == "1",
            status = user.str("status"),
            expiresAt = user.str("exp_date").toLongOrNull()?.times(1000) ?: 0,
            maxConnections = user.str("max_connections").toIntOrNull() ?: 0,
            activeConnections = user.str("active_cons").toIntOrNull() ?: 0,
            serverTimezone = server?.str("timezone").orEmpty(),
            message = user.str("message"),
        )
    }

    suspend fun categories(c: XtreamCredentials, action: String): List<Map<String, String>> =
        streamList(apiUrl(c, action)) { it }

    /** Recorre en streaming un array JSON de objetos planos, llamando a [onItem] por cada uno. */
    suspend fun <T> streamList(url: HttpUrl, mapper: (Map<String, String>) -> T?): List<T> = withContext(Dispatchers.IO) {
        val out = ArrayList<T>()
        streamEach(url) { obj -> mapper(obj)?.let(out::add) }
        out
    }

    /**
     * Descarga la respuesta a un fichero temporal y luego la recorre en streaming. Separar descarga
     * y parseo evita que el servidor corte la conexión mientras insertamos lotes en Room (lectura lenta).
     */
    suspend fun streamEach(url: HttpUrl, onItem: suspend (Map<String, String>) -> Unit) = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("xtream-", ".json", cacheDir)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} en ${url.encodedPath}")
                val body = resp.body ?: throw IOException("Respuesta vacía")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
            JsonReader(InputStreamReader(tmp.inputStream().buffered(64 * 1024), Charsets.UTF_8)).use { reader ->
                reader.isLenient = true
                when (reader.peek()) {
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) onItem(reader.readFlatObject()) else reader.skipValue()
                        }
                        reader.endArray()
                    }
                    // Algunos paneles devuelven un objeto indexado {"0": {...}, "1": {...}}.
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            reader.nextName()
                            if (reader.peek() == JsonToken.BEGIN_OBJECT) onItem(reader.readFlatObject()) else reader.skipValue()
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
        } finally {
            tmp.delete()
        }
    }

    suspend fun vodInfo(c: XtreamCredentials, vodId: String): VodInfo = withContext(Dispatchers.IO) {
        val root = getJson(apiUrl(c, "get_vod_info", "vod_id" to vodId)) as? JsonObject ?: JsonObject(emptyMap())
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val data = root["movie_data"] as? JsonObject ?: JsonObject(emptyMap())
        VodInfo(
            name = data.str("name").ifEmpty { info.str("name") },
            plot = info.str("plot").ifEmpty { info.str("description") },
            cast = info.str("cast").ifEmpty { info.str("actors") },
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate").ifEmpty { info.str("release_date") },
            durationSecs = info.str("duration_secs").toIntOrNull() ?: 0,
            rating = info.str("rating"),
            poster = info.str("movie_image").ifEmpty { info.str("cover_big") },
            backdrop = info.firstString("backdrop_path"),
            trailer = info.str("youtube_trailer"),
            containerExt = data.str("container_extension").ifEmpty { "mp4" },
        )
    }

    suspend fun seriesInfo(c: XtreamCredentials, seriesId: String): SeriesInfo = withContext(Dispatchers.IO) {
        val root = getJson(apiUrl(c, "get_series_info", "series_id" to seriesId)) as? JsonObject ?: JsonObject(emptyMap())
        val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
        val seasons = sortedMapOf<Int, List<Episode>>()
        fun parseEpisodes(seasonKey: Int?, arr: JsonArray) {
            arr.mapNotNull { it as? JsonObject }.forEach { ep ->
                val epInfo = ep["info"] as? JsonObject ?: JsonObject(emptyMap())
                val season = ep.str("season").toIntOrNull() ?: seasonKey ?: 1
                val episode = Episode(
                    id = ep.str("id"),
                    season = season,
                    number = ep.str("episode_num").toIntOrNull() ?: 0,
                    title = ep.str("title"),
                    plot = epInfo.str("plot"),
                    image = epInfo.str("movie_image"),
                    durationSecs = epInfo.str("duration_secs").toIntOrNull() ?: 0,
                    containerExt = ep.str("container_extension").ifEmpty { "mp4" },
                )
                seasons[season] = (seasons[season] ?: emptyList()) + episode
            }
        }
        when (val eps = root["episodes"]) {
            is JsonObject -> eps.forEach { (k, v) -> (v as? JsonArray)?.let { parseEpisodes(k.toIntOrNull(), it) } }
            is JsonArray -> eps.forEach { (it as? JsonArray)?.let { arr -> parseEpisodes(null, arr) } }
            else -> Unit
        }
        SeriesInfo(
            name = info.str("name"),
            plot = info.str("plot"),
            cast = info.str("cast"),
            genre = info.str("genre"),
            releaseDate = info.str("releaseDate").ifEmpty { info.str("release_date") },
            rating = info.str("rating"),
            cover = info.str("cover"),
            backdrop = info.firstString("backdrop_path"),
            seasons = seasons.mapValues { (_, list) -> list.sortedBy { it.number } },
        )
    }

    /** EPG corto de un canal (ahora + siguientes). */
    suspend fun shortEpg(c: XtreamCredentials, streamId: String, limit: Int = 6): List<XtreamEpgEntry> =
        epgListings(apiUrl(c, "get_short_epg", "stream_id" to streamId, "limit" to limit.toString()))

    /** Toda la programación disponible del canal, incluida la grabada (catch-up). */
    suspend fun archiveEpg(c: XtreamCredentials, streamId: String): List<XtreamEpgEntry> =
        epgListings(apiUrl(c, "get_simple_data_table", "stream_id" to streamId))

    private suspend fun epgListings(url: HttpUrl): List<XtreamEpgEntry> = withContext(Dispatchers.IO) {
        val root = getJson(url) as? JsonObject ?: return@withContext emptyList()
        val listings = root["epg_listings"] as? JsonArray ?: return@withContext emptyList()
        listings.mapNotNull { it as? JsonObject }.mapNotNull { e ->
            val start = e.str("start_timestamp").toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val stop = (e.str("stop_timestamp").ifEmpty { e.str("end_timestamp") }).toLongOrNull()?.times(1000) ?: return@mapNotNull null
            XtreamEpgEntry(
                title = decodeB64(e.str("title")),
                description = decodeB64(e.str("description")),
                startAt = start,
                stopAt = stop,
                hasArchive = e.str("has_archive") == "1",
            )
        }
    }

    // --- URLs de reproducción ---

    fun xmltvUrl(c: XtreamCredentials): String =
        "${c.baseUrl}/xmltv.php?username=${enc(c.username)}&password=${enc(c.password)}"

    fun liveUrl(c: XtreamCredentials, streamId: String, ext: String) =
        "${c.baseUrl}/live/${enc(c.username)}/${enc(c.password)}/$streamId.$ext"

    fun movieUrl(c: XtreamCredentials, streamId: String, ext: String) =
        "${c.baseUrl}/movie/${enc(c.username)}/${enc(c.password)}/$streamId.$ext"

    fun episodeUrl(c: XtreamCredentials, episodeId: String, ext: String) =
        "${c.baseUrl}/series/${enc(c.username)}/${enc(c.password)}/$episodeId.$ext"

    /**
     * URL de catch-up (timeshift). La hora de inicio va en la zona horaria del servidor
     * con el formato `yyyy-MM-dd:HH-mm`.
     */
    fun timeshiftUrl(c: XtreamCredentials, streamId: String, startAt: Long, durationMin: Int, serverTz: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
            timeZone = if (serverTz.isNotBlank()) TimeZone.getTimeZone(serverTz) else TimeZone.getDefault()
        }
        return "${c.baseUrl}/timeshift/${enc(c.username)}/${enc(c.password)}/$durationMin/${fmt.format(Date(startAt))}/$streamId.ts"
    }

    fun apiUrl(c: XtreamCredentials, action: String? = null, vararg params: Pair<String, String>): HttpUrl {
        val builder = "${c.baseUrl}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", c.username)
            .addQueryParameter("password", c.password)
        action?.let { builder.addQueryParameter("action", it) }
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun getJson(url: HttpUrl): JsonElement {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw IOException("Credenciales rechazadas (HTTP ${resp.code})")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return JsonObject(emptyMap())
            return json.parseToJsonElement(text)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        fun decodeB64(s: String): String {
            if (s.isEmpty()) return s
            return runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) }
                .getOrDefault(s)
                .takeIf { decoded -> decoded.none { it == '�' } } ?: s
        }
    }
}

// --- Helpers JSON tolerantes ---

internal fun JsonObject.str(key: String): String = when (val v = this[key]) {
    is JsonPrimitive -> v.contentOrNull.orEmpty()
    else -> ""
}

internal fun JsonObject.firstString(key: String): String = when (val v = this[key]) {
    is JsonPrimitive -> v.contentOrNull.orEmpty()
    is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
    else -> ""
}

/** Lee un objeto JSON quedándose solo con valores primitivos (y el primer string de cada array). */
internal fun JsonReader.readFlatObject(): Map<String, String> {
    val map = HashMap<String, String>(24)
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> map[name] = nextString()
            JsonToken.BOOLEAN -> map[name] = if (nextBoolean()) "1" else "0"
            JsonToken.NULL -> nextNull()
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                var first: String? = null
                while (hasNext()) {
                    if (first == null && peek() == JsonToken.STRING) first = nextString() else skipValue()
                }
                endArray()
                first?.let { map[name] = it }
            }
            else -> skipValue()
        }
    }
    endObject()
    return map
}
