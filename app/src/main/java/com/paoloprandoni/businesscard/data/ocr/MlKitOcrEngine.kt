package com.paoloprandoni.businesscard.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paoloprandoni.businesscard.domain.model.OcrElement
import com.paoloprandoni.businesscard.domain.model.OcrLine
import com.paoloprandoni.businesscard.domain.model.OcrResult
import com.paoloprandoni.businesscard.domain.ocr.OcrEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR locale con ML Kit Text Recognition v2 (modello **bundled**).
 *
 * Il modello è incluso nell'APK: nessuna chiamata di rete, nessun download a runtime,
 * l'app non dichiara nemmeno il permesso INTERNET.
 */
class MlKitOcrEngine : OcrEngine {

    private var recognizerRef: TextRecognizer? = null

    private val recognizer: TextRecognizer
        get() = recognizerRef ?: TextRecognition
            .getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { recognizerRef = it }

    override suspend fun recognize(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val input = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(input)
                .addOnSuccessListener { text ->
                    if (continuation.isActive) {
                        continuation.resume(text.toOcrResult(bitmap.width, bitmap.height))
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

    override fun close() {
        recognizerRef?.close()
        recognizerRef = null
    }

    private fun Text.toOcrResult(width: Int, height: Int): OcrResult {
        val lines = mutableListOf<OcrLine>()
        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                lines += OcrLine(
                    text = line.text,
                    box = line.boundingBox,
                    elements = line.elements.map { element ->
                        OcrElement(text = element.text, box = element.boundingBox)
                    },
                )
            }
        }
        return OcrResult(
            text = text,
            lines = lines,
            imageWidth = width,
            imageHeight = height,
        )
    }
}
