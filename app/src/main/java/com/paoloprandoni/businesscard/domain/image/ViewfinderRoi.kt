package com.paoloprandoni.businesscard.domain.image

/** Rettangolo normalizzato (0..1) sull'immagine. */
data class NormalizedRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Regione abbastanza grande per essere usata come area di scansione. */
    fun isUsable(): Boolean = width >= 0.02f && height >= 0.02f

    companion object {
        val FULL = NormalizedRegion(0f, 0f, 1f, 1f)
    }
}

/**
 * Converte la cornice guida mostrata nella fotocamera (pixel della `PreviewView`, riempita in
 * modalità `FILL_CENTER`) nel rettangolo normalizzato dell'immagine catturata.
 *
 * Con `FILL_CENTER` l'immagine copre la view e viene centrata: una delle due dimensioni esce
 * dallo schermo. La conversione serve a far coincidere esattamente l'area di OCR con la cornice
 * che l'utente vede durante lo scatto.
 *
 * Tutta la matematica è Kotlin puro, così è verificabile con test JVM.
 */
object ViewfinderRoi {

    fun toImageRegion(
        viewLeft: Float,
        viewTop: Float,
        viewRight: Float,
        viewBottom: Float,
        viewWidth: Int,
        viewHeight: Int,
        imageAspect: Float,
    ): NormalizedRegion {
        if (viewWidth <= 0 || viewHeight <= 0 || imageAspect <= 0f) return NormalizedRegion.FULL

        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val displayWidth: Float
        val displayHeight: Float
        if (imageAspect >= viewAspect) {
            // immagine relativamente più larga: riempie l'altezza e sborda ai lati
            displayHeight = viewHeight.toFloat()
            displayWidth = displayHeight * imageAspect
        } else {
            // immagine relativamente più alta: riempie la larghezza e sborda sopra/sotto
            displayWidth = viewWidth.toFloat()
            displayHeight = displayWidth / imageAspect
        }

        val offsetX = (viewWidth - displayWidth) / 2f
        val offsetY = (viewHeight - displayHeight) / 2f

        val region = NormalizedRegion(
            left = ((viewLeft - offsetX) / displayWidth).coerceIn(0f, 1f),
            top = ((viewTop - offsetY) / displayHeight).coerceIn(0f, 1f),
            right = ((viewRight - offsetX) / displayWidth).coerceIn(0f, 1f),
            bottom = ((viewBottom - offsetY) / displayHeight).coerceIn(0f, 1f),
        )
        return if (region.isUsable()) region else NormalizedRegion.FULL
    }
}
