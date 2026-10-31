#!/usr/bin/env bash
# Disables (never uninstalls) the packages listed in a file, one per line ("pkg  # why").
# Each change is logged to state/changes.tsv for restore.sh. Protected packages are refused.
# Usage: TV=… ./03-debloat.sh debloat/approved.txt [--dry-run]
source "$(dirname "$0")/lib.sh"
LIST="${1:?usage: $0 <package-list-file> [--dry-run]}"
DRY="${2:-}"
connect
[ -n "$(original_backup)" ] || { echo "No backup found. Run 01-backup.sh first." >&2; exit 1; }

done_n=0; skip_n=0
while read -r line; do
  p="$(sed 's/#.*//; s/[[:space:]]//g' <<<"$line")"
  [ -n "$p" ] || continue
  if is_protected "$p"; then echo "REFUSED   $p (protected)"; skip_n=$((skip_n+1)); continue; fi
  if ! pkg_installed "$p"; then echo "absent    $p"; skip_n=$((skip_n+1)); continue; fi
  if pkg_disabled "$p"; then echo "already   $p"; skip_n=$((skip_n+1)); continue; fi
  if [ "$DRY" = --dry-run ]; then echo "would     $p"; continue; fi
  out="$(tsh pm disable-user --user 0 "$p" 2>&1 || true)"
  if pkg_disabled "$p"; then
    log_change pkg-disable "$p" enabled disabled-user
    echo "disabled  $p"; done_n=$((done_n+1))
  else
    echo "FAILED    $p: $out"
  fi
done < "$LIST"
echo "disabled $done_n, skipped $skip_n"
[ "$DRY" = --dry-run ] || { home; shot "after-debloat-$(basename "$LIST" .txt)"; }
