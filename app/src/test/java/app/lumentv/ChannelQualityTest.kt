package app.lumentv

import app.lumentv.data.catalog.ChannelQuality
import app.lumentv.data.catalog.QualityFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelQualityTest {

    @Test
    fun resolution_fromTypicalProviderNames() {
        assertEquals(QualityFilter.FHD, ChannelQuality.resolutionOf("CANAL HISTORIA FHD"))
        assertEquals(QualityFilter.HD, ChannelQuality.resolutionOf("CANAL HISTORIA HD"))
        assertEquals(QualityFilter.SD, ChannelQuality.resolutionOf("CANAL HISTORIA SD"))
        assertEquals(QualityFilter.FHD, ChannelQuality.resolutionOf("|ES| DAZN 1 FHD HEVC"))
        assertEquals(QualityFilter.UHD, ChannelQuality.resolutionOf("ES: Movistar Liga 4K HDR"))
        assertEquals(QualityFilter.HD, ChannelQuality.resolutionOf("ES: La 1 [HD]"))
        assertEquals(QualityFilter.FHD, ChannelQuality.resolutionOf("Antena 3 1080p"))
        assertEquals(QualityFilter.HD, ChannelQuality.resolutionOf("cuatro hd+"))
    }

    @Test
    fun resolution_ignoresTagsInsideWords() {
        assertNull(ChannelQuality.resolutionOf("HDTV Channel"))
        assertNull(ChannelQuality.resolutionOf("SDTV Noticias"))
        assertNull(ChannelQuality.resolutionOf("Canal Historia"))
    }

    @Test
    fun hevcFilter_matchesCodecTag() {
        assertTrue(ChannelQuality.matches("|ES| DAZN 1 FHD HEVC", QualityFilter.HEVC))
        assertTrue(ChannelQuality.matches("CANAL HISTORIA FHD HECV", QualityFilter.HEVC))
        assertTrue(ChannelQuality.matches("Telecinco H.265", QualityFilter.HEVC))
        assertFalse(ChannelQuality.matches("CANAL HISTORIA FHD", QualityFilter.HEVC))
        assertTrue(ChannelQuality.matches("|ES| DAZN 1 FHD HEVC", QualityFilter.FHD))
    }

    @Test
    fun available_listsOnlyPresentQualities() {
        val names = listOf("A FHD", "A HD", "A FHD HEVC", "B")
        assertEquals(
            listOf(QualityFilter.ALL, QualityFilter.FHD, QualityFilter.HD, QualityFilter.HEVC),
            ChannelQuality.available(names),
        )
        assertEquals(listOf(QualityFilter.ALL), ChannelQuality.available(listOf("Canal sin etiqueta")))
    }
}
