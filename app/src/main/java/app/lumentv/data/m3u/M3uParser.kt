package app.lumentv.data.m3u

import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String,
    val logo: String,
    val group: String,
    val catchupDays: Int,
) {
    /** Clasificación heurística: los paneles Xtream exportan VOD bajo `/movie/` y series bajo `/series/`. */
    val isMovie: Boolean get() = url.contains("/movie/") || VIDEO_EXT.any { url.substringBefore('?').endsWith(it, ignoreCase = true) } && !url.contains("/live/")
    val isSeries: Boolean get() = url.contains("/series/")

    private companion object {
        val VIDEO_EXT = listOf(".mp4", ".mkv", ".avi", ".mov")
    }
}

/** Parser en streaming de listas M3U/M3U8 extendidas (`#EXTINF`). */
object M3uParser {

    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")

    suspend fun parse(reader: BufferedReader, onEntry: suspend (M3uEntry) -> Unit) {
        var pendingInfo: String? = null
        var pendingGroup: String? = null
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line
                line.startsWith("#EXTGRP:", ignoreCase = true) -> pendingGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    val info = pendingInfo
                    if (info != null) onEntry(toEntry(info, line, pendingGroup))
                    pendingInfo = null
                    pendingGroup = null
                }
            }
        }
    }

    internal fun toEntry(info: String, url: String, extGroup: String?): M3uEntry {
        val attrs = ATTR.findAll(info).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        // El nombre visible va tras la última coma que no esté dentro de comillas.
        val name = info.substring(lastUnquotedComma(info) + 1).trim()
            .ifEmpty { attrs["tvg-name"].orEmpty() }
        return M3uEntry(
            name = name,
            url = url,
            tvgId = attrs["tvg-id"].orEmpty(),
            logo = attrs["tvg-logo"].orEmpty(),
            group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGroup ?: "Sin categoría",
            catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: attrs["tvg-rec"]?.toIntOrNull() ?: 0,
        )
    }

    private fun lastUnquotedComma(s: String): Int {
        var inQuotes = false
        var last = -1
        s.forEachIndexed { i, c ->
            if (c == '"') inQuotes = !inQuotes
            if (c == ',' && !inQuotes) last = i
        }
        return last
    }
}
