package com.paoloprandoni.businesscard.domain.ocr

import android.graphics.Bitmap
import com.paoloprandoni.businesscard.domain.model.OcrResult

/** Motore OCR. L'implementazione concreta è locale (ML Kit on-device). */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult

    fun close()
}
