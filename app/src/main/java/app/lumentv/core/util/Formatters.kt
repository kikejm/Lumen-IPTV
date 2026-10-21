package app.lumentv.core.util

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale("es", "ES")

/** Formatea horas según la preferencia 12/24 h del usuario. */
class TimeFormatter(use24h: Boolean, private val offsetMillis: Long = 0) {
    private val zone = ZoneId.systemDefault()
    private val hour = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", SPANISH)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)
    private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)

    fun time(epochMillis: Long): String = hour.format(Instant.ofEpochMilli(epochMillis + offsetMillis).atZone(zone))
    fun range(start: Long, end: Long): String = "${time(start)} – ${time(end)}"
    fun day(epochMillis: Long): String = day.format(Instant.ofEpochMilli(epochMillis + offsetMillis).atZone(zone))
    fun date(epochMillis: Long): String = longDate.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMinutes(minutes: Int): String = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(SPANISH, bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(SPANISH, bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(SPANISH, bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun relativeAge(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0) return "nunca"
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> "hace $minutes min"
        minutes < 48 * 60 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (60 * 24)} días"
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Convierte un código ISO de país (NL) en su bandera emoji (🇳🇱). */
fun flagEmoji(countryCode: String): String {
    val cc = countryCode.uppercase(Locale.ROOT)
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
    val first = Character.codePointAt(cc, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(cc, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

/** Nombre del país en español a partir del código ISO. */
fun countryName(countryCode: String): String {
    val name = Locale("", countryCode.uppercase(Locale.ROOT)).getDisplayCountry(SPANISH)
    return name.ifBlank { countryCode.uppercase(Locale.ROOT) }.replaceFirstChar { it.titlecase(SPANISH) }
}
