package app.lumentv.data.epg

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

data class XmltvProgram(val channel: String, val start: Long, val stop: Long, val title: String, val description: String)

/**
 * Parser XMLTV en streaming (XmlPullParser). Soporta ficheros `.gz`, filtra por canal y
 * ventana temporal para no llenar la base de datos del Fire Stick con días de EPG inútiles.
 */
object XmltvParser {

    suspend fun parse(
        input: InputStream,
        acceptChannel: (String) -> Boolean,
        windowStart: Long,
        windowEnd: Long,
        onProgram: suspend (XmltvProgram) -> Unit,
    ) {
        val stream = maybeGunzip(input)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val fmtWithZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val fmtNoZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        fun parseTime(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val t = raw.trim()
            return runCatching { fmtWithZone.parse(t)?.time }.getOrNull()
                ?: runCatching { fmtNoZone.parse(t.take(14))?.time }.getOrNull()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel").orEmpty().lowercase(Locale.ROOT)
                val start = parseTime(parser.getAttributeValue(null, "start"))
                val stop = parseTime(parser.getAttributeValue(null, "stop"))
                val wanted = channel.isNotEmpty() && start != null && stop != null &&
                    stop > windowStart && start < windowEnd && acceptChannel(channel)
                if (!wanted) {
                    skipElement(parser)
                } else {
                    var title = ""
                    var desc = ""
                    val depth = parser.depth
                    while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> if (title.isEmpty()) title = parser.nextText().trim() else skipElement(parser)
                                "desc" -> if (desc.isEmpty()) desc = parser.nextText().trim() else skipElement(parser)
                                else -> skipElement(parser)
                            }
                        }
                        if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                    }
                    onProgram(XmltvProgram(channel, start!!, stop!!, title, desc))
                }
            }
            event = parser.next()
        }
    }

    private fun skipElement(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun maybeGunzip(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, 64 * 1024)) else buffered
    }
}
