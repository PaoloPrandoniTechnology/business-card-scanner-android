# Business Card Scanner — Android

App Android nativa (Kotlin + Jetpack Compose) per digitalizzare i biglietti da visita:
scatti una foto (o importi un'immagine dalla galleria), il testo viene letto **interamente sul
dispositivo**, i dati vengono classificati automaticamente, puoi correggerli, ritagliare il logo
dal biglietto e salvare il contatto nella rubrica scegliendo l'account di destinazione.

Nessun server, nessun account cloud dell'app, nessun dato del biglietto inviato in rete.

---

## Funzionalità

- **Acquisizione** — fotocamera CameraX con anteprima live, tap-to-focus, toggle flash e cornice
  guida per allineare il biglietto; in alternativa import da galleria (`PickVisualMedia`).
  L'orientamento EXIF viene corretto automaticamente.
- **OCR offline** — Google ML Kit Text Recognition v2 con **modello incluso nell'APK**: il
  riconoscimento funziona in aereo mode, senza download successivi.
- **Estrazione dati** — parser euristico italiano/inglese che riconosce:
  nome e cognome (con titoli: Ing., Dott., Avv., Arch., Prof., CEO, Founder…), azienda/ragione
  sociale, ruolo, telefono cellulare / ufficio / fax (prefissi internazionali come `+39`
  preservati), una o più email, sito web, indirizzo completo (via, CAP, città, provincia, nazione)
  e note extra (P.IVA, codice fiscale, righe non classificate).
- **Ritaglio logo** — editor interattivo (Canvas + gesture) con maniglie ridimensionabili,
  trascinamento dell'area e vincolo 1:1. Il box iniziale viene proposto sull'area del biglietto
  dove l'OCR non ha rilevato testo, usando i `boundingBox` di ML Kit.
- **Revisione** — form precompilato e completamente modificabile, con il testo OCR grezzo
  disponibile in fondo alle note per copia-incolla.
- **Salvataggio in rubrica** — scelta dell'account (Google personale/aziendale, Exchange,
  Outlook, CardDAV, memoria locale…) tramite `AccountManager` + `ContactsContract.RawContacts`;
  scrittura atomica con `ContentProviderOperation` su nome, organizzazione, telefoni, email, sito,
  indirizzo strutturato, note e foto. Il logo viene compresso (max 512×512, JPEG 90) per rispettare
  i limiti dei BLOB della rubrica.
- **Tema chiaro/scuro**, Material 3, edge-to-edge.

## Flusso

```
Home ──"Scansiona Biglietto"──▶ Camera (CameraX) ──foto──┐
  └──"Importa da Galleria"──(PickVisualMedia)───────────┤
                                                        ▼
                              ImageProcessor (EXIF corretto, JPEG in cache)
                                                        ▼
                              ML Kit OCR  (on-device, offline)
                                                        ▼
                              BusinessCardParser (regex + euristiche + dizionari)
                                                        ▼
        Revisione dati ◀──▶ Ritaglio logo (Canvas + gesture)
                │
                ▼
        ContactsRepository → rubrica Android (account scelto)
                │
                ▼
        "Contatto salvato" → Apri contatto / Nuovo biglietto
```

## Stack

| Ambito | Tecnologia |
|---|---|
| Linguaggio | Kotlin 2.0.21, Coroutines + Flow |
| UI | Jetpack Compose (BOM 2024.10.01), Material 3 |
| Architettura | MVVM + Clean Architecture leggera, stato unidirezionale (`StateFlow`) |
| Fotocamera | CameraX 1.4.0 |
| OCR | `com.google.mlkit:text-recognition` 16.0.1 (bundled) |
| Rubrica | `ContactsContract` + `ContentProviderOperation` |
| Build | Gradle 8.9, AGP 8.7.2, Version Catalog (`gradle/libs.versions.toml`) |

`minSdk 26` · `targetSdk 35` · `compileSdk 35` · JDK 17+

## Build

Il progetto usa un Android SDK locale nella cartella `.androidsdk/` (non versionata) e
`local.properties` punta già lì. Con un SDK di sistema basta rimuovere/aggiornare quella riga.

```bash
export ANDROID_HOME="$PWD/.androidsdk"

./gradlew assembleDebug     # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # su dispositivo/emulatore collegato
```

Installazione manuale dell'APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requisiti: JDK 17 o superiore, Android SDK Platform 35 e Build-Tools 35.0.0.
Il progetto si apre anche direttamente in Android Studio.

## Permessi

| Permesso | Perché |
|---|---|
| `CAMERA` | scattare la foto del biglietto |
| `READ_CONTACTS` | elencare gli account/rubriche disponibili |
| `WRITE_CONTACTS` | salvare il contatto revisionato |
| `GET_ACCOUNTS` | leggere via `AccountManager` gli account che supportano i contatti |

Tutti i permessi sono richiesti a runtime con spiegazione; in caso di rifiuto definitivo l'app
rimanda alle impostazioni di sistema. Senza permessi rubrica resta disponibile il salvataggio
locale sul dispositivo.

## Privacy

- L'OCR gira **on-device** con il modello incluso nell'APK: il testo del biglietto non viene
  trasmesso a nessun servizio.
- Il progetto non contiene client HTTP né chiamate di rete.
- L'APK dichiara `INTERNET` e `ACCESS_NETWORK_STATE` solo perché li aggiunge una dipendenza
  transitiva di ML Kit (`com.google.android.datatransport`, logging interno dell'SDK).
- Le immagini di lavoro restano nella cache privata dell'app.

## Struttura del progetto

```
app/src/main/java/com/paoloprandoni/businesscard/
├── domain/        modelli, parser (regex/dizionari/euristiche), interfacce repository
├── data/          OCR ML Kit, pipeline immagini, account, scrittura contatti, sessione
├── ui/            Compose: home, camera, revisione, ritaglio logo, tema
└── core/          service locator
```

Dettagli di architettura, dipendenze e regole di sviluppo in [`CLAUDE.md`](CLAUDE.md).

## Convenzioni di sviluppo

- Prima di modificare un file esistente va creato un backup con timestamp:
  `bash tools/backup.sh <file>` → `.backups/<percorso>.<YYYYMMDD_HHMMSS>.bak`
  (`.backups/` è esclusa dal repository).
- `CLAUDE.md` va aggiornato a ogni milestone o modifica strutturale.
- `./gradlew assembleDebug` deve restare verde prima di ogni commit.

## Limiti noti

- Il parser è euristico: biglietti molto grafici, con più colonne o font decorativi possono
  richiedere correzioni manuali (la schermata di revisione è interamente editabile).
- L'APK pesa circa 60 MB perché include il modello OCR: nessun download a runtime.
- Non è prevista sincronizzazione o cronologia delle scansioni: ogni biglietto è una sessione.
