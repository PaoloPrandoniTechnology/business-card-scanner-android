package com.paoloprandoni.businesscard.data.session

import android.graphics.RectF
import com.paoloprandoni.businesscard.domain.model.BusinessCardData
import com.paoloprandoni.businesscard.domain.repository.ScanSession
import com.paoloprandoni.businesscard.domain.repository.ScanSessionRepository

/** Sessione di scansione in memoria (singleton, vive quanto il processo). */
class InMemoryScanSessionRepository : ScanSessionRepository {

    private var session: ScanSession? = null

    override fun start(imagePath: String, card: BusinessCardData, logoHint: RectF?): ScanSession {
        val newSession = ScanSession(imagePath = imagePath, card = card, logoHint = logoHint)
        session = newSession
        return newSession
    }

    override fun current(): ScanSession? = session

    override fun updateCard(card: BusinessCardData) {
        session = session?.copy(card = card)
    }

    override fun setLogo(logoPath: String?) {
        session = session?.copy(logoPath = logoPath)
    }

    override fun clear() {
        session = null
    }
}
