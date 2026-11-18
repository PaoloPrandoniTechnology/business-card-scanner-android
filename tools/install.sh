#!/usr/bin/env bash
# Compila la build release firmata e la installa (o aggiorna) via ADB.
# Con questo percorso il telefono non mostra il dialogo "installa app sconosciute"
# né la conferma di Play Protect: richiede solo il debug USB attivo.
#
# Uso: bash tools/install.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT/.androidsdk}"

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB="$ANDROID_HOME/platform-tools/adb"
  elif command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  else
    echo "ERRORE: adb non trovato (installa platform-tools o imposta ADB=...)" >&2
    exit 1
  fi
fi

echo "==> build release"
"$ROOT/gradlew" -p "$ROOT" assembleRelease --console=plain

if [ ! -f "$APK" ]; then
  echo "ERRORE: APK non generato: $APK" >&2
  exit 1
fi

echo "==> dispositivi collegati"
"$ADB" devices

echo "==> installazione"
"$ADB" install -r "$APK"

echo "Fatto: $(basename "$APK") installato/aggiornato sul dispositivo."
