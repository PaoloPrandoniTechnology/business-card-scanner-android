package com.paoloprandoni.businesscard.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.paoloprandoni.businesscard.core.ServiceLocator
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.domain.image.ViewfinderRoi
import com.paoloprandoni.businesscard.domain.ocr.OcrEngine
import com.paoloprandoni.businesscard.domain.parser.BusinessCardParser
import com.paoloprandoni.businesscard.domain.parser.LogoRegionSuggester
import com.paoloprandoni.businesscard.domain.repository.ScanSessionRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val isProcessing: Boolean = false,
    val stage: String = "",
    val error: String? = null,
    val imagePath: String? = null,
    /** Timestamp dell'ultima scansione completata: usato per navigare alla revisione. */
    val completedAt: Long = 0L,
)

/**
 * Orchestrazione della scansione: import immagine (EXIF corretto) → eventuale ritaglio dell'area
 * di scansione (ROI) → OCR locale → parsing → sessione pronta per la revisione.
 *
 * L'OCR lavora **solo** sull'area selezionata: con la fotocamera è la cornice guida mostrata
 * durante lo scatto, con la galleria è il rettangolo confermato dall'utente.
 */
class ScanViewModel(
    private val imageProcessor: ImageProcessor,
    private val ocrEngine: OcrEngine,
    private val parser: BusinessCardParser,
    private val session: ScanSessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    /**
     * Importa l'immagine scelta e, se sono note la cornice della fotocamera e le dimensioni della
     * preview, ritaglia l'area inquadrata prima dell'OCR.
     *
     * @param viewRoi rettangolo della cornice guida in pixel della PreviewView.
     * @param viewSize dimensioni della PreviewView.
     */
    fun processImage(
        context: Context,
        uri: Uri,
        viewRoi: RectF? = null,
        viewSize: IntSize? = null,
    ) {
        if (_ui.value.isProcessing) return
        viewModelScope.launch {
            startProcessing()
            try {
                var file = imageProcessor.importImage(context, uri)
                    ?: error("Impossibile leggere l'immagine selezionata.")

                if (viewRoi != null && viewSize != null && viewSize.width > 0 && viewSize.height > 0) {
                    val aspect = imageProcessor.imageAspect(file)
                    if (aspect != null) {
                        val region = ViewfinderRoi.toImageRegion(
                            viewLeft = viewRoi.left,
                            viewTop = viewRoi.top,
                            viewRight = viewRoi.right,
                            viewBottom = viewRoi.bottom,
                            viewWidth = viewSize.width,
                            viewHeight = viewSize.height,
                            imageAspect = aspect,
                        )
                        val cropped = imageProcessor.cropRegion(context, file, region)
                        if (cropped != null) {
                            file.delete()
                            file = cropped
                        }
                    }
                }

                analyze(file)
            } catch (throwable: Throwable) {
                fail(throwable.message ?: "Errore durante l'analisi dell'immagine.")
            }
        }
    }

    /** Analizza un'immagine già ritagliata dall'utente (galleria con area di scansione). */
    fun processBitmap(context: Context, bitmap: Bitmap) {
        if (_ui.value.isProcessing) return
        viewModelScope.launch {
            startProcessing()
            try {
                val file = imageProcessor.saveBitmap(context, bitmap, prefix = "roi")
                    ?: error("Immagine non valida o danneggiata.")
                analyze(file)
            } catch (throwable: Throwable) {
                fail(throwable.message ?: "Errore durante l'analisi dell'immagine.")
            }
        }
    }

    /** Mostra un errore senza avviare l'elaborazione (es. import immagine fallito). */
    fun reportError(message: String) {
        _ui.update { it.copy(isProcessing = false, stage = "", error = message) }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun reset() {
        _ui.value = ScanUiState()
    }

    private fun startProcessing() {
        _ui.update { it.copy(isProcessing = true, stage = "Preparazione immagine…", error = null) }
    }

    private fun fail(message: String) {
        _ui.update { it.copy(isProcessing = false, stage = "", error = message) }
    }

    private suspend fun analyze(file: File) {
        _ui.update { it.copy(stage = "Riconoscimento testo in corso (offline)…") }
        val bitmap = imageProcessor.decode(file)
            ?: error("Immagine non valida o danneggiata.")
        val ocr = ocrEngine.recognize(bitmap)
        bitmap.recycle()

        if (!ocr.hasText) {
            fail("Nessun testo riconosciuto nell'area selezionata. Inquadra meglio il biglietto o allarga l'area.")
            return
        }

        _ui.update { it.copy(stage = "Estrazione dei dati…") }
        val card = parser.parse(ocr)
        val logoHint = LogoRegionSuggester.suggest(ocr)
        session.start(file.absolutePath, card, logoHint)

        _ui.update {
            it.copy(
                isProcessing = false,
                stage = "",
                imagePath = file.absolutePath,
                completedAt = System.currentTimeMillis(),
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScanViewModel(
                    imageProcessor = ServiceLocator.imageProcessor,
                    ocrEngine = ServiceLocator.ocrEngine,
                    parser = ServiceLocator.parser,
                    session = ServiceLocator.scanSession,
                )
            }
        }
    }
}
