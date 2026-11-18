package com.paoloprandoni.businesscard

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.data.ocr.MlKitOcrEngine
import com.paoloprandoni.businesscard.domain.parser.BusinessCardParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica end-to-end sul dispositivo della pipeline reale:
 * import immagine (correzione EXIF) → OCR ML Kit → parsing.
 *
 * Usa il biglietto di esempio in `androidTest/assets/sample_card.webp`.
 */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val testContext = instrumentation.context

    @Test
    fun importOcrAndParseSampleCard() = runBlocking {
        val imageFile = File(targetContext.cacheDir, "sample_card.webp")
        testContext.assets.open("sample_card.webp").use { input ->
            imageFile.outputStream().use { output -> input.copyTo(output) }
        }

        val processor = ImageProcessor()
        val imported = processor.importImage(targetContext, Uri.fromFile(imageFile))
        assertNotNull("importImage ha restituito null", imported)
        println("IMPORTED=${imported!!.absolutePath} (${imported.length()} byte)")

        val bitmap = processor.decode(imported!!, 2400)
        assertNotNull("decode ha restituito null", bitmap)
        println("BITMAP=${bitmap!!.width}x${bitmap.height}")

        val engine = MlKitOcrEngine()
        val ocr = try {
            engine.recognize(bitmap)
        } finally {
            engine.close()
        }
        println("OCR_START\n${ocr.text}\nOCR_END")
        assertTrue("nessun testo riconosciuto", ocr.hasText)

        val card = BusinessCardParser().parse(ocr)
        println("PARSED=$card")

        val digits = { value: String -> value.filter { it.isDigit() } }

        assertTrue("nome: '${card.firstName}'", card.firstName.equals("Claudio", ignoreCase = true))
        assertTrue("cognome: '${card.lastName}'", card.lastName.equals("Bresciani", ignoreCase = true))
        assertTrue("azienda: '${card.company}'", card.company.contains("BIPIESSE", ignoreCase = true))
        assertTrue(
            "ufficio: '${card.workPhone}'",
            digits(card.workPhone).endsWith("0233912016"),
        )
        assertTrue("fax: '${card.faxPhone}'", digits(card.faxPhone).endsWith("0233914337"))
        assertTrue(
            "cellulare: '${card.mobilePhone}'",
            digits(card.mobilePhone).endsWith("3405325561"),
        )
        assertTrue(
            "email: '${card.emails}'",
            card.emails.any { it.equals("c.bresciani@bipiesse.it", ignoreCase = true) },
        )
        assertTrue("sito: '${card.website}'", card.website.contains("bipiesse.it", ignoreCase = true))
        assertTrue(
            "indirizzo: '${card.address}'",
            card.address.contains("Via Risorgimento", ignoreCase = true),
        )
        assertTrue("CAP: '${card.address}'", card.address.contains("20016"))
    }
}
