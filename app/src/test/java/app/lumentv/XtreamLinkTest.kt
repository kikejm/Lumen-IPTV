package app.lumentv

import app.lumentv.data.xtream.XtreamCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamLinkTest {

    @Test
    fun m3uGetPhpLink_becomesXtreamCredentials() {
        val c = XtreamCredentials.fromM3uUrl("http://panel.example.xyz:8080/get.php?username=ana&password=s3cr3t&type=m3u_plus&output=ts")!!
        assertEquals("http://panel.example.xyz:8080", c.baseUrl)
        assertEquals("ana", c.username)
        assertEquals("s3cr3t", c.password)
    }

    @Test
    fun httpsOnPort80_isFixedToHttp() {
        val c = XtreamCredentials.fromM3uUrl("https://panel.example.xyz:80/get.php?username=ana&password=x&type=m3u_plus")!!
        assertEquals("http://panel.example.xyz", c.baseUrl)
        assertEquals("http://panel.example.xyz:80", XtreamCredentials.normalizeServer("https://panel.example.xyz:80"))
        assertEquals("https://panel.example.xyz:8080", XtreamCredentials.normalizeServer("https://panel.example.xyz:8080"))
    }

    @Test
    fun plainM3uLinks_areNotXtream() {
        assertNull(XtreamCredentials.fromM3uUrl("https://example.com/lista.m3u"))
        assertNull(XtreamCredentials.fromM3uUrl("http://panel.example.xyz/get.php?username=ana"))
        assertNull(XtreamCredentials.fromM3uUrl("no es una url"))
    }
}
