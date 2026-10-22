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
    fun quality_fromTypicalProviderNames() {
        assertEquals(QualityFilter.FHD, ChannelQuality.of("CANAL HISTORIA FHD"))
        assertEquals(QualityFilter.FHD_HEVC, ChannelQuality.of("CANAL HISTORIA FHD HEVC"))
        assertEquals(QualityFilter.FHD_HEVC, ChannelQuality.of("CANAL HISTORIA FHD HECV"))
        assertEquals(QualityFilter.HD, ChannelQuality.of("CANAL HISTORIA HD"))
        assertEquals(QualityFilter.SD, ChannelQuality.of("CANAL HISTORIA SD"))
        assertEquals(QualityFilter.FHD_HEVC, ChannelQuality.of("|ES| DAZN 1 FHD H.265"))
        assertEquals(QualityFilter.HD, ChannelQuality.of("ES: La 1 [HD]"))
        assertEquals(QualityFilter.HD, ChannelQuality.of("cuatro hd+"))
    }

    @Test
    fun quality_ignoresTagsInsideWordsAndOtherResolutions() {
        assertNull(ChannelQuality.of("HDTV Channel"))
        assertNull(ChannelQuality.of("SDTV Noticias"))
        assertNull(ChannelQuality.of("Canal Historia"))
        assertNull(ChannelQuality.of("DEP: Tenis 4K"))
    }

    @Test
    fun filters_areExclusive() {
        assertTrue(ChannelQuality.matches("CANAL HISTORIA FHD", QualityFilter.FHD))
        assertFalse(ChannelQuality.matches("CANAL HISTORIA FHD HEVC", QualityFilter.FHD))
        assertTrue(ChannelQuality.matches("CANAL HISTORIA FHD HEVC", QualityFilter.FHD_HEVC))
        assertFalse(ChannelQuality.matches("CANAL HISTORIA FHD", QualityFilter.HD))
        assertTrue(ChannelQuality.matches("Canal sin etiqueta", QualityFilter.ALL))
        assertFalse(ChannelQuality.matches("Canal sin etiqueta", QualityFilter.SD))
    }

    @Test
    fun available_listsOnlyPresentQualities() {
        val names = listOf("A FHD", "A HD", "A FHD HEVC", "B")
        assertEquals(
            listOf(QualityFilter.ALL, QualityFilter.FHD, QualityFilter.FHD_HEVC, QualityFilter.HD),
            ChannelQuality.available(names),
        )
        assertEquals(listOf(QualityFilter.ALL), ChannelQuality.available(listOf("Canal sin etiqueta")))
    }
}
