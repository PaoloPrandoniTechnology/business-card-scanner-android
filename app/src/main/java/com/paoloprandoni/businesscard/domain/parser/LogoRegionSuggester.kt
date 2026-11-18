package com.paoloprandoni.businesscard.domain.parser

import android.graphics.Rect
import android.graphics.RectF
import com.paoloprandoni.businesscard.domain.model.OcrResult
import kotlin.math.max
import kotlin.math.min

/**
 * Propone il box iniziale di ritaglio del logo.
 *
 * L'area utile è sempre quella del biglietto (unione dei `boundingBox` del testo), così il
 * ritaglio non finisce mai sullo sfondo della foto. Dentro quell'area:
 * 1. se c'è una riga "brand" (font più grande, parte alta del biglietto) si propone un quadrato
 *    attorno a quella riga — è quasi sempre il logo;
 * 2. altrimenti si cerca il quadrato con meno testo, usando una griglia di occupazione.
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

        val boxes = ocr.lines.mapNotNull { it.box }
        if (boxes.isEmpty()) return fallback

        val card = textUnion(boxes, width, height)
        if (card.width() < 0.05f || card.height() < 0.02f) return fallback

        logoLineCandidate(ocr, card)?.let { return it }

        val occupied = buildOccupancy(boxes, width, height)
        return emptiestRectInside(card, occupied)
    }

    /** Area del biglietto: unione dei riquadri di testo, con un piccolo margine. */
    private fun textUnion(boxes: List<Rect>, width: Float, height: Float): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        boxes.forEach { box ->
            minX = min(minX, box.left.toFloat())
            minY = min(minY, box.top.toFloat())
            maxX = max(maxX, box.right.toFloat())
            maxY = max(maxY, box.bottom.toFloat())
        }
        val marginX = (maxX - minX) * 0.04f
        val marginY = (maxY - minY) * 0.04f
        return RectF(
            ((minX - marginX) / width).coerceIn(0f, 1f),
            ((minY - marginY) / height).coerceIn(0f, 1f),
            ((maxX + marginX) / width).coerceIn(0f, 1f),
            ((maxY + marginY) / height).coerceIn(0f, 1f),
        )
    }

    /** Quadrato attorno alla riga con il font più grande nella parte alta del biglietto. */
    private fun logoLineCandidate(ocr: OcrResult, card: RectF): RectF? {
        val height = ocr.imageHeight.toFloat()
        val width = ocr.imageWidth.toFloat()
        val heights = ocr.lines.mapNotNull { it.box?.height()?.toFloat() }.sorted()
        if (heights.isEmpty()) return null
        val median = heights[heights.size / 2].coerceAtLeast(1f)

        val brand = ocr.lines
            .mapNotNull { line -> line.box }
            .filter { box -> box.height() >= median * 1.3f }
            .filter { box -> (box.centerY() / height) <= card.top + card.height() * 0.55f }
            .maxByOrNull { box -> box.height() }
            ?: return null

        val line = RectF(
            brand.left / width,
            brand.top / height,
            brand.right / width,
            brand.bottom / height,
        )
        if (line.height() <= 0f) return null

        var sideHeight = (line.height() * 2.6f).coerceAtMost(card.height())
        var sideWidth = sideHeight * (height / width)
        if (sideWidth > card.width()) {
            sideWidth = card.width()
            sideHeight = sideWidth * (width / height)
        }

        val left = (line.left - line.height() * 0.6f)
            .coerceIn(card.left, (card.right - sideWidth).coerceAtLeast(card.left))
        val top = (line.top - line.height() * 0.5f)
            .coerceIn(card.top, (card.bottom - sideHeight).coerceAtLeast(card.top))
        return RectF(left, top, left + sideWidth, top + sideHeight)
    }

    private fun buildOccupancy(boxes: List<Rect>, width: Float, height: Float): Array<BooleanArray> {
        val occupied = Array(ROWS) { BooleanArray(COLS) }
        boxes.forEach { box ->
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
        return occupied
    }

    /** Quadrato con meno testo, sempre contenuto nel biglietto. */
    private fun emptiestRectInside(card: RectF, occupied: Array<BooleanArray>): RectF {
        var bestScore = Float.NEGATIVE_INFINITY
        var best = card

        val fractions = floatArrayOf(0.35f, 0.5f, 0.65f, 0.8f, 0.95f)
        fractions.forEach { fraction ->
            val sideHeight = fraction * card.height()
            val sideWidth = sideHeight * (card.width() / card.height())
            if (sideWidth <= card.width() && sideHeight <= card.height()) {
                var left = card.left
                while (left + sideWidth <= card.right + 0.001f) {
                    var top = card.top
                    while (top + sideHeight <= card.bottom + 0.001f) {
                        val rect = RectF(left, top, left + sideWidth, top + sideHeight)
                        val score = emptyRatio(rect, occupied) * 2f + (1f - top) * 0.35f
                        if (score > bestScore) {
                            bestScore = score
                            best = rect
                        }
                        top += 0.02f
                    }
                    left += 0.02f
                }
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
