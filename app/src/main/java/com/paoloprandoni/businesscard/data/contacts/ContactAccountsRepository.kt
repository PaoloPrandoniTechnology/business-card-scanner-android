package com.paoloprandoni.businesscard.data.contacts

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.paoloprandoni.businesscard.domain.model.ContactAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Elenca gli account/rubriche di destinazione disponibili sul dispositivo.
 *
 * Fonti:
 * 1. `AccountManager` — account configurati che tipicamente supportano i contatti
 *    (Google, Exchange/Microsoft, Outlook, CardDAV, iCloud...). Richiede `GET_ACCOUNTS`.
 * 2. `ContactsContract.RawContacts` — account che hanno già contatti sincronizzati
 *    (fonte autoritativa, richiede solo `READ_CONTACTS`).
 *
 * La voce "Telefono (solo locale)" è sempre presente e corrisponde a `ACCOUNT_NAME/TYPE = null`.
 */
class ContactAccountsRepository(
    private val context: Context,
    private val resolver: ContentResolver,
) {

    suspend fun loadAccounts(): List<ContactAccount> = withContext(Dispatchers.IO) {
        val accountManagerAccounts = loadAccountsFromAccountManager()
        val rawContactAccounts = loadAccountsFromRawContacts()
        val merged = LinkedHashMap<String, ContactAccount>()

        (accountManagerAccounts + rawContactAccounts).forEach { account ->
            if (account.isLocal) return@forEach
            merged.putIfAbsent(account.key, account)
        }

        val result = merged.values.sortedWith(
            compareByDescending<ContactAccount> { it.accountType?.contains("google", ignoreCase = true) == true }
                .thenBy { it.accountType.orEmpty() }
                .thenBy { it.accountName.orEmpty() },
        )
        result + ContactAccount(accountName = null, accountType = null)
    }

    private fun loadAccountsFromAccountManager(): List<ContactAccount> = runCatching {
        val manager = AccountManager.get(context)
        manager.accounts
            .filter { account -> isContactCapable(account.type.orEmpty()) }
            .map { account -> ContactAccount(accountName = account.name, accountType = account.type) }
    }.getOrDefault(emptyList())

    private fun loadAccountsFromRawContacts(): List<ContactAccount> = runCatching {
        val projection = arrayOf(
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DATA_SET,
        )
        val accounts = mutableListOf<ContactAccount>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            projection,
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null,
        )?.use { cursor: Cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            val typeIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val dataSetIndex = cursor.getColumnIndex(ContactsContract.RawContacts.DATA_SET)
            while (cursor.moveToNext()) {
                val type = typeIndex.readString(cursor) ?: continue
                if (type.isBlank()) continue
                val name = nameIndex.readString(cursor)
                val dataSet = dataSetIndex.readString(cursor)
                accounts += ContactAccount(accountName = name, accountType = type, dataSet = dataSet)
            }
        }
        accounts.distinctBy { it.key }
    }.getOrDefault(emptyList())

    private fun isContactCapable(accountType: String): Boolean {
        val type = accountType.lowercase()
        if (type.isBlank()) return false
        return CONTACT_ACCOUNT_HINTS.any { hint -> type.contains(hint) } ||
            type.startsWith("com.google")
    }

    private fun Int.readString(cursor: Cursor): String? =
        if (this >= 0 && !cursor.isNull(this)) cursor.getString(this) else null

    private companion object {
        val CONTACT_ACCOUNT_HINTS = listOf(
            "google",
            "exchange",
            "microsoft",
            "outlook",
            "office",
            "yahoo",
            "icloud",
            "carddav",
            "davdroid",
            "dav",
            "nextcloud",
            "owncloud",
            "samsung",
            "huawei",
            "xiaomi",
            "vivo",
            "oppo",
            "oneplus",
            "ldap",
            "zimbra",
            "fastmail",
        )
    }
}
