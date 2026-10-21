package app.lumentv.vpn

import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.util.Locale

/** Metadatos extraídos de un `.conf` WireGuard generado en account.protonvpn.com. */
data class ParsedServer(
    val name: String,
    val countryCode: String,
    val endpoint: String,
    val isFree: Boolean,
    val configText: String,
)

/**
 * Valida un fichero WireGuard y deduce el servidor Proton al que apunta.
 *
 * Proton escribe el nombre del servidor como comentario bajo `[Peer]` (p. ej. `# NL-FREE#12`)
 * y nombra los ficheros como `wg-NL-FREE-12.conf`. Se usan ambas pistas; si no hay ninguna,
 * se usa el host del endpoint.
 */
object ProtonConfigParser {

    private val SERVER_COMMENT = Regex("""^#\s*([A-Z]{2}(?:-[A-Z]{2,5})?(?:-FREE)?#\d+)""", RegexOption.IGNORE_CASE)
    private val FILE_NAME = Regex("""([A-Z]{2}(?:-[A-Z]{2,5})?(?:-FREE)?)[-#_](\d+)""", RegexOption.IGNORE_CASE)
    /** Países de entrada de Secure Core: en `CH-NL#1` el país de salida es NL. */
    private val SECURE_CORE_ENTRIES = setOf("CH", "IS", "SE")

    fun parse(fileName: String?, rawText: String): ParsedServer {
        val text = rawText.replace("\r\n", "\n").trim() + "\n"
        val config = try {
            Config.parse(ByteArrayInputStream(text.toByteArray()))
        } catch (e: Exception) {
            throw IllegalArgumentException("No es una configuración WireGuard válida: ${e.message ?: e.javaClass.simpleName}")
        }
        val peer = config.peers.firstOrNull() ?: throw IllegalArgumentException("La configuración no tiene [Peer]")
        val endpoint = peer.endpoint.orElse(null)?.let { "${it.host}:${it.port}" }.orEmpty()

        val name = text.lineSequence().map { it.trim() }.firstNotNullOfOrNull { SERVER_COMMENT.find(it)?.groupValues?.get(1) }
            ?: fileName?.let { raw ->
                val base = raw.substringAfterLast('/').replace(Regex("^wg[-_]", RegexOption.IGNORE_CASE), "")
                FILE_NAME.find(base)?.let { m -> "${m.groupValues[1]}#${m.groupValues[2]}" }
            }
            ?: endpoint.substringBefore(':').ifEmpty { fileName ?: "Servidor" }
        val normalized = name.uppercase(Locale.ROOT)

        return ParsedServer(
            name = normalized,
            countryCode = countryOf(normalized),
            endpoint = endpoint,
            isFree = normalized.contains("FREE"),
            configText = text,
        )
    }

    fun countryOf(serverName: String): String {
        val parts = serverName.substringBefore('#').split('-')
        val first = parts.getOrNull(0)?.takeIf { it.length == 2 && it.all(Char::isLetter) } ?: return "UN"
        val second = parts.getOrNull(1)
        return if (first in SECURE_CORE_ENTRIES && second != null && second.length == 2 && second.all(Char::isLetter)) second else first
    }

    /**
     * Aplica el ámbito del túnel. En modo "solo Lumen" se añade `IncludedApplications`
     * para que únicamente el tráfico de esta app pase por la VPN (el resto del Fire TV no se ve afectado).
     */
    fun withScope(configText: String, appOnlyPackage: String?): String {
        val lines = configText.lines().filterNot {
            val key = it.substringBefore('=').trim().lowercase(Locale.ROOT)
            key == "includedapplications" || key == "excludedapplications"
        }.toMutableList()
        if (appOnlyPackage != null) {
            val idx = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
            if (idx >= 0) lines.add(idx + 1, "IncludedApplications = $appOnlyPackage")
        }
        return lines.joinToString("\n")
    }
}
