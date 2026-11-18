package com.paoloprandoni.businesscard.domain.model

/**
 * Dati estratti (e poi revisionati dall'utente) da un biglietto da visita.
 *
 * Tutti i campi sono stringhe editabili: la schermata di revisione lavora direttamente su questo
 * modello e [com.paoloprandoni.businesscard.data.contacts.ContactsRepositoryImpl] lo traduce in
 * righe di [android.provider.ContactsContract.Data].
 */
data class BusinessCardData(
    val namePrefix: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val mobilePhone: String = "",
    val workPhone: String = "",
    val faxPhone: String = "",
    val emails: List<String> = emptyList(),
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val rawText: String = "",
) {
    /** Nome completo comprensivo di titolo (es. "Ing. Mario Rossi"). */
    val fullName: String
        get() = listOf(namePrefix, firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    val isEmpty: Boolean
        get() = fullName.isBlank() &&
            company.isBlank() &&
            emails.isEmpty() &&
            mobilePhone.isBlank() &&
            workPhone.isBlank()
}

/**
 * Account/rubrica di destinazione per il salvataggio del contatto.
 *
 * [accountName] e [accountType] nulli indicano la memoria locale del dispositivo
 * (nessun account sincronizzato).
 */
data class ContactAccount(
    val accountName: String?,
    val accountType: String?,
    val dataSet: String? = null,
) {
    val isLocal: Boolean get() = accountType.isNullOrBlank()

    /** Chiave stabile usata per la selezione nella UI. */
    val key: String get() = listOf(accountType.orEmpty(), accountName.orEmpty()).joinToString("|")
}
