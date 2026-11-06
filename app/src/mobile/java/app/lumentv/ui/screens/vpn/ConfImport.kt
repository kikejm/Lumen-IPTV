package app.lumentv.ui.screens.vpn

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.lumentv.di.AppGraph
import app.lumentv.vpn.ImportResult
import app.lumentv.vpn.VpnServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Importación de `.conf` WireGuard en el móvil: desde el selector de ficheros del sistema (SAF)
 * o desde «Compartir»/«Abrir con» de otra app (gestor de archivos, navegador, correo).
 * En el móvil no existe el servidor de importación de la TV (`ImportServer`): no hace falta.
 */
object ConfImport {

    /** Un `.conf` ocupa ~1 KB; cualquier cosa mucho mayor no es una configuración. */
    private const val MAX_BYTES = 256 * 1024

    private val URL = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    private val _notice = MutableStateFlow<Notice?>(null)

    /** Resultado de la última importación llegada desde otra app; lo muestra la pantalla VPN. */
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    /** `fromOtherApp`: llegó por «Abrir con»/«Compartir»; solo entonces se lleva al usuario a la pantalla VPN. */
    data class Notice(val text: String, val error: Boolean, val fromOtherApp: Boolean = false)

    fun post(notice: Notice) {
        _notice.value = notice
    }

    fun consume() {
        _notice.value = null
    }

    /** Lee e importa los ficheros (y, si llega de «Compartir», el texto pegado) y devuelve el aviso para la UI. */
    suspend fun import(context: Context, graph: AppGraph, uris: List<Uri>, sharedText: String? = null): Notice {
        val files = mutableListOf<Pair<String?, String>>()
        val readErrors = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            uris.forEach { uri ->
                val name = displayName(context, uri)
                runCatching { files += name to readText(context, uri) }
                    .onFailure { readErrors += "${name ?: "fichero"}: ${it.message ?: "no se pudo leer"}" }
            }
        }
        val text = sharedText?.trim().orEmpty()
        var fromUrl: ImportResult? = null
        when {
            text.contains("[Interface]", ignoreCase = true) -> files += VpnServerRepository.splitBundle(null, text)
            // Un enlace compartido desde el navegador: se descarga como en «Desde URL».
            URL.matches(text) -> fromUrl = runCatching { graph.vpnServers.importFromUrl(text) }
                .getOrElse { ImportResult(emptyList(), listOf("${text.substringAfterLast('/')}: ${it.message ?: "no se pudo descargar"}")) }
        }
        if (files.isEmpty() && readErrors.isEmpty() && fromUrl == null) return Notice("No se ha recibido ninguna configuración WireGuard.", error = true)
        val result = graph.vpnServers.importMany(files, "file")
        return describe(
            ImportResult(
                result.imported + fromUrl?.imported.orEmpty(),
                readErrors + result.errors + fromUrl?.errors.orEmpty(),
            ),
        )
    }

    fun describe(r: ImportResult): Notice {
        val ok = r.imported.size
        return when {
            ok == 0 -> Notice("No se pudo importar: ${r.errors.firstOrNull() ?: "configuración no válida"}", error = true)
            r.errors.isEmpty() -> Notice(if (ok == 1) "Servidor ${r.imported.first()} añadido" else "$ok servidores añadidos", error = false)
            else -> Notice("$ok ${if (ok == 1) "servidor añadido" else "servidores añadidos"}; ${r.errors.size} con error (${r.errors.first()})", error = false)
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun readText(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("no se pudo abrir")
        return input.use { stream ->
            val bytes = stream.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw IOException("demasiado grande para ser un .conf")
            String(bytes, Charsets.UTF_8)
        }
    }

    /** `InputStream.readNBytes` es de Java 11 (API 33 en Android): versión propia. */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
