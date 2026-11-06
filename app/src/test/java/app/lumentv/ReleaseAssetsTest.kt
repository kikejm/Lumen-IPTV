package app.lumentv

import app.lumentv.update.ReleaseAssets
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseAssetsTest {

    @Test
    fun eachFlavor_readsItsOwnManifest() {
        // La TV sigue leyendo update.json: es lo que buscan las versiones ya instaladas en el Fire TV.
        assertEquals("update.json", ReleaseAssets.manifestName("tv"))
        assertEquals("update-mobile.json", ReleaseAssets.manifestName("mobile"))
    }

    @Test
    fun thisBuild_usesTheManifestOfItsFlavor() {
        val expected = if (BuildConfig.FLAVOR == "mobile") "update-mobile.json" else "update.json"
        assertEquals(expected, ReleaseAssets.manifestName(BuildConfig.FLAVOR))
    }

    @Test
    fun apkName_isTheLastSegmentOfTheManifestUrl() {
        val base = "https://github.com/kikejm/IPTV-Fire-Stick-TV/releases/download/v1.0.8"
        assertEquals("lumen-1.0.8.apk", ReleaseAssets.apkName("$base/lumen-1.0.8.apk"))
        assertEquals("lumen-mobile-1.0.8.apk", ReleaseAssets.apkName("$base/lumen-mobile-1.0.8.apk"))
        assertEquals("lumen-mobile-1.0.8.apk", ReleaseAssets.apkName("https://example.com/apk/lumen-mobile-1.0.8.apk?token=x#y"))
        assertEquals("", ReleaseAssets.apkName(""))
    }
}
