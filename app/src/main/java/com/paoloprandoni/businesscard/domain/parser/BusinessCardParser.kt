package com.paoloprandoni.businesscard.domain.parser

import com.paoloprandoni.businesscard.domain.model.BusinessCardData
import com.paoloprandoni.businesscard.domain.model.OcrLine
import com.paoloprandoni.businesscard.domain.model.OcrResult
import kotlin.math.abs

/**
 * Parser euristico e 100% offline dei biglietti da visita.
 *
 * Strategia:
 * 1. normalizzazione delle righe OCR e ricostruzione dell'ordine di lettura (top → left);
 * 2. estrazione deterministica con regex (email, siti, telefoni, CAP, P.IVA/CF);
 * 3. classificazione con dizionari IT/EN di ruoli, marchi societari, vie, titoli;
 * 4. euristiche dimensionali (altezza dei `boundingBox` ≈ dimensione del font) e posizionali
 *    (il nome è quasi sempre la riga più grande nella parte alta del biglietto);
 * 5. tutto ciò che non viene classificato finisce nelle note: nessuna informazione persa,
 *    e il testo OCR grezzo è sempre disponibile in [BusinessCardData.rawText].
 */
class BusinessCardParser {

    private data class Line(
        val index: Int,
        val text: String,
        val height: Float,
        val top: Int,
        val left: Int,
    )

    private enum class PhoneType { MOBILE, WORK, FAX, UNKNOWN }

    private data class PhoneHit(val number: String, val type: PhoneType)

