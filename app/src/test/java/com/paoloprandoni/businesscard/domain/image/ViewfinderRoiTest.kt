package com.paoloprandoni.businesscard.domain.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewfinderRoiTest {

    @Test
    fun `con stesso aspect ratio la conversione e proporzionale`() {
        val region = ViewfinderRoi.toImageRegion(
            viewLeft = 100f,
            viewTop = 200f,
            viewRight = 500f,
            viewBottom = 600f,
            viewWidth = 1000,
            viewHeight = 1000,
            imageAspect = 1f,
        )
        assertEquals(0.1f, region.left, 0.0001f)
        assertEquals(0.2f, region.top, 0.0001f)
        assertEquals(0.5f, region.right, 0.0001f)
        assertEquals(0.6f, region.bottom, 0.0001f)
    }

    @Test
    fun `immagine larga in view verticale viene tagliata ai lati`() {
        // view 1000x2000 (aspect 0.5), immagine 4:3 (1.333): riempie l'altezza e sborda di lato
        val region = ViewfinderRoi.toImageRegion(
            viewLeft = 0f,
            viewTop = 0f,
            viewRight = 1000f,
            viewBottom = 2000f,
            viewWidth = 1000,
            viewHeight = 2000,
            imageAspect = 4f / 3f,
        )
        val displayWidth = 2000f * (4f / 3f)
        val offsetX = (1000f - displayWidth) / 2f
        assertEquals((0f - offsetX) / displayWidth, region.left, 0.0001f)
        assertEquals((1000f - offsetX) / displayWidth, region.right, 0.0001f)
        assertEquals(0f, region.top, 0.0001f)
        assertEquals(1f, region.bottom, 0.0001f)
        assertTrue(region.left > 0f)
        assertTrue(region.right < 1f)
    }

    @Test
    fun `immagine alta in view orizzontale sborda sopra e sotto`() {
        val region = ViewfinderRoi.toImageRegion(
            viewLeft = 0f,
            viewTop = 0f,
            viewRight = 2000f,
            viewBottom = 1000f,
            viewWidth = 2000,
            viewHeight = 1000,
            imageAspect = 0.5f,
        )
        assertEquals(0f, region.left, 0.0001f)
        assertEquals(1f, region.right, 0.0001f)
        assertTrue(region.top > 0f)
        assertTrue(region.bottom < 1f)
    }

    @Test
    fun `i valori vengono limitati all'immagine`() {
        val region = ViewfinderRoi.toImageRegion(
            viewLeft = -200f,
            viewTop = -200f,
            viewRight = 400f,
            viewBottom = 400f,
            viewWidth = 1000,
            viewHeight = 1000,
            imageAspect = 1f,
        )
        assertEquals(0f, region.left, 0.0001f)
        assertEquals(0f, region.top, 0.0001f)
    }

    @Test
    fun `cornice degenere restituisce l'intera immagine`() {
        val region = ViewfinderRoi.toImageRegion(
            viewLeft = 10f,
            viewTop = 10f,
            viewRight = 12f,
            viewBottom = 12f,
            viewWidth = 1000,
            viewHeight = 1000,
            imageAspect = 1f,
        )
        assertEquals(NormalizedRegion.FULL, region)
    }

    @Test
    fun `aspect non valido restituisce l'intera immagine`() {
        val region = ViewfinderRoi.toImageRegion(0f, 0f, 100f, 100f, 0, 0, 0f)
        assertEquals(NormalizedRegion.FULL, region)
    }
}
