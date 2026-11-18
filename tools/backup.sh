#!/usr/bin/env bash
# Backup obbligatorio prima di modificare un file esistente.
# Uso: bash tools/backup.sh <file> [<file> ...]
# Crea .backups/<percorso relativo>.<YYYYMMDD_HHMMSS>.bak
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Uso: bash tools/backup.sh <file> [<file> ...]" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date +%Y%m%d_%H%M%S)"
STATUS=0

for f in "$@"; do
  if [ ! -f "$f" ]; then
    echo "SKIP (non esiste): $f" >&2
    STATUS=1
    continue
  fi

  abs="$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
  rel="${abs#"$ROOT"/}"

  if [ "$rel" = "$abs" ]; then
    echo "ERRORE: il file è fuori dal progetto: $f" >&2
    STATUS=1
    continue
  fi

  dest="$ROOT/.backups/${rel}.${TS}.bak"
  mkdir -p "$(dirname "$dest")"
  cp -p "$abs" "$dest"
  echo "backup → .backups/${rel}.${TS}.bak"
done

exit $STATUS
