# Business Card Scanner — Android (CLAUDE.md)

Applicazione Android nativa per digitalizzare biglietti da visita: foto (o import da galleria),
OCR **100% offline**, ritaglio del logo, revisione dei dati e salvataggio nella rubrica Android
scegliendo l'account di destinazione.

---

## 1. Stato del progetto

| Milestone | Stato |
|---|---|
| M0 — Scaffolding Gradle / Version Catalog / manifest / risorse | ✅ completata |
| M1 — Modelli di dominio + `BusinessCardParser` (regex, euristiche, dizionari IT/EN) | ✅ completata |
| M2 — OCR locale ML Kit Text Recognition v2 (modello bundled) | ✅ completata |
| M3 — Acquisizione: CameraX (preview, tap-to-focus, flash, cornice guida) + galleria | ✅ completata |
| M4 — Ritaglio logo interattivo (Canvas + gesture, maniglie, 1:1, pre-posizionamento su area senza testo) | ✅ completata |
| M5 — Revisione dati (form editabile) + selettore account/rubrica (`AccountManager` + `RawContacts`) | ✅ completata |
| M6 — Salvataggio contatto (`ContentProviderOperation` batch, photo blob compressa) | ✅ completata |
| M7 — Build `assembleDebug` + APK | ✅ completata |
| M8 — Repository GitHub privata (commit + push) | ✅ completata |
| M9 — Release alpha `v0.1.0-alpha` con APK allegato | ✅ completata |

Repository privata: <https://github.com/PaoloPrandoniTechnology/business-card-scanner-android>
(branch `main`, remote `origin`).

Release di prova: `v0.1.0-alpha` (prerelease) con APK debug allegato —
<https://github.com/PaoloPrandoniTechnology/business-card-scanner-android/releases/tag/v0.1.0-alpha>.
Gli APK non sono versionati nel repository (`*.apk` in `.gitignore`), vengono solo allegati alle release.

Ultima verifica: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**, APK in
`app/build/outputs/apk/debug/app-debug.apk` (~60 MB, include il modello OCR bundled).
`aapt2 dump badging` conferma: `package com.paoloprandoni.businesscard`, versione `1.0.0`,
`minSdkVersion 26`, `targetSdkVersion 35`, `compileSdkVersion 35`, permessi `CAMERA`,
`READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`.

---

## 2. Stack tecnologico

- **Linguaggio:** Kotlin 2.0.21, 100% Kotlin, Coroutines + Flow (`StateFlow`).
- **UI:** Jetpack Compose (BOM 2024.10.01) + Material Design 3, tema chiaro/scuro, Material You opzionale disattivato
  (palette brand fissa), edge-to-edge.
- **Architettura:** MVVM + Clean Architecture leggera, stato unidirezionale (UDF):
  `Screen → evento → ViewModel → StateFlow<UiState> → Screen`.
