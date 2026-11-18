package com.paoloprandoni.businesscard.ui.review

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.paoloprandoni.businesscard.core.ServiceLocator
import com.paoloprandoni.businesscard.data.contacts.ContactAccountsRepository
import com.paoloprandoni.businesscard.data.image.ImageProcessor
import com.paoloprandoni.businesscard.domain.model.BusinessCardData
import com.paoloprandoni.businesscard.domain.model.ContactAccount
import com.paoloprandoni.businesscard.domain.repository.ContactsRepository
import com.paoloprandoni.businesscard.domain.repository.ScanSessionRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato editabile del form di revisione. */
data class CardForm(
    val namePrefix: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val company: String = "",
    val jobTitle: String = "",
    val mobilePhone: String = "",
    val workPhone: String = "",
    val faxPhone: String = "",
    val emailsText: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
) {
    fun toCard(rawText: String): BusinessCardData = BusinessCardData(
        namePrefix = namePrefix.trim(),
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        company = company.trim(),
        jobTitle = jobTitle.trim(),
        mobilePhone = mobilePhone.trim(),
        workPhone = workPhone.trim(),
        faxPhone = faxPhone.trim(),
        emails = emailsText
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() },
        website = website.trim(),
        address = address.trim(),
        notes = notes.trim(),
        rawText = rawText,
    )
}

data class ReviewUiState(
    val isLoading: Boolean = true,
    val form: CardForm = CardForm(),
    val rawText: String = "",
    val imagePath: String? = null,
    val logoPath: String? = null,
    val logoPreview: ImageBitmap? = null,
    val accounts: List<ContactAccount> = emptyList(),
    val selectedAccountKey: String? = null,
    val isSaving: Boolean = false,
    val savedContactUri: String? = null,
    val error: String? = null,
) {
    val selectedAccount: ContactAccount?
        get() = accounts.firstOrNull { it.key == selectedAccountKey }
}

class ReviewViewModel(
    private val session: ScanSessionRepository,
    private val contacts: ContactsRepository,
    private val accounts: ContactAccountsRepository,
    private val imageProcessor: ImageProcessor,
) : ViewModel() {

    private var loadedFor: String? = null

    private val _ui = MutableStateFlow(ReviewUiState())
    val ui: StateFlow<ReviewUiState> = _ui.asStateFlow()

    /** Carica la sessione corrente una sola volta (le modifiche al form non vengono perse). */
    fun loadIfNeeded() {
        val current = session.current()
        if (current == null) {
            _ui.update { it.copy(isLoading = false) }
            return
        }
        if (loadedFor == current.imagePath) return
        loadedFor = current.imagePath
        _ui.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val logoPreview = current.logoPath
                ?.let { path -> imageProcessor.decode(File(path), PREVIEW_SIZE) }
                ?.asImageBitmap()
            _ui.update {
                it.copy(
                    isLoading = false,
                    form = current.card.toForm(),
                    rawText = current.card.rawText,
                    imagePath = current.imagePath,
                    logoPath = current.logoPath,
                    logoPreview = logoPreview,
                    savedContactUri = null,
                    error = null,
                )
            }
        }
    }

    fun loadAccounts() {
        viewModelScope.launch {
            val list = accounts.loadAccounts()
            _ui.update { state ->
                val key = state.selectedAccountKey
                    ?.takeIf { selected -> list.any { it.key == selected } }
                    ?: list.lastOrNull()?.key
                state.copy(accounts = list, selectedAccountKey = key)
            }
        }
    }

    fun selectAccount(account: ContactAccount) {
        _ui.update { it.copy(selectedAccountKey = account.key) }
    }

    fun updateForm(transform: (CardForm) -> CardForm) {
        _ui.update { it.copy(form = transform(it.form)) }
    }

    fun onLogoCropped(context: Context, bitmap: Bitmap) {
        viewModelScope.launch {
            val file = imageProcessor.saveLogo(context, bitmap)
            if (file == null) {
                _ui.update { it.copy(error = "Impossibile salvare il logo ritagliato.") }
                return@launch
            }
            session.setLogo(file.absolutePath)
            val preview = imageProcessor.decode(file, PREVIEW_SIZE)?.asImageBitmap()
            _ui.update { it.copy(logoPath = file.absolutePath, logoPreview = preview, error = null) }
        }
    }

    fun removeLogo() {
        session.setLogo(null)
        _ui.update { it.copy(logoPath = null, logoPreview = null) }
    }

    fun save() {
        val state = _ui.value
        if (state.isSaving) return
        val card = state.form.toCard(state.rawText)
        if (card.fullName.isBlank() && card.company.isBlank() && card.emails.isEmpty()) {
            _ui.update { it.copy(error = "Inserisci almeno un nome, un'azienda o un'email.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(isSaving = true, error = null) }
            val photo = state.logoPath?.let { path ->
                val bitmap = imageProcessor.decode(File(path), LOGO_CONTACT_DECODE)
                if (bitmap == null) {
                    null
                } else {
                    imageProcessor.compressForContact(bitmap).also { bitmap.recycle() }
                }
            }
            val account = state.selectedAccount ?: ContactAccount(null, null)

            contacts.saveContact(card, photo, account)
                .onSuccess { uri ->
                    session.updateCard(card)
                    _ui.update { it.copy(isSaving = false, savedContactUri = uri.toString()) }
                }
                .onFailure { throwable ->
                    _ui.update {
                        it.copy(
                            isSaving = false,
                            error = throwable.message ?: "Salvataggio non riuscito.",
                        )
                    }
                }
        }
    }

    fun dismissResult() {
        _ui.update { it.copy(savedContactUri = null) }
    }

    fun dismissError() {
        _ui.update { it.copy(error = null) }
    }

    fun reset() {
        loadedFor = null
        _ui.value = ReviewUiState(isLoading = false)
    }

    private fun BusinessCardData.toForm(): CardForm {
        val notesWithRaw = buildString {
            if (notes.isNotBlank()) append(notes.trim())
            if (rawText.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("--- Testo OCR ---\n")
                append(rawText.trim())
            }
        }
        return CardForm(
            namePrefix = namePrefix,
            firstName = firstName,
            lastName = lastName,
            company = company,
            jobTitle = jobTitle,
            mobilePhone = mobilePhone,
            workPhone = workPhone,
            faxPhone = faxPhone,
            emailsText = emails.joinToString("\n"),
            website = website,
            address = address,
            notes = notesWithRaw,
        )
    }

    companion object {
        private const val PREVIEW_SIZE = 512
        private const val LOGO_CONTACT_DECODE = 1024

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReviewViewModel(
                    session = ServiceLocator.scanSession,
                    contacts = ServiceLocator.contactsRepository,
                    accounts = ServiceLocator.accountsRepository,
                    imageProcessor = ServiceLocator.imageProcessor,
                )
            }
        }
    }
}
