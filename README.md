# Business Card Scanner — Android

App Android nativa in **Kotlin + Jetpack Compose** per digitalizzare i biglietti da visita: scatti
una foto (o importi un'immagine dalla galleria), il testo viene letto **interamente sul
dispositivo**, i dati vengono classificati automaticamente, puoi correggerli, ritagliare il logo
dal biglietto e salvare il contatto nella rubrica scegliendo l'account di destinazione.

Nessun server, nessun account dell'app, nessun dato del biglietto inviato in rete.

| | |
|---|---|
| **Versione** | `0.1.1-alpha` (pre-release, APK di debug) |
| **Requisiti** | Android 8.0+ (`minSdk 26`) · `targetSdk 35` |
| **APK** | ~59 MB (modello OCR incluso, nessun download a runtime) |
| **Lingua UI** | Italiano |
| **Stato** | Alpha — flusso completo funzionante e verificato su emulatore API 34 |

---

## Indice

- [Screenshot](#screenshot)
- [Funzionalità](#funzionalità)
- [Come funziona](#come-funziona)
- [Installazione](#installazione)
- [Build da sorgente](#build-da-sorgente)
- [Test](#test)
- [Permessi](#permessi)
- [Privacy](#privacy)
- [Architettura](#architettura)
- [Cosa viene scritto in rubrica](#cosa-viene-scritto-in-rubrica)
- [Stato, verifiche e roadmap](#stato-verifiche-e-roadmap)
- [Changelog](#changelog)
- [Convenzioni di sviluppo](#convenzioni-di-sviluppo)
- [Limiti noti](#limiti-noti)

---

## Screenshot

| Home | Fotocamera | Revisione dati |
|:---:|:---:|:---:|
| <img src="docs/screenshots/01-home.png" width="240" alt="Schermata iniziale"> | <img src="docs/screenshots/02-camera.png" width="240" alt="Fotocamera con cornice guida"> | <img src="docs/screenshots/03-review.png" width="240" alt="Revisione dei dati estratti"> |
| **Ritaglio logo** | **Contatto salvato** | |
| <img src="docs/screenshots/04-crop.jpg" width="240" alt="Ritaglio interattivo del logo"> | <img src="docs/screenshots/05-saved.png" width="240" alt="Conferma salvataggio in rubrica"> | |

> Screenshot catturati dall'emulatore Android 14 (API 34); l'anteprima della fotocamera mostra la
> scena virtuale dell'emulatore.

---

## Funzionalità

- **Acquisizione** — fotocamera CameraX con anteprima live, tap-to-focus (AF+AE), toggle flash e
  cornice guida per allineare il biglietto; in alternativa import dalla galleria
  (`PickVisualMedia`). L'orientamento EXIF viene corretto e l'immagine normalizzata subito.
- **OCR offline** — Google ML Kit Text Recognition v2 con **modello incluso nell'APK**: funziona in
  modalità aereo e senza download successivi.
- **Estrazione automatica** — nome e cognome (con titoli come Ing., Dott., Avv., Arch., Prof.,
  CEO, Founder…), azienda/ragione sociale, ruolo, telefono cellulare / ufficio / fax (prefissi
  internazionali come `+39` preservati), una o più email, sito web, indirizzo completo e note
  extra (P.IVA, codice fiscale, righe non riconosciute).
- **Ritaglio logo** — editor interattivo (Canvas + gesture) con maniglie ridimensionabili,
  trascinamento dell'area, griglia dei terzi e vincolo 1:1. Il box iniziale viene proposto
  **dentro il biglietto**, attorno alla riga del brand, e come fallback sull'area con meno testo
  (usando i `boundingBox` di ML Kit).
- **Revisione** — form precompilato e interamente modificabile; il testo OCR grezzo resta in fondo
  al campo Note per copia-incolla rapido.
- **Scelta della rubrica** — dropdown con gli account del dispositivo (`AccountManager` +
  `ContactsContract.RawContacts`): Google personale/aziendale, Exchange/Outlook, CardDAV e
  memoria locale.
- **Salvataggio atomico** — un'unica `applyBatch` su `RawContacts` + `Data`, con foto compressa
  entro i limiti del database contatti.
- **Tema chiaro/scuro**, Material 3, edge-to-edge, feedback con dialog "Apri contatto / Nuovo
  biglietto".

---

## Come funziona

```
Home ──"Scansiona Biglietto"──▶ Camera (CameraX) ──foto──┐
  └──"Importa da Galleria"──(PickVisualMedia)────────────┤
                                                         ▼
                        ImageProcessor: copia in cache + correzione EXIF
                                                         ▼
                        ML Kit Text Recognition (on-device, offline)
                                                         ▼
                        BusinessCardParser: regex + euristiche + dizionari IT/EN
                                                         ▼
     Revisione dati ◀──▶ Ritaglio logo (Canvas + gesture, 1:1)
             │
             ▼
     ContactsRepository → rubrica Android (account scelto)
             │
             ▼
     "Contatto salvato" → Apri contatto / Nuovo biglietto
```

### Il parser in breve

`BusinessCardParser` lavora sulle righe OCR e combina tre livelli:

1. **Regex** — email (anche `nome (at) dominio`), URL con TLD riconosciuto, numeri di telefono
   (6–15 cifre), CAP, partita IVA/codice fiscale.
2. **Dizionari IT/EN** — ruoli e qualifiche, marchi societari (`Srl`, `S.p.A.`, `GmbH`, `Ltd`…),
   vie (`Via`, `Piazza`, `Street`…), titoli onorifici, nomi propri comuni.
3. **Euristiche** — altezza dei `boundingBox` come proxy della dimensione del font, posizione
   della riga (il nome è spesso in alto e con font grande) e riconoscimento del payoff in
   maiuscolo per non confonderlo con un nome.

Regole utili emerse dai biglietti reali:

- un secondo numero fisso **senza etichetta** viene assegnato al fax (spesso la "F" iniziale non
  viene letta dall'OCR);
- le etichette compatte `T`, `M`, `F`, `W`, `E` vengono riconosciute;
- P.IVA, codice fiscale, REA e IBAN non vengono mai interpretati come telefoni;
- tutto ciò che non viene classificato finisce nelle **note**, insieme al testo OCR grezzo:
  nessuna informazione va persa.

---

## Installazione

### Dall'APK pubblicato

1. Apri la pagina delle release:
   <https://github.com/PaoloPrandoniTechnology/business-card-scanner-android/releases>
2. Scarica `business-card-scanner-<versione>-alpha.apk`.
3. Apri il file sul telefono e consenti l'installazione da questa origine
   (Impostazioni → App → Accesso speciale → Installa app sconosciute).
4. Avvia **Biglietti da Visita** e concedi i permessi fotocamera e rubrica.

> La repository è **privata**: il download richiede l'accesso a GitHub con l'account proprietario.
> L'APK è firmato con la chiave di debug, quindi Play Protect può mostrare un avviso.

### Da computer, via USB

```bash
adb install -r business-card-scanner-0.1.1-alpha.apk
```

---

## Build da sorgente

Requisiti: **JDK 17+** (testato con JDK 21), Android SDK Platform 35 e Build-Tools 35.0.0.

```bash
export ANDROID_HOME="$PWD/.androidsdk"   # SDK locale del progetto (cartella non versionata)
./gradlew assembleDebug                  # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                   # su dispositivo/emulatore collegato
```

Il progetto si apre direttamente in Android Studio: `local.properties` punta già all'SDK locale;
con un SDK di sistema basta aggiornare quella riga.

Versioni: Gradle 8.9 · AGP 8.7.2 · Kotlin 2.0.21 · Compose BOM 2024.10.01 · CameraX 1.4.0 ·
ML Kit Text Recognition 16.0.1 · Coroutines 1.9.0. Le dipendenze sono centralizzate nel Version
Catalog `gradle/libs.versions.toml`.

---

## Test

Test strumentato che riproduce la pipeline reale (import immagine → OCR → parsing) su un
biglietto di esempio incluso negli asset:

```bash
./gradlew connectedDebugAndroidTest     # richiede un device/emulatore collegato
```

Il test verifica nome, cognome, azienda, cellulare/ufficio/fax, email, sito e indirizzo estratti
dal biglietto `app/src/androidTest/assets/sample_card.webp`.

---

## Permessi

| Permesso | Perché |
|---|---|
| `CAMERA` | scattare la foto del biglietto |
| `READ_CONTACTS` | elencare le rubriche disponibili |
| `WRITE_CONTACTS` | salvare il contatto revisionato |
| `GET_ACCOUNTS` | leggere via `AccountManager` gli account che supportano i contatti |

Tutti i permessi sono richiesti a runtime con una spiegazione; in caso di rifiuto definitivo
l'app rimanda alle impostazioni di sistema. Senza permessi rubrica resta disponibile il
salvataggio locale sul dispositivo.

---

## Privacy

- L'OCR gira **on-device** con il modello incluso nell'APK: il testo del biglietto non viene
  trasmesso a nessun servizio.
- Il codice dell'app non contiene client HTTP né chiamate di rete.
- L'APK dichiara `INTERNET` e `ACCESS_NETWORK_STATE` solo perché li aggiunge una dipendenza
  transitiva di ML Kit (`com.google.android.datatransport`, logging interno dell'SDK).
- Le immagini di lavoro restano nella cache privata dell'app e non vengono condivise.

---

## Architettura

MVVM con Clean Architecture leggera e stato unidirezionale:

```
Screen (Compose) ──evento──▶ ViewModel ──StateFlow──▶ Screen
                                  │
                                  ▼
                    domain (modelli, parser, repository)
                                  ▲
                                  ▼
                    data (ML Kit, immagini, contatti, sessione)
```

| Livello | Contenuto |
|---|---|
| `domain` | `BusinessCardData`, `OcrResult`, `BusinessCardParser`, `AddressParser`, `LogoRegionSuggester`, interfacce repository |
| `data` | `MlKitOcrEngine`, `ImageProcessor`, `ContactsRepositoryImpl`, `ContactAccountsRepository`, `InMemoryScanSessionRepository` |
| `ui` | `AppRoot` (navigazione a stati), `HomeScreen`, `CameraScreen`, `ReviewScreen`, `LogoCropScreen`, tema Material 3 |

Scelte tecniche:

- la sessione di scansione vive in `ScanSessionRepository` (solo percorsi di file in cache): nessun
  `Bitmap` viene passato tra schermate, quindi niente `TransactionTooLargeException`;
- `ImageProcessor` normalizza orientamento EXIF e dimensione in un unico punto;
- `ServiceLocator` fornisce le dipendenze ai ViewModel senza framework DI esterni.

```
app/src/main/java/com/paoloprandoni/businesscard/
├── core/          service locator
├── domain/        modelli, parser (regex/dizionari/euristiche), interfacce repository
├── data/          OCR ML Kit, pipeline immagini, account, scrittura contatti, sessione
└── ui/            Compose: home, camera, revisione, ritaglio logo, tema
```

---

## Cosa viene scritto in rubrica

| Campo dell'app | `ContactsContract` |
|---|---|
| Titolo, Nome, Cognome | `StructuredName` (`PREFIX`, `GIVEN_NAME`, `FAMILY_NAME`) |
| Azienda | `Organization.COMPANY` |
| Ruolo | `Organization.TITLE` |
| Cellulare / Ufficio / Fax | `Phone` con tipo `TYPE_MOBILE` / `TYPE_WORK` / `TYPE_FAX_WORK` |
| Email (una o più) | `Email` tipo `TYPE_WORK` |
| Sito web | `Website` tipo `TYPE_HOMEPAGE` (schema `https://` aggiunto se mancante) |
| Indirizzo | `StructuredPostal` (`STREET`, `CITY`, `POSTCODE`, `REGION`, `COUNTRY` + indirizzo formattato) |
| Note | `Note` (include le righe non classificate e il testo OCR) |
| Logo | `Photo` — max 512×512 px, JPEG qualità 90, con riduzione adattiva sotto ~900 KB |

Il contatto viene creato sull'account scelto (`ACCOUNT_NAME`/`ACCOUNT_TYPE`, più `DATA_SET` se
presente) oppure su `null`/`null` per la memoria locale del telefono.

---

## Stato, verifiche e roadmap

**Verificato** (emulatore Android 14, API 34, 1080×2280, biglietto reale "BIPIESSE"):

- import da galleria → OCR → revisione con dati corretti (nome, cognome, azienda, cellulare,
  ufficio, fax, email, sito, indirizzo);
- ritaglio logo con anteprima nella schermata di revisione;
- salvataggio verificato sul database contatti: 9 righe `Data`, inclusa la foto;
- test strumentato `connectedDebugAndroidTest` verde.

**Non ancora verificato**: fotocamera su hardware reale, salvataggio su account Google/Exchange
(l'emulatore ha solo l'account locale), dispositivi con Android 8–13.

**Idee per le prossime versioni** (non implementate):

- build release firmata e riduzione della dimensione dell'APK;
- condivisione del contatto come vCard e cronologia delle scansioni;
- supporto OCR multilingua (modelli aggiuntivi scaricabili);
- miglioramento del parser su biglietti a due colonne.

---

## Changelog

### v0.1.1-alpha — 2026-11-18

- **Fix**: l'import immagine falliva sempre ("Analisi non riuscita") perché una decodifica di soli
  metadati restituiva `null` e veniva interpretata come errore.
- **Fix**: il secondo numero fisso senza etichetta viene assegnato al fax.
- **Fix**: nome riconosciuto anche nella metà bassa del biglietto, senza confonderlo con il payoff.
- **Fix**: il box di ritaglio logo parte dentro il biglietto, attorno al brand.
- Aggiunti test strumentato e screenshot in `docs/screenshots/`.

### v0.1.0-alpha — 2026-11-18

- Prima versione completa: acquisizione, OCR offline, parsing, revisione, ritaglio logo,
  selezione account e salvataggio in rubrica.

---

## Convenzioni di sviluppo

- **Backup prima di ogni modifica** a un file esistente:
  `bash tools/backup.sh <file>` → `.backups/<percorso>.<YYYYMMDD_HHMMSS>.bak` (cartella esclusa da git).
- `CLAUDE.md` va aggiornato a ogni milestone e modifica strutturale.
- **Questo README va aggiornato a ogni release**: versione e stato in testa, sezione screenshot se
  cambia la UI, voce nel changelog e roadmap.
- `./gradlew assembleDebug` deve restare verde prima di ogni commit.

---

## Limiti noti

- Il parser è euristico: biglietti molto grafici, con più colonne o font decorativi possono
  richiedere correzioni manuali (la revisione è interamente editabile).
- L'OCR può sbagliare singoli caratteri (nomi di dominio, civici): i campi restano modificabili.
- L'APK pesa ~59 MB perché include il modello OCR: nessun download al primo avvio.
- Non è prevista sincronizzazione o cronologia: ogni biglietto è una sessione.
