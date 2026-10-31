#!/usr/bin/env bash
# Lists enabled packages, marks the protected ones, and flags the known debloat candidates,
# so we can decide together what goes. Changes nothing on the TV.
source "$(dirname "$0")/lib.sh"
connect
OUT="$STATE/inventory.tsv"
protected_list > "$STATE/protected-resolved.txt"
printf 'package\ttype\tstatus\tnote\n' > "$OUT"
sys="$(tsh pm list packages -s | sed 's/^package://')"
while read -r p; do
  [ -n "$p" ] || continue
  type=user; grep -qx "$p" <<<"$sys" && type=system
  status=""; note=""
  if is_protected "$p"; then status=PROTECTED; fi
  c="$(grep -E "^$p([[:space:]]|$)" "$ROOT/debloat/candidates.txt" | head -1 || true)"
  [ -n "$c" ] && { [ -z "$status" ] && status=CANDIDATE; note="${c#*# }"; }
  printf '%s\t%s\t%s\t%s\n' "$p" "$type" "$status" "$note" >> "$OUT"
done < <(tsh pm list packages -e | sed 's/^package://' | sort)
{ column -t -s $'\t' "$OUT" 2>/dev/null || cat "$OUT"; }
echo; echo "Saved to $OUT. Packages with an empty status still need a decision."
