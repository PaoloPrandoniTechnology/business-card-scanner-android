package com.paoloprandoni.businesscard.domain.repository

import android.graphics.RectF
import com.paoloprandoni.businesscard.domain.model.BusinessCardData

/** Sessione di scansione corrente (immagine, dati parsati, logo ritagliato). */
data class ScanSession(
    val imagePath: String,
    val card: BusinessCardData,
    val logoPath: String? = null,
    /** Box normalizzato suggerito per il primo ritaglio del logo (area senza testo OCR). */
    val logoHint: RectF? = null,
)

/**
 * Ponte in memoria tra la schermata di scansione e quella di revisione.
 * I bitmap non transitano mai per questo repository: solo percorsi di file in cache.
 */
interface ScanSessionRepository {
    fun start(imagePath: String, card: BusinessCardData, logoHint: RectF? = null): ScanSession

    fun current(): ScanSession?

    fun updateCard(card: BusinessCardData)

    fun setLogo(logoPath: String?)

    fun clear()
}
