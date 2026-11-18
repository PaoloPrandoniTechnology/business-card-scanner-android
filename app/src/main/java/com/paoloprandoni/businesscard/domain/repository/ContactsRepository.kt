package com.paoloprandoni.businesscard.domain.repository

import android.net.Uri
import com.paoloprandoni.businesscard.domain.model.BusinessCardData
import com.paoloprandoni.businesscard.domain.model.ContactAccount

/** Scrittura del contatto nella rubrica Android, sull'account scelto. */
interface ContactsRepository {
    /**
     * Salva [card] nella rubrica.
     *
     * @param photo JPEG già compresso (max 512×512) oppure null.
     * @return Uri del contatto creato (aggregato) o errore.
     */
    suspend fun saveContact(
        card: BusinessCardData,
        photo: ByteArray?,
        account: ContactAccount,
    ): Result<Uri>
}
