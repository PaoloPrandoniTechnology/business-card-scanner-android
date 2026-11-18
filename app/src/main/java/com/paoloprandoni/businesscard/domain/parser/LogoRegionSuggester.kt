package com.paoloprandoni.businesscard.domain.parser

import android.graphics.RectF
import com.paoloprandoni.businesscard.domain.model.OcrResult
import kotlin.math.abs
import kotlin.math.min

/**
 * Propone il box iniziale di ritaglio del logo.
 *
 * Costruisce una griglia di occupazione a partire dai `boundingBox` delle righe OCR e cerca il
 * quadrato più "vuoto" (con preferenza per gli angoli), così il ritaglio parte tipicamente
 * sull'area del biglietto dove non è stato rilevato testo.
 *
 * @return rettangolo normalizzato (0..1) sull'immagine.
 */
object LogoRegionSuggester {

    private const val COLS = 40
    private const val ROWS = 30

    fun suggest(ocr: OcrResult): RectF {
        val width = ocr.imageWidth.toFloat()
        val height = ocr.imageHeight.toFloat()
        val fallback = RectF(0.05f, 0.05f, 0.35f, 0.35f)
        if (width <= 0f || height <= 0f) return fallback

        val occupied = Array(ROWS) { BooleanArray(COLS) }
        ocr.lines.forEach { line ->
            val box = line.box ?: return@forEach
            val x0 = ((box.left / width) * COLS).toInt().coerceIn(0, COLS - 1)
            val x1 = ((box.right / width) * COLS).toInt().coerceIn(0, COLS - 1)
            val y0 = ((box.top / height) * ROWS).toInt().coerceIn(0, ROWS - 1)
            val y1 = ((box.bottom / height) * ROWS).toInt().coerceIn(0, ROWS - 1)
            for (y in y0..y1) {
                for (x in x0..x1) {
                    occupied[y][x] = true
                }
            }
        }

        val shortSide = min(width, height)
        var bestScore = Float.NEGATIVE_INFINITY
        var best = fallback
        val sizeFractions = floatArrayOf(0.22f, 0.30f, 0.38f, 0.46f)
        val step = 0.03f

        for (fraction in sizeFractions) {
            val side = fraction * shortSide
            val widthFraction = side / width
            val heightFraction = side / height
            var left = 0.03f
            while (left <= 1f - widthFraction) {
                var top = 0.03f
                while (top <= 1f - heightFraction) {
                    val rect = RectF(left, top, left + widthFraction, top + heightFraction)
                    val empty = emptyRatio(rect, occupied)
                    val centerDistance = abs(rect.centerX() - 0.5f) + abs(rect.centerY() - 0.5f)
                    val score = empty * 2f - centerDistance.coerceIn(0f, 1f) * 0.4f + fraction * 0.6f
                    if (score > bestScore) {
                        bestScore = score
                        best = rect
                    }
                    top += step
                }
                left += step
            }
        }
        return best
    }

    private fun emptyRatio(rect: RectF, occupied: Array<BooleanArray>): Float {
        val x0 = (rect.left * COLS).toInt().coerceIn(0, COLS - 1)
        val x1 = (rect.right * COLS).toInt().coerceIn(0, COLS - 1)
        val y0 = (rect.top * ROWS).toInt().coerceIn(0, ROWS - 1)
        val y1 = (rect.bottom * ROWS).toInt().coerceIn(0, ROWS - 1)
        var total = 0
        var empty = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                total++
                if (!occupied[y][x]) empty++
            }
        }
        return if (total == 0) 0f else empty.toFloat() / total
    }
}
