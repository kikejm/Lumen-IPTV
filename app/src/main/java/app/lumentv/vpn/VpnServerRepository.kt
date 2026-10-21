package app.lumentv.vpn

import android.content.Context
import android.util.Log
import app.lumentv.core.crypto.SecretBox
import app.lumentv.data.db.LumenDatabase
import app.lumentv.data.db.VpnServerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class ImportResult(val imported: List<String>, val errors: List<String>)

/**
 * Catálogo de servidores VPN del usuario. Cada servidor es un `.conf` WireGuard de Proton
 * guardado cifrado (contiene la clave privada). Vías de importación:
 *  - assets empaquetados en build (`vpn-configs/` + `LUMEN_BUNDLE_VPN_CONFIGS=true`),
 *  - subida desde el móvil con el servidor de importación local,
 *  - descarga desde una URL.
 */
class VpnServerRepository(
    private val context: Context,
    private val db: LumenDatabase,
    private val secretBox: SecretBox,
    private val http: OkHttpClient,
) {
    private val dao = db.vpnServers()

    val servers: Flow<List<VpnServerEntity>> = dao.observeAll()

    suspend fun byId(id: Long) = dao.byId(id)
    suspend fun all() = dao.all()

    suspend fun configText(server: VpnServerEntity): String = secretBox.decrypt(server.configEnc)

    suspend fun import(fileName: String?, text: String, source: String): String {
        val parsed = ProtonConfigParser.parse(fileName, text)
        val existing = dao.byName(parsed.name)
        dao.upsert(
            VpnServerEntity(
                id = existing?.id ?: 0,
                name = parsed.name,
                countryCode = parsed.countryCode,
                endpoint = parsed.endpoint,
                isFree = parsed.isFree,
                configEnc = secretBox.encrypt(parsed.configText),
                source = source,
                favorite = existing?.favorite ?: false,
                lastConnectedAt = existing?.lastConnectedAt ?: 0,
            ),
        )
        return parsed.name
    }

    suspend fun importMany(files: List<Pair<String?, String>>, source: String): ImportResult {
        val ok = mutableListOf<String>()
        val errors = mutableListOf<String>()
        files.forEach { (name, text) ->
            runCatching { import(name, text, source) }
                .onSuccess(ok::add)
                .onFailure { errors += "${name ?: "config"}: ${it.message}" }
        }
        return ImportResult(ok, errors)
    }

    /** Importa los `.conf` empaquetados en `assets/vpn/` (idempotente). */
    suspend fun importBundled(): ImportResult = withContext(Dispatchers.IO) {
        val names = runCatching { context.assets.list("vpn")?.toList() }.getOrNull().orEmpty().filter { it.endsWith(".conf") }
        val files = names.mapNotNull { name ->
            runCatching { name to context.assets.open("vpn/$name").bufferedReader().readText() }
                .onFailure { Log.w("VpnServers", "No se pudo leer $name", it) }
                .getOrNull()
        }
        importMany(files, "bundled")
    }

    /** Descarga una configuración (o varias separadas por líneas `### fichero.conf`). */
    suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        val text = http.newCall(Request.Builder().url(url.trim()).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        importMany(splitBundle(url.substringAfterLast('/'), text), "url")
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun markConnected(id: Long) = dao.markConnected(id, System.currentTimeMillis())
    suspend fun delete(id: Long) = dao.delete(id)

    companion object {
        /** Permite pegar varios `.conf` en un único texto separados por `### nombre.conf`. */
        fun splitBundle(defaultName: String?, text: String): List<Pair<String?, String>> {
            if (!text.contains("###")) return listOf(defaultName to text)
            return text.split(Regex("""(?m)^###\s*""")).filter { it.isNotBlank() }.map { chunk ->
                val name = chunk.lineSequence().first().trim()
                name to chunk.substringAfter('\n')
            }
        }
    }
}
