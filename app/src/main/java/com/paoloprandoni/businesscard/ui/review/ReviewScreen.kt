package com.paoloprandoni.businesscard.ui.review

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paoloprandoni.businesscard.domain.model.ContactAccount
import com.paoloprandoni.businesscard.ui.components.PermissionExplanationDialog
import com.paoloprandoni.businesscard.ui.components.findActivity
import com.paoloprandoni.businesscard.ui.components.hasPermission
import com.paoloprandoni.businesscard.ui.components.openAppSettings

private val CONTACTS_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.GET_ACCOUNTS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onCropLogo: () -> Unit,
    onNewScan: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }

    var hasReadContacts by remember { mutableStateOf(context.hasPermission(Manifest.permission.READ_CONTACTS)) }
    var hasWriteContacts by remember { mutableStateOf(context.hasPermission(Manifest.permission.WRITE_CONTACTS)) }
    var pendingSave by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasReadContacts = context.hasPermission(Manifest.permission.READ_CONTACTS)
        hasWriteContacts = context.hasPermission(Manifest.permission.WRITE_CONTACTS)
        viewModel.loadAccounts()
        if (!hasReadContacts || !hasWriteContacts) showPermissionDialog = true
        if (pendingSave) {
            pendingSave = false
            if (hasWriteContacts) viewModel.save()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        if (hasReadContacts) {
            viewModel.loadAccounts()
        } else {
            contactsLauncher.launch(CONTACTS_PERMISSIONS)
        }
    }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    val requestSave = {
        if (hasWriteContacts) {
            viewModel.save()
        } else {
            pendingSave = true
            contactsLauncher.launch(CONTACTS_PERMISSIONS)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Revisione dati") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = requestSave,
                        enabled = !state.isSaving && !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Salva in rubrica", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard(title = "Logo / foto contatto") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            val preview = state.logoPreview
                            if (preview != null) {
                                Image(
                                    bitmap = preview,
                                    contentDescription = "Logo del contatto",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = onCropLogo,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Crop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.logoPath == null) "Ritaglia logo" else "Ritaglia di nuovo")
                            }
                            TextButton(
                                onClick = viewModel::removeLogo,
                                enabled = state.logoPath != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rimuovi foto")
                            }
                        }
                    }
                    Text(
                        text = "Ritaglia dal biglietto il logo aziendale (o il volto) da usare come foto del contatto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "Persona") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FormField(
                            label = "Titolo",
                            value = state.form.namePrefix,
                            onValueChange = { value -> viewModel.updateForm { it.copy(namePrefix = value) } },
                            modifier = Modifier.weight(0.4f),
                        )
                        FormField(
                            label = "Nome",
                            value = state.form.firstName,
                            onValueChange = { value -> viewModel.updateForm { it.copy(firstName = value) } },
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                    FormField(
                        label = "Cognome",
                        value = state.form.lastName,
                        onValueChange = { value -> viewModel.updateForm { it.copy(lastName = value) } },
                    )
                }
            }

            item {
                SectionCard(title = "Azienda") {
                    FormField(
                        label = "Azienda / Ragione sociale",
                        value = state.form.company,
                        onValueChange = { value -> viewModel.updateForm { it.copy(company = value) } },
                    )
                    FormField(
                        label = "Ruolo / Qualifica",
                        value = state.form.jobTitle,
                        onValueChange = { value -> viewModel.updateForm { it.copy(jobTitle = value) } },
                    )
                }
            }

            item {
                SectionCard(title = "Contatti") {
                    FormField(
                        label = "Telefono cellulare",
                        value = state.form.mobilePhone,
                        onValueChange = { value -> viewModel.updateForm { it.copy(mobilePhone = value) } },
                        keyboardType = KeyboardType.Phone,
                    )
                    FormField(
                        label = "Telefono ufficio",
                        value = state.form.workPhone,
                        onValueChange = { value -> viewModel.updateForm { it.copy(workPhone = value) } },
                        keyboardType = KeyboardType.Phone,
                    )
                    FormField(
                        label = "Fax",
                        value = state.form.faxPhone,
                        onValueChange = { value -> viewModel.updateForm { it.copy(faxPhone = value) } },
                        keyboardType = KeyboardType.Phone,
                    )
                    FormField(
                        label = "Email",
                        value = state.form.emailsText,
                        onValueChange = { value -> viewModel.updateForm { it.copy(emailsText = value) } },
                        keyboardType = KeyboardType.Email,
                        singleLine = false,
                        minLines = 1,
                        supportingText = "Una email per riga",
                    )
                    FormField(
                        label = "Sito web",
                        value = state.form.website,
                        onValueChange = { value -> viewModel.updateForm { it.copy(website = value) } },
                        keyboardType = KeyboardType.Uri,
                    )
                }
            }

            item {
                SectionCard(title = "Indirizzo") {
                    FormField(
                        label = "Indirizzo completo",
                        value = state.form.address,
                        onValueChange = { value -> viewModel.updateForm { it.copy(address = value) } },
                        singleLine = false,
                        minLines = 2,
                    )
                }
            }

            item {
                SectionCard(
                    title = "Note",
                    subtitle = "Include le righe non riconosciute e il testo OCR grezzo in fondo.",
                ) {
                    FormField(
                        label = "Note",
                        value = state.form.notes,
                        onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
                        singleLine = false,
                        minLines = 6,
                    )
                }
            }

            item {
                SectionCard(title = "Rubrica di destinazione") {
                    AccountSelector(
                        accounts = state.accounts,
                        selectedKey = state.selectedAccountKey,
                        onSelect = viewModel::selectAccount,
                    )
                    Text(
                        text = if (hasReadContacts) {
                            "Scegli l'account su cui salvare il contatto."
                        } else {
                            "Senza accesso alla rubrica è disponibile solo il salvataggio locale sul telefono."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                TextButton(
                    onClick = onNewScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scarta e scansiona un nuovo biglietto")
                }
            }
        }
    }

    state.savedContactUri?.let { uri ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = { Text("Contatto salvato") },
            text = { Text("Il contatto è stato aggiunto alla rubrica selezionata.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissResult()
                    openContact(context, uri)
                }) { Text("Apri contatto") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissResult()
                    onNewScan()
                }) { Text("Nuovo biglietto") }
            },
        )
    }

    if (showPermissionDialog) {
        PermissionExplanationDialog(
            title = "Accesso alla rubrica",
            message = "Per salvare il contatto serve il permesso di lettura e scrittura della rubrica. " +
                "Puoi concederlo dalle impostazioni dell'app; senza permesso resta disponibile " +
                "solo la memoria locale.",
            permanentlyDenied = activity?.let {
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.WRITE_CONTACTS,
                )
            } ?: false,
            onRequest = {
                showPermissionDialog = false
                contactsLauncher.launch(CONTACTS_PERMISSIONS)
            },
            onOpenSettings = {
                showPermissionDialog = false
                context.openAppSettings()
            },
            onDismiss = { showPermissionDialog = false },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        supportingText = supportingText?.let { text -> { Text(text) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelector(
    accounts: List<ContactAccount>,
    selectedKey: String?,
    onSelect: (ContactAccount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.key == selectedKey } ?: accounts.lastOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.displayLabel() ?: "Telefono (solo locale)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Account") },
            leadingIcon = {
                Icon(
                    imageVector = if (selected?.isLocal != false) {
                        Icons.Outlined.Smartphone
                    } else {
                        Icons.Outlined.AccountCircle
                    },
                    contentDescription = null,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.displayLabel()) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (account.isLocal) {
                                Icons.Outlined.Smartphone
                            } else {
                                Icons.Outlined.AccountCircle
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onSelect(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ContactAccount.displayLabel(): String {
    if (isLocal) return "Telefono (solo locale)"
    val provider = when {
        accountType?.contains("google", ignoreCase = true) == true -> "Google"
        accountType?.contains("exchange", ignoreCase = true) == true -> "Exchange"
        accountType?.contains("outlook", ignoreCase = true) == true -> "Outlook"
        accountType?.contains("microsoft", ignoreCase = true) == true -> "Microsoft"
        accountType?.contains("icloud", ignoreCase = true) == true -> "iCloud"
        accountType?.contains("carddav", ignoreCase = true) == true ||
            accountType?.contains("dav", ignoreCase = true) == true -> "CardDAV"
        !accountType.isNullOrBlank() -> accountType.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        else -> "Account"
    }
    return "${accountName ?: "Account"} ($provider)"
}

private fun openContact(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "Nessuna app Contatti disponibile", Toast.LENGTH_SHORT).show()
        }
}
