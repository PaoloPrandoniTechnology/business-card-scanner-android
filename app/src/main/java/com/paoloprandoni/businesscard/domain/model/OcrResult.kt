package com.paoloprandoni.businesscard.domain.model

import android.graphics.Rect

/** Singola parola riconosciuta dall'OCR, con il proprio rettangolo sull'immagine. */
data class OcrElement(
    val text: String,
    val box: Rect?,
)

/** Riga di testo riconosciuta: unità minima usata dal parser. */
data class OcrLine(
    val text: String,
    val box: Rect?,
    val elements: List<OcrElement> = emptyList(),
)

/**
 * Risultato OCR normalizzato, indipendente dall'implementazione (ML Kit).
 *
 * [imageWidth] / [imageHeight] servono per normalizzare i `boundingBox` (crop del logo,
 * euristiche posizionali del parser).
 */
data class OcrResult(
    val text: String,
    val lines: List<OcrLine>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val hasText: Boolean get() = lines.any { it.text.isNotBlank() }
}
