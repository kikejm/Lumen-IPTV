package app.lumentv.core.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Descarga [url] a un fichero temporal en [dir] y ejecuta [block] con él; el fichero se borra al terminar.
 *
 * Los ficheros grandes (M3U, XMLTV, listas Xtream) se descargan primero y se parsean después:
 * si se parsean directamente del socket mientras se escribe en Room, la lectura es tan lenta que
 * muchos servidores cortan la conexión a medias ("unexpected end of stream").
 */
inline fun <T> OkHttpClient.withDownloadedFile(url: String, dir: File, prefix: String, block: (File) -> T): T {
    val tmp = File.createTempFile(prefix, ".tmp", dir)
    try {
        newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Respuesta vacía")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
        }
        return block(tmp)
    } finally {
        tmp.delete()
    }
}
