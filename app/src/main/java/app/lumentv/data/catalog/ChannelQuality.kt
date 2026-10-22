package app.lumentv.data.catalog

/** Filtro de calidad de TV en directo. La resolución y el códec se deducen del nombre del canal. */
enum class QualityFilter(val label: String) {
    ALL("Todas"), UHD("4K"), FHD("FHD"), HD("HD"), SD("SD"), HEVC("HEVC"),
}

/**
 * Los proveedores repiten cada canal por calidad ("CANAL HISTORIA FHD", "… HD", "… SD",
 * "|ES| DAZN 1 FHD HEVC"). Las etiquetas cuentan solo como palabra suelta: "FHD" no es "HD"
 * y "HDR" no es nada.
 */
object ChannelQuality {
    private fun tag(alternatives: String) =
        Regex("(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private val uhd = tag("4K|UHD|2160P?")
    private val fhd = tag("FHD|FULL ?HD|1080[PI]?")
    private val hd = tag("HD|720P?")
    private val sd = tag("SD|576[PI]?|480[PI]?")
    private val hevc = tag("HEVC|HECV|H\\.?265|X265")

    /** La resolución más alta que aparezca en el nombre, o null si no lleva etiqueta. */
    fun resolutionOf(name: String): QualityFilter? = when {
        uhd.containsMatchIn(name) -> QualityFilter.UHD
        fhd.containsMatchIn(name) -> QualityFilter.FHD
        hd.containsMatchIn(name) -> QualityFilter.HD
        sd.containsMatchIn(name) -> QualityFilter.SD
        else -> null
    }

    fun matches(name: String, filter: QualityFilter): Boolean = when (filter) {
        QualityFilter.ALL -> true
        QualityFilter.HEVC -> hevc.containsMatchIn(name)
        else -> resolutionOf(name) == filter
    }

    /** Filtros con al menos un canal en `names`, en el orden del enum. Siempre incluye [QualityFilter.ALL]. */
    fun available(names: List<String>): List<QualityFilter> {
        val found = HashSet<QualityFilter>()
        for (n in names) {
            resolutionOf(n)?.let(found::add)
            if (hevc.containsMatchIn(n)) found += QualityFilter.HEVC
        }
        return QualityFilter.entries.filter { it == QualityFilter.ALL || it in found }
    }
}
