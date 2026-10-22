package app.lumentv.data.catalog

/**
 * Filtro de calidad de TV en directo: FHD, FHD HEVC, HD y SD. [ALL] es no filtrar.
 * La calidad se deduce del nombre del canal.
 */
enum class QualityFilter(val label: String) {
    ALL("Todas"), FHD("FHD"), FHD_HEVC("FHD HEVC"), HD("HD"), SD("SD"),
}

/**
 * Los proveedores repiten cada canal por calidad ("CANAL HISTORIA FHD", "… FHD HEVC", "… HD",
 * "… SD"). Las etiquetas cuentan solo como palabra suelta: "FHD" no es "HD" y "HDTV" no es nada.
 * Cada canal cae en una sola calidad: un "FHD HEVC" no aparece en "FHD".
 */
object ChannelQuality {
    private fun tag(alternatives: String) =
        Regex("(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private val fhd = tag("FHD|FULL ?HD|1080[PI]?")
    private val hd = tag("HD|720P?")
    private val sd = tag("SD|576[PI]?|480[PI]?")
    private val hevc = tag("HEVC|HECV|H\\.?265|X265")

    /** Calidad del canal, o null si el nombre no lleva ninguna de las cuatro etiquetas. */
    fun of(name: String): QualityFilter? = when {
        fhd.containsMatchIn(name) -> if (hevc.containsMatchIn(name)) QualityFilter.FHD_HEVC else QualityFilter.FHD
        hd.containsMatchIn(name) -> QualityFilter.HD
        sd.containsMatchIn(name) -> QualityFilter.SD
        else -> null
    }

    fun matches(name: String, filter: QualityFilter): Boolean = filter == QualityFilter.ALL || of(name) == filter

    /** Filtros con al menos un canal en `names`, en el orden del enum. Siempre incluye [QualityFilter.ALL]. */
    fun available(names: List<String>): List<QualityFilter> {
        val found = names.mapNotNullTo(HashSet()) { of(it) }
        return QualityFilter.entries.filter { it == QualityFilter.ALL || it in found }
    }
}
