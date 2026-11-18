package com.paoloprandoni.businesscard.core

import android.content.Context
import com.paoloprandoni.businesscard.data.contacts.ContactAccountsRepository
import com.paoloprandoni.businesscard.data.contacts.ContactsRepositoryImpl
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.data.ocr.MlKitOcrEngine
import com.paoloprandoni.businesscard.data.session.InMemoryScanSessionRepository
import com.paoloprandoni.businesscard.domain.ocr.OcrEngine
import com.paoloprandoni.businesscard.domain.parser.BusinessCardParser
import com.paoloprandoni.businesscard.domain.repository.ContactsRepository
import com.paoloprandoni.businesscard.domain.repository.ScanSessionRepository

/**
 * Dependency injection minimale (service locator): evita un framework DI esterno
 * mantenendo le dipendenze esplicite nei costruttori dei ViewModel.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val imageProcessor: ImageProcessor by lazy { ImageProcessor() }

    val ocrEngine: OcrEngine by lazy { MlKitOcrEngine() }

    val parser: BusinessCardParser by lazy { BusinessCardParser() }

    val scanSession: ScanSessionRepository by lazy { InMemoryScanSessionRepository() }

    val contactsRepository: ContactsRepository by lazy {
        ContactsRepositoryImpl(appContext.contentResolver)
    }

    val accountsRepository: ContactAccountsRepository by lazy {
        ContactAccountsRepository(appContext, appContext.contentResolver)
    }
}
