package app.lumentv

import app.lumentv.data.m3u.M3uParser
import app.lumentv.data.xtream.XtreamCredentials
import app.lumentv.vpn.ProtonConfigParser
import app.lumentv.vpn.VpnServerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    // Clave de prueba generada solo para este test (no pertenece a ninguna cuenta).
    private val sampleConf = """
        [Interface]
        # Key for test-device
        # Bouncing = 1
        # NetShield = 0
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.2.0.2/32
        DNS = 10.2.0.1

        [Peer]
        # NL-FREE#12
        PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
        AllowedIPs = 0.0.0.0/0
        Endpoint = 185.107.56.1:51820
    """.trimIndent()

    @Test
    fun protonConfig_nameAndCountryFromPeerComment() {
        val parsed = ProtonConfigParser.parse("whatever.conf", sampleConf)
        assertEquals("NL-FREE#12", parsed.name)
        assertEquals("NL", parsed.countryCode)
        assertTrue(parsed.isFree)
        assertEquals("185.107.56.1:51820", parsed.endpoint)
    }

    @Test
    fun protonConfig_nameFromFileNameWhenNoComment() {
        val noComment = sampleConf.lines().filterNot { it.trim() == "# NL-FREE#12" }.joinToString("\n")
        val parsed = ProtonConfigParser.parse("wg-JP-FREE-3.conf", noComment)
        assertEquals("JP-FREE#3", parsed.name)
        assertEquals("JP", parsed.countryCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun protonConfig_rejectsGarbage() {
        ProtonConfigParser.parse("x.conf", "hola")
    }

    @Test
    fun countryOf_handlesSecureCoreAndStates() {
        assertEquals("NL", ProtonConfigParser.countryOf("CH-NL#1"))
        assertEquals("US", ProtonConfigParser.countryOf("US-NY#5"))
        assertEquals("CH", ProtonConfigParser.countryOf("CH#12"))
        assertEquals("PL", ProtonConfigParser.countryOf("PL-FREE#2"))
    }

    @Test
    fun withScope_addsIncludedApplicationsOnce() {
        val scoped = ProtonConfigParser.withScope(sampleConf + "\nIncludedApplications = other.app", "app.lumentv")
        assertEquals(1, scoped.lines().count { it.startsWith("IncludedApplications") })
        assertTrue(scoped.contains("IncludedApplications = app.lumentv"))
        val whole = ProtonConfigParser.withScope(scoped, null)
        assertFalse(whole.contains("IncludedApplications"))
    }

    @Test
    fun splitBundle_splitsMultipleConfigs() {
        val text = "### a.conf\n$sampleConf\n### b.conf\n$sampleConf"
        val parts = VpnServerRepository.splitBundle(null, text)
        assertEquals(listOf("a.conf", "b.conf"), parts.map { it.first })
        assertTrue(parts.all { it.second.contains("[Interface]") })
    }

    @Test
    fun m3u_parsesAttributesAndClassifies() = runTest {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="energy.es" tvg-logo="http://logo/e.png" group-title="ES: Entretenimiento",ES: Energy HD
            http://host:8080/live/u/p/123.ts
            #EXTINF:-1 tvg-id="" tvg-logo="" group-title="Cine, estrenos",Dune (2024)
            http://host:8080/movie/u/p/999.mkv
        """.trimIndent()
        val entries = mutableListOf<app.lumentv.data.m3u.M3uEntry>()
        M3uParser.parse(m3u.reader().buffered()) { entries += it }
        assertEquals(2, entries.size)
        assertEquals("ES: Energy HD", entries[0].name)
        assertEquals("energy.es", entries[0].tvgId)
        assertEquals("ES: Entretenimiento", entries[0].group)
        assertFalse(entries[0].isMovie)
        assertEquals("Dune (2024)", entries[1].name)
        assertEquals("Cine, estrenos", entries[1].group)
        assertTrue(entries[1].isMovie)
    }

    @Test
    fun xtreamServer_isNormalized() {
        assertEquals("http://host.com:8080", XtreamCredentials.normalizeServer("host.com:8080/"))
        assertEquals("https://host.com", XtreamCredentials.normalizeServer(" https://host.com/player_api.php "))
    }
}
