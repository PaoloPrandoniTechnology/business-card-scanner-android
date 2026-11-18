package com.paoloprandoni.businesscard.domain.parser

/** Componenti di un indirizzo, mappati su [android.provider.ContactsContract.CommonDataKinds.StructuredPostal]. */
data class AddressParts(
    val street: String = "",
    val city: String = "",
    val postalCode: String = "",
    val region: String = "",
    val country: String = "",
)

/**
 * Scompone l'indirizzo formattato (una riga, separato da virgole) nelle parti strutturate
 * richieste dalla rubrica Android.
 *
 * Gestisce i formati tipici italiani ("Via Roma 12, 20121 Milano MI, Italia"),
 * la provincia tra parentesi ("Milano (MI)") e l'indirizzo su un'unica riga senza virgole.
 */
object AddressParser {

    private val CAP_REGEX = Regex("\\b\\d{5}\\b")
    private val REGION_REGEX = Regex("^[A-Z]{2}$")

    fun parse(address: String): AddressParts {
        val text = address.trim()
        if (text.isEmpty()) return AddressParts()

        var street = ""
        var city = ""
        var postalCode = ""
        var region = ""
        var country = ""

        val remaining = mutableListOf<String>()
        val tokens = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        tokens.forEach { token ->
            val capMatch = CAP_REGEX.find(token)
            if (capMatch != null && postalCode.isEmpty()) {
                postalCode = capMatch.value
                val before = token.substring(0, capMatch.range.first).trim().trimEnd(',', ';', '-')
                val after = token.substring(capMatch.range.last + 1).trim()
                if (before.isNotEmpty()) remaining += before
                if (after.isNotEmpty()) {
                    val parsed = parseCityAndRegion(after)
                    city = parsed.first
                    region = parsed.second
                }
            } else {
                remaining += token
            }
        }

        // Nazione: ultimo token se riconosciuto.
        val last = remaining.lastOrNull()
        if (last != null && isCountry(last)) {
            country = last
            remaining.removeAt(remaining.lastIndex)
        }

        street = remaining.joinToString(", ")

        if (postalCode.isEmpty()) {
            CAP_REGEX.find(text)?.let { postalCode = it.value }
        }
        if (city.isEmpty()) {
            city = cityFromTail(remaining)
        }

        return AddressParts(
            street = street.trim(),
            city = city.trim(),
            postalCode = postalCode.trim(),
            region = region.trim(),
            country = country.trim(),
        )
    }

    private fun parseCityAndRegion(value: String): Pair<String, String> {
        val cleaned = value.trim().trim('(', ')')
        val parenthesized = Regex("\\(([A-Za-z]{2})\\)").find(cleaned)
        if (parenthesized != null) {
            val province = parenthesized.groupValues[1].uppercase()
            val city = cleaned.replace(parenthesized.value, "").trim().trimEnd(',', '-')
            return city to province
        }
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 2) {
            val candidate = words.last().trim('(', ')', ',')
            if (REGION_REGEX.matches(candidate)) {
                return words.dropLast(1).joinToString(" ") to candidate
            }
        }
        return cleaned to ""
    }

    /** Se non c'è CAP, l'ultimo elemento rimasto è probabilmente la città. */
    private fun cityFromTail(remaining: List<String>): String {
        val last = remaining.lastOrNull() ?: return ""
        if (remaining.size < 2) return ""
        if (last.length > 40 || last.any { it.isDigit() }) return ""
        return last
    }

    private fun isCountry(value: String): Boolean {
        val normalized = value.trim().trimEnd('.').lowercase()
        return normalized in KeywordDictionaries.COUNTRY_NAMES
    }
}