- **Fotocamera:** CameraX 1.4.0 (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`).
- **OCR:** Google ML Kit `com.google.mlkit:text-recognition` 16.0.1 — **modello bundled on-device**:
  il riconoscimento funziona senza rete e i dati del biglietto non vengono mai trasmessi.
  Nota: la dipendenza transitiva `com.google.android.datatransport:transport-backend-cct`
  (logging interno di ML Kit) aggiunge `INTERNET` e `ACCESS_NETWORK_STATE` al manifest finale;
  il codice dell'app non contiene **nessuna** richiesta HTTP e non ha client di rete.
- **Rubrica:** `ContactsContract` + `ContentProviderOperation` in batch; account via `AccountManager`
  e `ContactsContract.RawContacts`.
- **Build:** AGP 8.7.2, Gradle 8.9 (wrapper), JDK 17 target, `compileSdk`/`targetSdk` 35, `minSdk` 26.

### Dipendenze principali (Version Catalog: `gradle/libs.versions.toml`)

| Alias | Modulo |
|---|---|
| `androidx-core-ktx` | androidx.core:core-ktx:1.13.1 |
| `androidx-activity-compose` | androidx.activity:activity-compose:1.9.3 |
| `androidx-lifecycle-*` | lifecycle-runtime-ktx / runtime-compose / viewmodel-compose 2.8.7 |
| `androidx-compose-bom` | androidx.compose:compose-bom:2024.10.01 |
| `androidx-compose-material3` | androidx.compose.material3:material3 |
| `androidx-compose-material-icons-extended` | material-icons-extended |
| `androidx-camera-*` | camera-core / camera-camera2 / camera-lifecycle / camera-view 1.4.0 |
| `mlkit-text-recognition` | com.google.mlkit:text-recognition:16.0.1 (bundled) |
| `androidx-exifinterface` | androidx.exifinterface:exifinterface:1.3.7 |
| `kotlinx-coroutines-android` | 1.9.0 |

Nessuna libreria di terze parti oltre a Google/AndroidX.

---

## 3. Struttura delle cartelle

```
.
├── CLAUDE.md                     # questo file (regole + architettura + stato)
├── README.md                     # presentazione del progetto (visibile sulla repo GitHub)
├── .gitignore                    # esclude build/, .gradle/, .backups/, .androidsdk/, local.properties
├── settings.gradle.kts           # repo google()/mavenCentral(), inclusione :app
├── build.gradle.kts              # plugin dichiarati apply false
├── gradle.properties
├── gradle/libs.versions.toml     # Version Catalog
├── gradlew / gradlew.bat / gradle/wrapper/
├── tools/backup.sh               # backup pre-modifica in .backups/ (regola obbligatoria)
├── .backups/                     # backup con timestamp (NON versionato)
├── .androidsdk/                  # Android SDK locale usato per la build (NON versionato)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/paoloprandoni/businesscard/
        │   ├── BusinessCardApplication.kt
        │   ├── MainActivity.kt
        │   ├── core/ServiceLocator.kt
        │   ├── domain/
        │   │   ├── model/BusinessCardData.kt      # modello contatto + ContactAccount
        │   │   ├── model/OcrResult.kt             # astrazione output OCR (linee + boundingBox)
        │   │   ├── ocr/OcrEngine.kt               # interfaccia motore OCR
        │   │   ├── parser/BusinessCardParser.kt   # regex + euristiche + dizionari IT/EN
        │   │   ├── parser/AddressParser.kt        # indirizzo → parti StructuredPostal
        │   │   ├── parser/LogoRegionSuggester.kt  # box iniziale su area senza testo
        │   │   └── repository/                    # interfacce (Contacts, ScanSession)
        │   ├── data/
        │   │   ├── ocr/MlKitOcrEngine.kt
        │   │   ├── image/ImageProcessor.kt        # EXIF, decode campionato, crop, JPEG
        │   │   ├── contacts/ContactsRepositoryImpl.kt
        │   │   ├── contacts/ContactAccountsRepository.kt
        │   │   └── session/InMemoryScanSessionRepository.kt
        │   └── ui/
        │       ├── AppRoot.kt                     # navigazione a stati + permission gate
        │       ├── scan/{ScanViewModel,HomeScreen}.kt
        │       ├── camera/CameraScreen.kt
        │       ├── review/{ReviewViewModel,ReviewScreen}.kt
        │       ├── crop/LogoCropScreen.kt
        │       ├── components/PermissionDialogs.kt
        │       └── theme/{Color,Theme,Type}.kt
        └── res/
            ├── values/{strings,colors,themes}.xml
            ├── values-night/themes.xml
            ├── drawable/ic_launcher_foreground.xml
            └── mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml
```

---

## 4. Architettura e flusso dati

```
Home ──"Scansiona"──▶ Camera (CameraX) ──foto──┐
  └──"Importa da Galleria"──(PickVisualMedia)──┤
                                               ▼
                            ImageProcessor.importImage()
                       (copia in cache, EXIF corretto, JPEG)
                                               ▼
                       MlKitOcrEngine.recognize(bitmap)  [offline]
                                               ▼
                       BusinessCardParser.parse(OcrResult)
                                               ▼
                       ScanSessionRepository (immagine + dati + logo)
                                               ▼
                 ReviewScreen ◀──▶ LogoCropScreen (Canvas + gesture)
                       │
                       ▼
                 ContactsRepository.saveContact(card, photo, account)
                       │  ContentProviderOperation batch su RawContacts/Data
                       ▼
                 Dialog "Contatto salvato" → Apri contatto / Nuovo biglietto
```

### Dettagli chiave

- **`ScanSessionRepository`** (in memoria, singleton) disaccoppia la scansione dalla revisione:
  contiene `imagePath` (file cache), `BusinessCardData` parsato e `logoPath` opzionale.
  Nessun `Bitmap` passa tra schermate o tra processi → niente `TransactionTooLargeException`.
- **`ImageProcessor`** normalizza subito l'orientamento EXIF e salva un JPEG upright: tutti i
  consumatori (OCR, crop, anteprima) lavorano su coordinate coerenti.
- **`BusinessCardParser`** combina: regex (email, URL, telefoni, CAP/P.IVA/CF), dizionari di parole
  chiave IT/EN (ruoli, suffissi societari, titoli, vie), euristica dimensionale (altezza dei
  `boundingBox` come proxy della dimensione del font) ed euristica posizionale (righe in alto per
  il nome). Ogni riga non classificata finisce nelle note: nessuna informazione viene persa.
- **`LogoRegionSuggester`** costruisce una griglia di occupazione dai `boundingBox` OCR e propone
  come box iniziale di ritaglio la zona quadrata con minore sovrapposizione di testo.
- **Salvataggio contatti:** un'unica `applyBatch` con back-reference al `RawContacts` appena
  inserito; l'account scelto viene scritto in `ACCOUNT_NAME`/`ACCOUNT_TYPE` (+ `DATA_SET` se
  presente) oppure `null`/`null` per il salvataggio locale sul dispositivo.
  La foto è ridimensionata a max **512×512 px**, JPEG **qualità 90**, con riduzione adattiva della
  qualità sotto ~900 KB.

---

## 5. Regole di sviluppo (obbligatorie)

1. **Backup prima di ogni modifica a file esistenti**
   ```bash
   bash tools/backup.sh app/src/main/java/.../MainActivity.kt
   # → .backups/app/src/main/java/.../MainActivity.kt.20261118_164500.bak
   ```
   Vale per QUALSIASI file già esistente: `CLAUDE.md`, Gradle, Kotlin, XML, manifest.
   `.backups/` è in `.gitignore` e non va mai committato.

2. **`CLAUDE.md` va aggiornato** a ogni milestone, nuova funzionalità o modifica strutturale
   (architettura, dipendenze, struttura cartelle, stato).

3. **Kotlin only**, Coroutines/Flow, MVVM + UDF. Nessun accesso di rete: l'OCR è on-device e
   l'app non dichiara `INTERNET`.

4. **Compose + Material 3**, tema chiaro/scuro, edge-to-edge, nessuna View XML (solo `PreviewView`
   CameraX via `AndroidView`).

5. **Permessi runtime** (`CAMERA`, `READ_CONTACTS`, `WRITE_CONTACTS`, `GET_ACCOUNTS`) sempre
   spiegati all'utente, con rimando alle impostazioni di sistema in caso di rifiuto definitivo.
   `GET_ACCOUNTS` serve solo a leggere via `AccountManager` gli account che supportano i contatti;
   senza permesso l'app mostra comunque gli account ricavati da `RawContacts` e la memoria locale.

6. **Contatti:** salvataggio esclusivamente via `ContentProviderOperation` in batch; nessuna
   scrittura diretta su DB. Ogni `Data` usa la back-reference al `RawContacts` (indice 0).

7. **Verifica:** `./gradlew assembleDebug` deve restare verde prima di ogni commit.

8. **Commit:** messaggi in italiano, imperativi e sintetici; `main` come branch principale.

---

## 6. Build & run

```bash
# SDK locale (già presente in .androidsdk/, path scritto in local.properties)
export ANDROID_HOME="$PWD/.androidsdk"

./gradlew assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # su device/emulatore collegato
```

Requisiti: JDK 17+ (testato con JDK 21), Android SDK platform 35 + build-tools 35.0.0
(l'installazione dell'SDK avviene con gli command-line tools scaricati in `.androidsdk/`).

### Verifiche eseguite

- `./gradlew assembleDebug` → BUILD SUCCESSFUL, APK generato e ispezionato con `aapt2 dump badging`.
- Estrazione dati validata su un biglietto di esempio (nome + titolo, azienda, ruolo, cellulare,
  ufficio, fax, email, sito, indirizzo, P.IVA nelle note): tutti i campi classificati correttamente.
- Non verificato: esecuzione su dispositivo/emulatore (nessun device collegato durante la build),
  quindi UI, permessi runtime e salvataggio reale in rubrica sono da provare sul telefono.

---

## 7. Note tecniche / limiti noti

- `minSdk 26`: adaptive icon, `PickVisualMedia`, `ExposedDropdownMenuBox` e CameraX sono supportati
  senza fallback legacy.
- Il parser è euristico: biglietti molto grafici o con testo su più colonne possono richiedere
  correzioni manuali — per questo la schermata di revisione è completamente editabile e il testo
  OCR grezzo resta sempre disponibile nel campo Note.
- La build `release` non ha minify attivo (nessuna regola ProGuard necessaria per ML Kit/CameraX);
  il deliverable richiesto è `assembleDebug`.
- L'app è in italiano; le stringhe dell'interfaccia sono in italiano, `app_name` e i testi dei
  permessi sono in `res/values/strings.xml`.