    fun parse(ocr: OcrResult): BusinessCardData {
        val lines = buildLines(ocr)
        if (lines.isEmpty()) {
            return BusinessCardData(rawText = ocr.text.trim())
        }

        val consumed = BooleanArray(lines.size)
        val medianHeight = median(lines.map { it.height })

        // --- 1. Email (una o più) ---
        val emails = LinkedHashSet<String>()
        lines.forEachIndexed { i, line ->
            val found = EMAIL_REGEX
                .findAll(deobfuscate(line.text))
                .map { it.value.trim('.', ',', ';', ')', '>', '\'') }
                .filter { isValidEmail(it) }
                .map { it.lowercase() }
                .toList()
            if (found.isNotEmpty()) {
                emails.addAll(found)
                consumed[i] = true
            }
        }

        // --- 2. Sito web ---
        var website = ""
        for (i in lines.indices) {
            if (consumed[i] || website.isNotBlank()) continue
            val url = extractWebsite(lines[i].text)
            if (url != null) {
                website = url
                consumed[i] = true
            }
        }

        // --- 3. Numeri di telefono ---
        val phoneHits = mutableListOf<PhoneHit>()
        for (i in lines.indices) {
            if (consumed[i] || lines[i].text.contains("@")) continue
            // P.IVA, codice fiscale, REA, IBAN... non sono recapiti telefonici.
            if (containsAny(lines[i].text, KeywordDictionaries.FISCAL_KEYWORDS)) continue
            val hits = extractPhones(lines[i].text)
            if (hits.isNotEmpty()) {
                phoneHits += hits
                if (stripPhonesAndLabels(lines[i].text).length < 14) consumed[i] = true
            }
        }
        var mobile = ""
        var work = ""
        var fax = ""
        val phoneOverflow = mutableListOf<String>()
        for (hit in phoneHits) {
            when (hit.type) {
                PhoneType.FAX -> if (fax.isBlank()) fax = hit.number else phoneOverflow += hit.number
                PhoneType.WORK -> if (work.isBlank()) work = hit.number else phoneOverflow += hit.number
                PhoneType.MOBILE -> if (mobile.isBlank()) mobile = hit.number else phoneOverflow += hit.number
                PhoneType.UNKNOWN -> when {
                    mobile.isBlank() -> mobile = hit.number
                    work.isBlank() -> work = hit.number
                    else -> phoneOverflow += hit.number
                }
            }
        }

        // --- 4. Nome e cognome ---
        val nameIndex = detectNameLine(lines, consumed, medianHeight)
        var namePrefix = ""
        var firstName = ""
        var lastName = ""
        if (nameIndex != null) {
            consumed[nameIndex] = true
            val split = splitName(lines[nameIndex].text)
            namePrefix = split.first
            firstName = split.second
            lastName = split.third
        }

        // --- 5. Ruolo ---
        val roleIndex = detectRoleLine(lines, consumed, nameIndex)
        var jobTitle = ""
        if (roleIndex != null) {
            consumed[roleIndex] = true
            jobTitle = cleanRole(lines[roleIndex].text)
        }

        // --- 6. Azienda ---
        val companyIndex = detectCompanyLine(lines, consumed, nameIndex, medianHeight)
        var company = ""
        if (companyIndex != null) {
            consumed[companyIndex] = true
            company = cleanCompany(lines[companyIndex].text)
        }

        // --- 7. Indirizzo ---
        val addressStart = detectAddressStart(lines, consumed)
        var address = ""
        if (addressStart != null) {
            address = collectAddress(lines, consumed, addressStart)
        }

        // --- 8. Note: righe non classificate + numeri in eccesso ---
        val extras = lines
            .filterIndexed { i, line -> !consumed[i] && line.text.isNotBlank() }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
        val notesParts = mutableListOf<String>()
        notesParts += extras
        if (phoneOverflow.isNotEmpty()) {
            notesParts += phoneOverflow.map { "Altro numero: $it" }
        }

        return BusinessCardData(
            namePrefix = namePrefix,
            firstName = firstName,
            lastName = lastName,
            company = company,
            jobTitle = jobTitle,
            mobilePhone = mobile,
            workPhone = work,
            faxPhone = fax,
            emails = emails.toList(),
            website = website,
            address = address,
            notes = notesParts.joinToString("\n"),
            rawText = ocr.text.trim(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Costruzione righe / normalizzazione
    // ---------------------------------------------------------------------------------------

    private fun buildLines(ocr: OcrResult): List<Line> {
        val parsed = mutableListOf<Line>()
        ocr.lines.forEach { ocrLine: OcrLine ->
            val text = normalize(ocrLine.text)
            if (text.isEmpty()) return@forEach
            parsed += Line(
                index = 0,
                text = text,
                height = ((ocrLine.box?.height() ?: 0)).toFloat(),
                top = ocrLine.box?.top ?: 0,
                left = ocrLine.box?.left ?: 0,
            )
        }
        val sorted = parsed.sortedWith(compareBy({ it.top }, { it.left }))
        // Elimina righe duplicate consecutive prodotte da OCR su testo a doppia esposizione.
        val deduped = mutableListOf<Line>()
        sorted.forEach { line ->
            val previous = deduped.lastOrNull()
            if (previous == null || !previous.text.equals(line.text, ignoreCase = true)) {
                deduped += line
            }
        }
        return deduped.mapIndexed { i, line -> line.copy(index = i) }
    }

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace(Regex("[•·|»«▪◦]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun median(values: List<Float>): Float {
        val positive = values.filter { it > 0f }.sorted()
        if (positive.isEmpty()) return 1f
        val middle = positive.size / 2
        val value = if (positive.size % 2 == 0) {
            (positive[middle - 1] + positive[middle]) / 2f
        } else {
            positive[middle]
        }
        return value.coerceAtLeast(1f)
    }

    private fun deobfuscate(text: String): String = text
        .replace(Regex("\\s*[\\(\\[]\\s*(at|chiocciola)\\s*[\\)\\]]\\s*", RegexOption.IGNORE_CASE), "@")
        .replace(Regex("\\s*[\\(\\[]\\s*(dot|punto)\\s*[\\)\\]]\\s*", RegexOption.IGNORE_CASE), ".")

    // ---------------------------------------------------------------------------------------
    // Email / siti
    // ---------------------------------------------------------------------------------------

    private fun isValidEmail(value: String): Boolean {
        if (!value.contains('@') || value.contains(' ')) return false
        val domain = value.substringAfterLast('@')
        if (!domain.contains('.')) return false
        val tld = domain.substringAfterLast('.')
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    private fun extractWebsite(text: String): String? {
        if (text.contains("@")) return null
        val candidates = URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '\'') }
            .filter { it.contains('.') }
        for (candidate in candidates) {
            val lower = candidate.lowercase()
            val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
            val hasWww = lower.startsWith("www.")
            val tld = lower.substringAfterLast('/').substringAfterLast('.')
            if (!hasScheme && !hasWww && tld !in KeywordDictionaries.TLDS) continue
            if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".pdf")) continue
            return candidate
        }
        return null
    }

    // ---------------------------------------------------------------------------------------
    // Telefoni
    // ---------------------------------------------------------------------------------------

    private fun extractPhones(rawLine: String): List<PhoneHit> {
        val line = rawLine.replace('–', '-').replace('—', '-')
        val matches = PHONE_REGEX.findAll(line).toList()
        val hits = mutableListOf<PhoneHit>()
        matches.forEachIndexed { index, match ->
            val rawNumber = match.value.trim()
            val digits = rawNumber.count { it.isDigit() }
            if (digits < 6 || digits > 15) return@forEachIndexed
            val previousEnd = if (index == 0) 0 else matches[index - 1].range.last + 1
            val before = line.substring(previousEnd.coerceAtMost(match.range.first), match.range.first)
                .lowercase()
            val after = line.substring(match.range.last + 1).take(24).lowercase()
            val context = before.ifBlank { after }
            hits += PhoneHit(formatPhone(rawNumber), classifyPhone(context, rawNumber))
        }
        return hits
    }

    private fun classifyPhone(context: String, number: String): PhoneType {
        if (KeywordDictionaries.FAX_WORDS.any { context.contains(it) }) return PhoneType.FAX
        if (KeywordDictionaries.MOBILE_WORDS.any { context.contains(it) }) return PhoneType.MOBILE
        if (KeywordDictionaries.WORK_WORDS.any { context.contains(it) }) return PhoneType.WORK
        val digits = number.filter { it.isDigit() }
        val local = if (digits.startsWith("39") && digits.length > 10) digits.removePrefix("39") else digits
        return when {
            local.startsWith("3") && local.length in 9..11 -> PhoneType.MOBILE
            local.startsWith("0") -> PhoneType.WORK
            else -> PhoneType.UNKNOWN
        }
    }

    /** Mantiene il prefisso internazionale e normalizza i separatori ("+39 333 123 4567"). */
    private fun formatPhone(raw: String): String {
        val hasPlus = raw.trimStart().startsWith("+")
        val cleaned = raw
            .replace(Regex("[^0-9+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("+")
        val body = if (hasPlus) "+$cleaned" else cleaned
        return body.trim()
    }

    private fun stripPhonesAndLabels(text: String): String {
        var result = text.replace(PHONE_REGEX, " ")
        KeywordDictionaries.WORK_WORDS.forEach { result = result.replace(it, " ", ignoreCase = true) }
        KeywordDictionaries.MOBILE_WORDS.forEach { result = result.replace(it, " ", ignoreCase = true) }
        KeywordDictionaries.FAX_WORDS.forEach { result = result.replace(it, " ", ignoreCase = true) }
        return result.replace(Regex("[:\\-–—]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    // ---------------------------------------------------------------------------------------
    // Nome
    // ---------------------------------------------------------------------------------------

    private fun detectNameLine(lines: List<Line>, consumed: BooleanArray, medianHeight: Float): Int? {
        var bestIndex: Int? = null
        var bestScore = 0.0
        val half = lines.size / 2
        lines.forEach { line ->
            if (consumed[line.index]) return@forEach
            val text = line.text
            if (text.contains("@")) return@forEach
            if (text.any { it.isDigit() }) return@forEach
            if (text.contains("www.", ignoreCase = true) || text.contains("http", ignoreCase = true)) return@forEach
            if (containsAny(text, KeywordDictionaries.ROLE_KEYWORDS)) return@forEach
            if (containsAny(text, KeywordDictionaries.COMPANY_STRONG_MARKERS)) return@forEach
            if (containsAny(text, KeywordDictionaries.STREET_KEYWORDS)) return@forEach

            val words = text.split(" ").filter { it.isNotBlank() }
            if (words.size < 2 || words.size > 5) return@forEach
            if (words.all { it.length <= 2 }) return@forEach

            var score = 1.0
            if (line.height >= medianHeight * 1.15f) score += 2.0
            if (line.height >= medianHeight * 1.4f) score += 1.0
            score += when (line.index) {
                0 -> 2.0
                1 -> 1.6
                2 -> 1.2
                3 -> 0.7
                else -> 0.0
            }
            if (words.size in 2..3) score += 0.6
            if (words.all { it.firstOrNull()?.isUpperCase() == true }) score += 0.8
            if (HONORIFIC_REGEX.containsMatchIn(text)) score += 0.5
            if (line.index >= half) score -= 1.5
            if (words.any { it.length == 1 }) score -= 0.4
            if (score > bestScore) {
                bestScore = score
                bestIndex = line.index
            }
        }
        return if (bestScore >= 2.0) bestIndex else null
    }

    private fun splitName(raw: String): Triple<String, String, String> {
        var text = raw.trim()
        var prefix = ""
        val honorific = HONORIFIC_REGEX.find(text)
        if (honorific != null && honorific.range.first == 0) {
            prefix = KeywordDictionaries.canonicalHonorific(honorific.groupValues[1])
            text = text.removeRange(honorific.range).trim()
        }

        // "Mario Rossi | CEO" oppure "Mario Rossi – Sales Manager": tiene solo il nome.
        val segments = text.split(Regex("\\s*[|•·]\\s*")).filter { it.isNotBlank() }
        if (segments.size > 1) text = segments.first().trim()
        val dashed = text.split(Regex("\\s+[-–—]\\s+")).filter { it.isNotBlank() }
        if (dashed.size > 1) text = dashed.first().trim()

        val tokens = text.split(Regex("\\s+"))
            .map { it.trim(',', ';', '.', ':') }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Triple(prefix, "", "")
        if (tokens.size == 1) return Triple(prefix, tokens[0].toTitleCaseName(), "")

        val lower = tokens.map { it.lowercase() }
        val firstIsName = lower.first() in KeywordDictionaries.FIRST_NAMES
        val lastIsName = lower.last() in KeywordDictionaries.FIRST_NAMES

        val given = if (!firstIsName && lastIsName) tokens.last() else tokens.first()
        val family = if (!firstIsName && lastIsName) {
            tokens.dropLast(1).joinToString(" ")
        } else {
            tokens.drop(1).joinToString(" ")
        }
        return Triple(prefix, given.toTitleCaseName(), family.toTitleCaseName())
    }

    private fun String.toTitleCaseName(): String {
        if (isBlank()) return this
        if (this != uppercase()) return this
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Ruolo e azienda
    // ---------------------------------------------------------------------------------------

    private fun detectRoleLine(lines: List<Line>, consumed: BooleanArray, nameIndex: Int?): Int? {
        var bestIndex: Int? = null
        var bestScore = 0.0
        lines.forEach { line ->
            if (consumed[line.index] || line.text.contains("@")) return@forEach
            if (line.text.any { it.isDigit() }) return@forEach
            val matches = KeywordDictionaries.ROLE_KEYWORDS.count { containsWord(line.text, it) }
            if (matches == 0) return@forEach
            var score = matches * 2.0
            if (nameIndex != null && abs(line.index - nameIndex) <= 2) score += 1.5
            if (line.text.length > 60) score -= 1.0
            if (line.text.split(" ").size > 8) score -= 0.5
            if (score > bestScore) {
                bestScore = score
                bestIndex = line.index
            }
        }
        return if (bestScore >= 2.0) bestIndex else null
    }

    private fun cleanRole(raw: String): String = raw
        .replace(Regex("^(ruolo|posizione|title|job title|carica)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun detectCompanyLine(
        lines: List<Line>,
        consumed: BooleanArray,
        nameIndex: Int?,
        medianHeight: Float,
    ): Int? {
        var bestIndex: Int? = null
        var bestScore = 0.0
        lines.forEach { line ->
            if (consumed[line.index] || line.index == nameIndex) return@forEach
            val text = line.text
            if (text.contains("@")) return@forEach
            if (text.any { it.isDigit() }) return@forEach
            if (containsAny(text, KeywordDictionaries.ROLE_KEYWORDS)) return@forEach
            if (containsAny(text, KeywordDictionaries.STREET_KEYWORDS)) return@forEach

            var score = 0.0
            if (containsAny(text, KeywordDictionaries.COMPANY_STRONG_MARKERS)) score += 5.0
            if (containsAny(text, KeywordDictionaries.COMPANY_GENERIC_MARKERS)) score += 2.0
            if (text == text.uppercase() && text.length >= 4) score += 1.5
            if (line.height >= medianHeight * 1.1f) score += 0.8
            if (text.contains('&')) score += 0.5
            if (text.split(" ").size <= 5) score += 0.5
            if (text.length > 55) score -= 1.0
            if (score > bestScore) {
                bestScore = score
                bestIndex = line.index
            }
        }
        if (bestScore >= 2.0) return bestIndex

        // Fallback: riga grande e "istituzionale" rimasta libera.
        return lines
            .filter { !consumed[it.index] && it.index != nameIndex }
            .filter { !it.text.any { ch -> ch.isDigit() } && it.text.length in 3..45 }
            .filter { it.text.split(" ").size in 1..4 }
            .maxByOrNull { it.height }
            ?.takeIf { it.height >= medianHeight * 1.05f }
            ?.index
    }

    private fun cleanCompany(raw: String): String = raw
        .replace(Regex("^(azienda|societa|società|company|ditta|ragione sociale)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

    // ---------------------------------------------------------------------------------------
    // Indirizzo
    // ---------------------------------------------------------------------------------------

    private fun detectAddressStart(lines: List<Line>, consumed: BooleanArray): Int? =
        lines.indices.firstOrNull { i ->
            !consumed[i] &&
                !lines[i].text.contains("@") &&
                containsAny(lines[i].text, KeywordDictionaries.STREET_KEYWORDS)
        }

    private fun collectAddress(lines: List<Line>, consumed: BooleanArray, start: Int): String {
        val parts = mutableListOf<String>()
        var i = start
        while (i < lines.size && parts.size < 4) {
            if (consumed[i]) break
            val text = lines[i].text
            if (text.contains("@")) break
            if (containsAny(text, KeywordDictionaries.ADDRESS_STOP_WORDS)) break
            if (i > start && extractPhones(text).isNotEmpty()) break
            if (i > start && !looksLikeAddressContinuation(text)) break

            parts += text
            consumed[i] = true
            if (CAP_REGEX.containsMatchIn(text)) break
            i++
        }
        return joinAddress(parts)
    }

    private fun looksLikeAddressContinuation(text: String): Boolean {
        if (CAP_REGEX.containsMatchIn(text)) return true
        if (containsAny(text, KeywordDictionaries.STREET_KEYWORDS)) return true
        val words = text.split(" ").filter { it.isNotBlank() }
        return text.length <= 42 && words.size in 1..6 && text.any { it.isLetter() }
    }

    private fun joinAddress(parts: List<String>): String = parts
        .map { it.trim().trimEnd(',', ';', '-') }
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .replace(Regex("\\s*,\\s*"), ", ")
        .replace(Regex("(,\\s*)+"), ", ")
        .trim()
        .trimEnd(',')

    // ---------------------------------------------------------------------------------------
    // Matching parole chiave
    // ---------------------------------------------------------------------------------------

    private fun containsAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { containsWord(text, it) }

    private fun containsWord(text: String, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        val regex = WORD_REGEX_CACHE.getOrPut(keyword) {
            Regex(
                "(?<![A-Za-z0-9])${Regex.escape(keyword)}(?![A-Za-z0-9])",
                RegexOption.IGNORE_CASE,
            )
        }
        return regex.containsMatchIn(text)
    }

    private companion object {
        val EMAIL_REGEX = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
        val URL_REGEX = Regex(
            "((https?://)?(www\\.)?[A-Za-z0-9][A-Za-z0-9\\-]*(\\.[A-Za-z0-9\\-]+)+(/[^\\s]*)?)",
            RegexOption.IGNORE_CASE,
        )
        val PHONE_REGEX = Regex("(?<!\\d)(\\+?\\d[\\d\\s().\\-/]{4,20}\\d)")
        val CAP_REGEX = Regex("\\b\\d{5}\\b")
        val HONORIFIC_REGEX = Regex(
            "^(ing|dott\\.ssa|dott|avv|arch|prof|rag|geom|sig\\.ra|sig|dr|mr|mrs|ms|eng|notaio)\\.?\\s+",
            RegexOption.IGNORE_CASE,
        )
        val WORD_REGEX_CACHE = HashMap<String, Regex>()
    }
}
