package com.paoloprandoni.businesscard.ui.scan

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.paoloprandoni.businesscard.core.ServiceLocator
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.domain.ocr.OcrEngine
import com.paoloprandoni.businesscard.domain.parser.BusinessCardParser
import com.paoloprandoni.businesscard.domain.parser.LogoRegionSuggester
import com.paoloprandoni.businesscard.domain.repository.ScanSessionRepository
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
 * Orchestrazione della scansione: import immagine (EXIF corretto) → OCR locale → parsing →
 * sessione pronta per la revisione.
 */
class ScanViewModel(
    private val imageProcessor: ImageProcessor,
    private val ocrEngine: OcrEngine,
    private val parser: BusinessCardParser,
    private val session: ScanSessionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    fun processImage(context: Context, uri: Uri) {
        if (_ui.value.isProcessing) return
        viewModelScope.launch {
            _ui.update { it.copy(isProcessing = true, stage = "Preparazione immagine…", error = null) }
            try {
                val file = imageProcessor.importImage(context, uri)
                    ?: error("Impossibile leggere l'immagine selezionata.")
                val bitmap = imageProcessor.decode(file)
                    ?: error("Immagine non valida o danneggiata.")

                _ui.update { it.copy(stage = "Riconoscimento testo in corso (offline)…") }
                val ocr = ocrEngine.recognize(bitmap)
                bitmap.recycle()

                if (!ocr.hasText) {
                    _ui.update {
                        it.copy(
                            isProcessing = false,
                            stage = "",
                            error = "Nessun testo riconosciuto. Riprova con più luce e il biglietto ben a fuoco.",
                        )
                    }
                    return@launch
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
            } catch (throwable: Throwable) {
                _ui.update {
                    it.copy(
                        isProcessing = false,
                        stage = "",
                        error = throwable.message ?: "Errore durante l'analisi dell'immagine.",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun reset() {
        _ui.value = ScanUiState()
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
