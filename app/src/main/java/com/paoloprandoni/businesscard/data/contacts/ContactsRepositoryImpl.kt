package com.paoloprandoni.businesscard.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.paoloprandoni.businesscard.domain.model.BusinessCardData
import com.paoloprandoni.businesscard.domain.model.ContactAccount
import com.paoloprandoni.businesscard.domain.parser.AddressParser
import com.paoloprandoni.businesscard.domain.repository.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scrittura del contatto tramite un'unica `applyBatch` atomica:
 * 1. `RawContacts` (account scelto, oppure null/null per la memoria locale);
 * 2. `Data` con back-reference all'indice 0 per nome, azienda/ruolo, telefoni, email,
 *    sito, indirizzo strutturato, note e foto.
 */
class ContactsRepositoryImpl(
    private val resolver: ContentResolver,
) : ContactsRepository {

    override suspend fun saveContact(
        card: BusinessCardData,
        photo: ByteArray?,
        account: ContactAccount,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching { insertContact(card, photo, account) }
    }

    private fun insertContact(
        card: BusinessCardData,
        photo: ByteArray?,
        account: ContactAccount,
    ): Uri {
        val operations = ArrayList<ContentProviderOperation>()

        val rawContact = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        if (account.isLocal) {
            rawContact
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        } else {
            rawContact
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.accountName)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.accountType)
            if (!account.dataSet.isNullOrBlank()) {
                rawContact.withValue(ContactsContract.RawContacts.DATA_SET, account.dataSet)
            }
        }
        operations += rawContact.build()

        // --- Nome ---
        if (card.firstName.isNotBlank() || card.lastName.isNotBlank() || card.namePrefix.isNotBlank()) {
            operations += newData(StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.PREFIX, card.namePrefix.ifBlank { null })
                .withValue(StructuredName.GIVEN_NAME, card.firstName.ifBlank { null })
                .withValue(StructuredName.FAMILY_NAME, card.lastName.ifBlank { null })
                .build()
        }

        // --- Azienda e ruolo ---
        if (card.company.isNotBlank() || card.jobTitle.isNotBlank()) {
            operations += newData(Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, card.company.ifBlank { null })
                .withValue(Organization.TITLE, card.jobTitle.ifBlank { null })
                .withValue(Organization.TYPE, Organization.TYPE_WORK)
                .build()
        }

        // --- Telefoni ---
        addPhone(operations, card.mobilePhone, Phone.TYPE_MOBILE)
        addPhone(operations, card.workPhone, Phone.TYPE_WORK)
        addPhone(operations, card.faxPhone, Phone.TYPE_FAX_WORK)

        // --- Email (una o più) ---
        card.emails
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { address ->
                operations += newData(Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.ADDRESS, address)
                    .withValue(Email.TYPE, Email.TYPE_WORK)
                    .build()
            }

        // --- Sito web ---
        if (card.website.isNotBlank()) {
            operations += newData(Website.CONTENT_ITEM_TYPE)
                .withValue(Website.URL, normalizeUrl(card.website))
                .withValue(Website.TYPE, Website.TYPE_HOMEPAGE)
                .build()
        }

        // --- Indirizzo strutturato ---
        if (card.address.isNotBlank()) {
            val parts = AddressParser.parse(card.address)
            val builder = newData(StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(StructuredPostal.FORMATTED_ADDRESS, card.address.trim())
                .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_WORK)
            if (parts.street.isNotBlank()) builder.withValue(StructuredPostal.STREET, parts.street)
            if (parts.city.isNotBlank()) builder.withValue(StructuredPostal.CITY, parts.city)
            if (parts.postalCode.isNotBlank()) builder.withValue(StructuredPostal.POSTCODE, parts.postalCode)
            if (parts.region.isNotBlank()) builder.withValue(StructuredPostal.REGION, parts.region)
            if (parts.country.isNotBlank()) builder.withValue(StructuredPostal.COUNTRY, parts.country)
            operations += builder.build()
        }

        // --- Note (incluse le righe non classificate e il testo OCR grezzo) ---
        if (card.notes.isNotBlank()) {
            operations += newData(Note.CONTENT_ITEM_TYPE)
                .withValue(Note.NOTE, card.notes.trim().take(MAX_NOTE_LENGTH))
                .build()
        }

        // --- Foto / logo ---
        if (photo != null && photo.isNotEmpty()) {
            operations += newData(Photo.CONTENT_ITEM_TYPE)
                .withValue(Photo.PHOTO, photo)
                .build()
        }

        val results = resolver.applyBatch(ContactsContract.AUTHORITY, operations)
        val rawUri = results.firstOrNull()?.uri
            ?: error("Inserimento del contatto non riuscito")
        val rawId = ContentUris.parseId(rawUri)

        val contactId = resolver.query(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId),
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L

        return if (contactId > 0L) {
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        } else {
            rawUri
        }
    }

    private fun newData(mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)

    private fun addPhone(
        operations: MutableList<ContentProviderOperation>,
        number: String,
        type: Int,
    ) {
        if (number.isBlank()) return
        operations += newData(Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, number.trim())
            .withValue(Phone.TYPE, type)
            .build()
    }

    private fun normalizeUrl(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private companion object {
        const val MAX_NOTE_LENGTH = 80_000
    }
}
